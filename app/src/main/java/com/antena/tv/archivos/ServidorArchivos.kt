package com.antena.tv.archivos

import com.antena.tv.log.Bitacora
import com.antena.tv.log.LogKind
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.MIME_PLAINTEXT
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse
import java.io.File
import java.io.FileInputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URLEncoder
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Comparte una carpeta del aparato por la red local: desde el navegador de un PC se puede ver, bajar,
 * subir y borrar archivos. Está pensado como algo que se enciende a propósito:
 *  · pide un PIN de 4 dígitos que sólo se ve en la pantalla del televisor,
 *  · sólo muestra la carpeta elegida (y nunca sale de ella),
 *  · se apaga solo al cerrar la app.
 * Va por HTTP sin cifrar: úsalo en tu red de casa, no en una pública.
 */
class ServidorArchivos(
    private val raiz: File,
    val pin: String,
    puerto: Int,
) : NanoHTTPD(puerto) {

    private val sesiones = mutableSetOf<String>()
    private var fallos = 0
    private var bloqueadoHasta = 0L
    private val raizCanonica: String = raiz.canonicalPath

    override fun serve(s: IHTTPSession): Response = runCatching { atender(s) }.getOrElse {
        Bitacora.add(LogKind.ERROR, "Servidor de archivos: ${it.javaClass.simpleName}: ${it.message}")
        texto(Response.Status.INTERNAL_ERROR, "Error interno")
    }

    private fun atender(s: IHTTPSession): Response {
        val uri = s.uri
        if (uri == "/entrar" && s.method == Method.POST) return entrar(s)
        if (!autorizado(s)) {
            return pagina(Response.Status.OK, "Acceso", formularioPin(null))
        }
        return when {
            uri == "/f" -> descargar(s)
            uri == "/subir" && s.method == Method.POST -> subir(s)
            uri == "/borrar" && s.method == Method.POST -> borrar(s)
            uri == "/carpeta" && s.method == Method.POST -> carpeta(s)
            uri == "/salir" -> {
                val r = redirigir("/")
                r.addHeader("Set-Cookie", "cs=; Max-Age=0; Path=/; HttpOnly")
                r
            }
            else -> listar(s)
        }
    }

    // ── Acceso con PIN ────────────────────────────────────────────────────

    private fun autorizado(s: IHTTPSession): Boolean {
        val c = s.headers["cookie"] ?: return false
        val token = c.split(';').map { it.trim() }.firstOrNull { it.startsWith("cs=") }?.removePrefix("cs=") ?: return false
        return synchronized(sesiones) { token in sesiones }
    }

    private fun entrar(s: IHTTPSession): Response {
        val ahora = System.currentTimeMillis()
        if (ahora < bloqueadoHasta) {
            return pagina(Response.Status.OK, "Acceso", formularioPin("Demasiados intentos. Espera un minuto."))
        }
        val cuerpo = HashMap<String, String>()
        s.parseBody(cuerpo)
        val dado = s.parms["pin"]?.trim()
        return if (dado == pin) {
            fallos = 0
            val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val token = bytes.joinToString("") { "%02x".format(it) }
            synchronized(sesiones) { sesiones.add(token) }
            Bitacora.add(LogKind.SISTEMA, "Servidor de archivos: se conectó un equipo (${s.remoteIpAddress})")
            val r = redirigir("/")
            r.addHeader("Set-Cookie", "cs=$token; Path=/; HttpOnly")
            r
        } else {
            fallos++
            if (fallos >= 5) {
                bloqueadoHasta = ahora + 60_000
                fallos = 0
            }
            pagina(Response.Status.OK, "Acceso", formularioPin("PIN incorrecto."))
        }
    }

    // ── Rutas ─────────────────────────────────────────────────────────────

    /** Convierte una ruta relativa de la URL en un archivo dentro de la raíz (o null si se sale de ella). */
    private fun resolver(rel: String?): File? {
        val limpio = (rel ?: "").trim('/')
        val f = if (limpio.isEmpty()) raiz else File(raiz, limpio)
        val c = runCatching { f.canonicalPath }.getOrNull() ?: return null
        return if (c == raizCanonica || c.startsWith(raizCanonica + File.separator)) f else null
    }

    private fun relativa(f: File): String = f.canonicalPath.removePrefix(raizCanonica).trim('/')

    private fun listar(s: IHTTPSession): Response {
        val rel = s.parms["r"]
        val dir = resolver(rel)?.takeIf { it.isDirectory } ?: return pagina(Response.Status.NOT_FOUND, "No existe", "<p>Esa carpeta no existe.</p>")
        val hijos = (dir.listFiles() ?: emptyArray())
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
        val actual = relativa(dir)
        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val sb = StringBuilder()
        // Migas de pan
        sb.append("<p class=ruta><a href=\"/\">Inicio</a>")
        var acumulado = ""
        actual.split('/').filter { it.isNotEmpty() }.forEach { parte ->
            acumulado = if (acumulado.isEmpty()) parte else "$acumulado/$parte"
            sb.append(" / <a href=\"/?r=${url(acumulado)}\">${html(parte)}</a>")
        }
        sb.append("</p>")
        // Subir y nueva carpeta
        sb.append(
            "<div class=acciones>" +
                "<form method=post action=\"/subir?r=${url(actual)}\" enctype=\"multipart/form-data\">" +
                "<input type=file name=archivo multiple required> <button>Subir aquí</button></form>" +
                "<form method=post action=\"/carpeta?r=${url(actual)}\"><input name=nombre placeholder=\"Nueva carpeta\" required> <button>Crear</button></form>" +
                "</div>",
        )
        sb.append("<table><tr><th>Nombre<th>Tamaño<th>Fecha<th></tr>")
        if (actual.isNotEmpty()) {
            val padre = actual.substringBeforeLast('/', "")
            sb.append("<tr><td><a href=\"/?r=${url(padre)}\">⬑ ..</a><td><td><td>")
        }
        for (h in hijos) {
            val r = relativa(h)
            sb.append("<tr><td>")
            if (h.isDirectory) {
                sb.append("📁 <a href=\"/?r=${url(r)}\">${html(h.name)}</a><td>—")
            } else {
                sb.append("<a href=\"/f?r=${url(r)}\">${html(h.name)}</a><td>${tamano(h.length())}")
            }
            sb.append("<td>${fmt.format(Date(h.lastModified()))}<td>")
            sb.append(
                "<form method=post action=\"/borrar?r=${url(r)}\" onsubmit=\"return confirm('¿Borrar ${html(h.name).replace("'", "")}?')\">" +
                    "<button class=borrar>Borrar</button></form>",
            )
            sb.append("</tr>")
        }
        sb.append("</table>")
        if (hijos.isEmpty()) sb.append("<p class=tenue>La carpeta está vacía.</p>")
        return pagina(Response.Status.OK, "Archivos", sb.toString())
    }

    private fun descargar(s: IHTTPSession): Response {
        val f = resolver(s.parms["r"])?.takeIf { it.isFile } ?: return texto(Response.Status.NOT_FOUND, "No existe")
        val mime = mimeDe(f.name)
        val total = f.length()
        val rango = s.headers["range"]
        if (rango != null && rango.startsWith("bytes=")) {
            val partes = rango.removePrefix("bytes=").split('-')
            val ini = partes.getOrNull(0)?.toLongOrNull() ?: 0L
            val fin = partes.getOrNull(1)?.toLongOrNull()?.coerceAtMost(total - 1) ?: (total - 1)
            if (ini in 0 until total && fin >= ini) {
                val entrada = FileInputStream(f).also { it.skip(ini) }
                val r = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mime, entrada, fin - ini + 1)
                r.addHeader("Content-Range", "bytes $ini-$fin/$total")
                r.addHeader("Accept-Ranges", "bytes")
                return r
            }
        }
        val r = newFixedLengthResponse(Response.Status.OK, mime, FileInputStream(f), total)
        r.addHeader("Accept-Ranges", "bytes")
        r.addHeader("Content-Disposition", "inline; filename=\"" + f.name.replace("\"", "") + "\"")
        return r
    }

    private fun subir(s: IHTTPSession): Response {
        val destino = resolver(s.parms["r"])?.takeIf { it.isDirectory } ?: return texto(Response.Status.NOT_FOUND, "Carpeta inexistente")
        val temporales = HashMap<String, String>()
        s.parseBody(temporales)
        var n = 0
        // NanoHTTPD deja cada archivo en un temporal; el nombre original llega en `parms`.
        for ((campo, ruta) in temporales) {
            val original = s.parms[campo] ?: continue
            val nombre = File(original).name
            if (nombre.isBlank()) continue
            val meta = File(destino, nombre)
            if (!(runCatching { meta.canonicalPath }.getOrNull() ?: "").startsWith(raizCanonica)) continue
            val tmp = File(ruta)
            if (!tmp.renameTo(meta)) {
                tmp.copyTo(meta, overwrite = true)
                tmp.delete()
            }
            n++
        }
        Bitacora.add(LogKind.SISTEMA, "Servidor de archivos: se subieron $n archivo(s) a ${relativa(destino).ifEmpty { "/" }}")
        return redirigir("/?r=" + url(relativa(destino)))
    }

    private fun borrar(s: IHTTPSession): Response {
        val f = resolver(s.parms["r"])?.takeIf { it.canonicalPath != raizCanonica } ?: return texto(Response.Status.FORBIDDEN, "No permitido")
        val padre = f.parentFile
        val ok = if (f.isDirectory) f.deleteRecursively() else f.delete()
        Bitacora.add(LogKind.SISTEMA, "Servidor de archivos: ${if (ok) "borrado" else "no se pudo borrar"} ${f.name}")
        return redirigir("/?r=" + url(if (padre != null) relativa(padre) else ""))
    }

    private fun carpeta(s: IHTTPSession): Response {
        val destino = resolver(s.parms["r"])?.takeIf { it.isDirectory } ?: return texto(Response.Status.NOT_FOUND, "Carpeta inexistente")
        s.parseBody(HashMap())
        val nombre = File(s.parms["nombre"] ?: "").name
        if (nombre.isNotBlank()) File(destino, nombre).mkdirs()
        return redirigir("/?r=" + url(relativa(destino)))
    }

    // ── Utilidades de respuesta ───────────────────────────────────────────

    private fun redirigir(a: String): Response =
        newFixedLengthResponse(Response.Status.REDIRECT_SEE_OTHER, MIME_PLAINTEXT, "").also { it.addHeader("Location", a) }

    private fun texto(estado: Response.IStatus, cuerpo: String) = newFixedLengthResponse(estado, MIME_PLAINTEXT, cuerpo)

    private fun formularioPin(aviso: String?): String =
        "<h1>Coco Surf · Archivos</h1><p>Escribe el PIN que aparece en la pantalla del televisor.</p>" +
            (if (aviso != null) "<p class=aviso>${html(aviso)}</p>" else "") +
            "<form method=post action=\"/entrar\"><input name=pin inputmode=numeric autofocus maxlength=4 placeholder=\"PIN\"> <button>Entrar</button></form>"

    private fun pagina(estado: Response.IStatus, titulo: String, cuerpo: String): Response {
        val doc = "<!doctype html><html lang=es><head><meta charset=utf-8>" +
            "<meta name=viewport content=\"width=device-width,initial-scale=1\"><title>${html(titulo)} · Coco Surf</title><style>$ESTILO</style></head>" +
            "<body><header><b>Coco Surf</b> · Archivos <a class=salir href=\"/salir\">Salir</a></header><main>$cuerpo</main></body></html>"
        return newFixedLengthResponse(estado, "text/html; charset=utf-8", doc)
    }

    private fun html(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun url(t: String) = URLEncoder.encode(t, "UTF-8")

    companion object {
        private const val ESTILO =
            "body{margin:0;background:#0b0f16;color:#e8ecf3;font:15px system-ui,sans-serif}" +
                "header{padding:14px 22px;background:#121926;border-bottom:1px solid #243047}" +
                "main{max-width:1000px;margin:0 auto;padding:18px 22px}a{color:#4fd8c4;text-decoration:none}a:hover{text-decoration:underline}" +
                ".salir{float:right;color:#9aa6bd}table{width:100%;border-collapse:collapse;margin-top:14px}" +
                "th,td{text-align:left;padding:9px 8px;border-bottom:1px solid #1d2638}th{color:#9aa6bd;font-weight:500}" +
                "button,input{font:inherit;border-radius:8px;border:1px solid #2a3750;background:#16203a;color:#e8ecf3;padding:7px 12px}" +
                "button{cursor:pointer}.borrar{background:#2a1618;border-color:#5a2a2e;color:#ff8f8f}" +
                ".acciones{display:flex;gap:18px;flex-wrap:wrap;margin:14px 0}.tenue{color:#9aa6bd}.aviso{color:#ff8f8f}" +
                "form{margin:0;display:inline-block}.ruta{color:#9aa6bd}"

        /** Dirección IPv4 del aparato en la red local (Wi‑Fi o cable), o null si no hay red. */
        fun direccionLocal(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()

        fun tamano(b: Long): String = when {
            b >= 1L shl 30 -> "%.1f GB".format(Locale.US, b / 1073741824.0)
            b >= 1L shl 20 -> "%.1f MB".format(Locale.US, b / 1048576.0)
            b >= 1L shl 10 -> "%d KB".format(b shr 10)
            else -> "$b B"
        }

        fun mimeDe(nombre: String): String {
            val ext = nombre.substringAfterLast('.', "").lowercase()
            return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }

        fun nuevoPin(): String = "%04d".format(SecureRandom().nextInt(10_000))
    }
}
