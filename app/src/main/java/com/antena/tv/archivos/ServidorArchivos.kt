package com.antena.tv.archivos

import com.antena.tv.log.Bitacora
import com.antena.tv.log.LogKind
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.MIME_PLAINTEXT
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse
import java.io.File
import java.io.FileInputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URLEncoder
import java.security.SecureRandom
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
        // Hoja de estilos y script de la página: no llevan nada privado.
        if (uri == "/_/app.css") return recurso("web/app.css", "text/css; charset=utf-8")
        if (uri == "/_/app.js") return recurso("web/app.js", "application/javascript; charset=utf-8")
        if (!autorizado(s)) {
            return if (uri.startsWith("/api/") || s.method == Method.POST) {
                texto(Response.Status.UNAUTHORIZED, "Sesión caducada")
            } else {
                pagina(Response.Status.OK, "Acceso", formularioPin(null))
            }
        }
        return when {
            uri == "/api/lista" -> apiLista(s)
            uri == "/f" -> descargar(s)
            uri == "/zip" -> zip(s)
            uri == "/subir" && s.method == Method.POST -> subir(s)
            uri == "/borrar" && s.method == Method.POST -> borrar(s)
            uri == "/carpeta" && s.method == Method.POST -> carpeta(s)
            uri == "/renombrar" && s.method == Method.POST -> renombrar(s)
            uri == "/salir" -> {
                val r = redirigir("/")
                r.addHeader("Set-Cookie", "cs=; Max-Age=0; Path=/; HttpOnly")
                r
            }
            else -> recurso("web/app.html", "text/html; charset=utf-8")
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

    private fun apiLista(s: IHTTPSession): Response {
        val dir = resolver(s.parms["r"])?.takeIf { it.isDirectory } ?: return texto(Response.Status.NOT_FOUND, "No existe")
        val items = JSONArray()
        for (h in dir.listFiles() ?: emptyArray()) {
            items.put(JSONObject().put("n", h.name).put("d", h.isDirectory).put("t", if (h.isDirectory) 0L else h.length()).put("m", h.lastModified()))
        }
        val json = JSONObject()
            .put("ruta", relativa(dir))
            .put("raiz", raiz.name.ifEmpty { "Almacenamiento" })
            .put("libre", runCatching { raiz.usableSpace }.getOrDefault(-1L))
            .put("items", items)
        val r = newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", json.toString())
        r.addHeader("Cache-Control", "no-store")
        return r
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
        r.addHeader("Content-Disposition", (if (s.parms["d"] == "1") "attachment" else "inline") + "; " + nombreCabecera(f.name))
        return r
    }

    private fun subir(s: IHTTPSession): Response {
        val base = resolver(s.parms["r"])?.takeIf { it.isDirectory } ?: return texto(Response.Status.NOT_FOUND, "Carpeta inexistente")
        // `p` = subcarpeta relativa (al subir una carpeta entera).
        val sub = s.parms["p"].orEmpty().trim('/')
        val destino = resolver(listOf(relativa(base), sub).filter { it.isNotEmpty() }.joinToString("/"))
            ?: return texto(Response.Status.FORBIDDEN, "No permitido")
        destino.mkdirs()
        val temporales = HashMap<String, String>()
        s.parseBody(temporales)
        var n = 0
        // NanoHTTPD deja cada archivo en un temporal; el nombre original llega en `parms`.
        for ((campo, ruta) in temporales) {
            val original = s.parms[campo] ?: continue
            val nombre = File(original).name
            if (nombre.isBlank()) continue
            val meta = libre(destino, nombre)
            if (!(runCatching { meta.canonicalPath }.getOrNull() ?: "").startsWith(raizCanonica)) continue
            val tmp = File(ruta)
            if (!tmp.renameTo(meta)) {
                tmp.copyTo(meta, overwrite = true)
                tmp.delete()
            }
            n++
        }
        Bitacora.add(LogKind.SISTEMA, "Servidor de archivos: se subieron $n archivo(s) a ${relativa(destino).ifEmpty { "/" }}")
        return texto(Response.Status.OK, "ok")
    }

    /** Un nombre que no pise nada: «foto.jpg» pasa a «foto (1).jpg». */
    private fun libre(dir: File, nombre: String): File {
        var f = File(dir, nombre)
        if (!f.exists()) return f
        val base = nombre.substringBeforeLast('.', nombre)
        val ext = if (base == nombre) "" else "." + nombre.substringAfterLast('.')
        var i = 1
        while (f.exists()) f = File(dir, "$base ($i)$ext").also { i++ }
        return f
    }

    private fun borrar(s: IHTTPSession): Response {
        val f = resolver(s.parms["r"])?.takeIf { it.canonicalPath != raizCanonica } ?: return texto(Response.Status.FORBIDDEN, "No permitido")
        val ok = if (f.isDirectory) f.deleteRecursively() else f.delete()
        Bitacora.add(LogKind.SISTEMA, "Servidor de archivos: ${if (ok) "borrado" else "no se pudo borrar"} ${f.name}")
        return texto(if (ok) Response.Status.OK else Response.Status.INTERNAL_ERROR, if (ok) "ok" else "error")
    }

    private fun carpeta(s: IHTTPSession): Response {
        val destino = resolver(s.parms["r"])?.takeIf { it.isDirectory } ?: return texto(Response.Status.NOT_FOUND, "Carpeta inexistente")
        s.parseBody(HashMap())
        val nombre = File(s.parms["nombre"] ?: "").name
        val nueva = File(destino, nombre)
        val ok = nombre.isNotBlank() && !nueva.exists() && nueva.mkdirs()
        return texto(if (ok) Response.Status.OK else Response.Status.CONFLICT, if (ok) "ok" else "error")
    }

    private fun renombrar(s: IHTTPSession): Response {
        val f = resolver(s.parms["r"])?.takeIf { it.exists() && it.canonicalPath != raizCanonica } ?: return texto(Response.Status.NOT_FOUND, "No existe")
        s.parseBody(HashMap())
        val nombre = File(s.parms["nombre"] ?: "").name
        val nuevo = File(f.parentFile, nombre)
        val ok = nombre.isNotBlank() && !nuevo.exists() && f.renameTo(nuevo)
        return texto(if (ok) Response.Status.OK else Response.Status.CONFLICT, if (ok) "ok" else "error")
    }

    /** ZIP en vivo (sin armarlo en memoria ni en disco) de la carpeta o de los elementos `n` elegidos. */
    private fun zip(s: IHTTPSession): Response {
        val dir = resolver(s.parms["r"])?.takeIf { it.isDirectory } ?: return texto(Response.Status.NOT_FOUND, "No existe")
        val elegidos = s.parameters["n"].orEmpty().map { File(it).name }.filter { it.isNotBlank() }
        val items = if (elegidos.isNotEmpty()) elegidos.map { File(dir, it) }.filter { it.exists() } else (dir.listFiles()?.toList() ?: emptyList())
        val entrada = PipedInputStream(1 shl 16)
        val salida = PipedOutputStream(entrada)
        Thread({
            runCatching {
                ZipOutputStream(salida).use { z -> items.forEach { agregar(z, it, it.name) } }
            }.onFailure { runCatching { salida.close() } }
        }, "zip-compartir").start()
        val base = if (elegidos.size == 1) elegidos[0].substringBeforeLast('.', elegidos[0]) else dir.name.ifEmpty { raiz.name.ifEmpty { "archivos" } }
        val r = newChunkedResponse(Response.Status.OK, "application/zip", entrada)
        r.addHeader("Content-Disposition", "attachment; " + nombreCabecera("$base.zip"))
        return r
    }

    private fun agregar(z: ZipOutputStream, f: File, ruta: String) {
        // Nunca sale de la carpeta compartida (enlaces simbólicos incluidos).
        val c = runCatching { f.canonicalPath }.getOrNull() ?: return
        if (c != raizCanonica && !c.startsWith(raizCanonica + File.separator)) return
        if (f.isDirectory) {
            z.putNextEntry(ZipEntry("$ruta/"))
            z.closeEntry()
            f.listFiles()?.forEach { agregar(z, it, "$ruta/${it.name}") }
        } else if (f.canRead()) {
            z.putNextEntry(ZipEntry(ruta))
            FileInputStream(f).use { it.copyTo(z) }
            z.closeEntry()
        }
    }

    private fun nombreCabecera(nombre: String): String {
        val ascii = nombre.map { if (it.code in 0x20..0x7e && it != '"' && it != '\\') it else '_' }.joinToString("")
        return "filename=\"$ascii\"; filename*=UTF-8''" + URLEncoder.encode(nombre, "UTF-8").replace("+", "%20")
    }

    /** Lee una página o recurso empaquetado en la app (src/main/resources/web). */
    private fun recurso(ruta: String, mime: String): Response {
        val bytes = textos.getOrPut(ruta) {
            ServidorArchivos::class.java.classLoader?.getResourceAsStream(ruta)?.use { it.readBytes() } ?: ByteArray(0)
        }
        if (bytes.isEmpty()) return texto(Response.Status.INTERNAL_ERROR, "Falta el recurso $ruta")
        val r = newFixedLengthResponse(Response.Status.OK, mime, java.io.ByteArrayInputStream(bytes), bytes.size.toLong())
        r.addHeader("Cache-Control", "no-cache")
        return r
    }

    // ── Utilidades de respuesta ───────────────────────────────────────────

    private fun redirigir(a: String): Response =
        newFixedLengthResponse(Response.Status.REDIRECT_SEE_OTHER, MIME_PLAINTEXT, "").also { it.addHeader("Location", a) }

    private fun texto(estado: Response.IStatus, cuerpo: String) = newFixedLengthResponse(estado, MIME_PLAINTEXT, cuerpo)

    private fun formularioPin(aviso: String?): String =
        "<div class=acceso><div class=caja><h1>Coco Surf · Archivos</h1><p>Escribe el PIN que aparece en la pantalla del televisor.</p>" +
            (if (aviso != null) "<p class=mal role=alert>${html(aviso)}</p>" else "") +
            "<form method=post action=\"/entrar\"><input name=pin type=password inputmode=numeric autocomplete=off autofocus maxlength=4 " +
            "placeholder=\"····\" aria-label=PIN style=\"width:100%;margin-bottom:14px\">" +
            "<button class=\"b prim\" style=\"width:100%\">Entrar</button></form></div></div>"

    private fun pagina(estado: Response.IStatus, titulo: String, cuerpo: String): Response {
        val doc = "<!doctype html><html lang=es><head><meta charset=utf-8>" +
            "<meta name=viewport content=\"width=device-width,initial-scale=1\"><title>${html(titulo)} · Coco Surf</title>" +
            "<link rel=stylesheet href=\"/_/app.css\"></head><body>$cuerpo</body></html>"
        return newFixedLengthResponse(estado, "text/html; charset=utf-8", doc)
    }

    private fun html(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun url(t: String) = URLEncoder.encode(t, "UTF-8")

    companion object {
        private val textos = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()

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
