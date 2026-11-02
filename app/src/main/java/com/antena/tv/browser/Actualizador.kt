package com.antena.tv.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.antena.tv.log.Bitacora
import com.antena.tv.log.LogKind
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Busca versiones nuevas en las Releases de GitHub, las descarga y abre el instalador de Android.
 * Funciona si el repositorio es público (o la Release lo es): una API sin sesión no ve repositorios privados.
 */
object Actualizador {
    const val REPO = "jesquiv2/Cocosurf"

    sealed class Estado {
        data object Inactivo : Estado()
        data object Buscando : Estado()
        data class AlDia(val version: String) : Estado()
        data class Nueva(val version: String, val urlApk: String, val notas: String, val mb: Long, val bytes: Long = 0L) : Estado()
        /** No hay Release con APK (o el repositorio es privado y GitHub la oculta). */
        data class SinPublicar(val detalle: String) : Estado()
        data class Error(val motivo: String) : Estado()
        data class Descargando(val version: String, val pct: Int) : Estado()
        data class Listo(val version: String, val archivo: File) : Estado()
    }

    var estado by mutableStateOf<Estado>(Estado.Inactivo)
        private set

    private fun partes(v: String) = v.trim().removePrefix("v").split('.').map { it.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: 0 }

    /** ¿`a` es una versión más nueva que `b`? (1.2.10 > 1.2.9). */
    fun esMasNueva(a: String, b: String): Boolean {
        val x = partes(a)
        val y = partes(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    private fun carpeta(c: Context): File = (c.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: c.filesDir).also { it.mkdirs() }

    private fun apkDe(c: Context, version: String) = File(carpeta(c), "CocoSurf-$version.apk")

    /**
     * Borra los instaladores que ya no sirven: los de una versión igual o anterior a la instalada, los
     * parciales de una descarga cortada y (si se indica) los de otra versión. Así no se acumulan APK de cientos de MB.
     */
    fun limpiar(c: Context, conservar: String? = null) {
        val actual = versionActual(c)
        val patron = Regex("CocoSurf-(.+)\\.apk(\\.part)?")
        carpeta(c).listFiles()?.forEach { f ->
            val n = f.name
            val v = patron.matchEntire(n)?.groupValues?.get(1) ?: return@forEach
            val sobra = !esMasNueva(v, actual) || (conservar != null && v != conservar)
            if (sobra && f.delete()) Bitacora.add(LogKind.SISTEMA, "Actualizador: borrado $n")
        }
    }

    fun versionActual(c: Context): String =
        runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull() ?: "0"

    /** Consulta la última Release. Devuelve el estado resultante (también queda en `estado`). */
    suspend fun buscar(c: Context): Estado {
        estado = Estado.Buscando
        val r = withContext(Dispatchers.IO) {
            runCatching {
                val con = (URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 12000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "CocoSurf/" + versionActual(c))
                }
                try {
                    when (con.responseCode) {
                        200 -> {
                            val j = JSONObject(con.inputStream.bufferedReader().use { it.readText() })
                            val tag = j.optString("tag_name")
                            val assets = j.optJSONArray("assets")
                            var url: String? = null
                            var tam = 0L
                            var bytes = 0L
                            if (assets != null) {
                                for (i in 0 until assets.length()) {
                                    val a = assets.getJSONObject(i)
                                    if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                                        url = a.optString("browser_download_url")
                                        bytes = a.optLong("size")
                                        tam = bytes / (1024 * 1024)
                                        break
                                    }
                                }
                            }
                            when {
                                tag.isBlank() -> Estado.SinPublicar("la Release no trae versión")
                                !esMasNueva(tag, versionActual(c)) -> Estado.AlDia(versionActual(c))
                                url == null -> Estado.SinPublicar("la versión $tag no trae un archivo .apk")
                                else -> {
                                    val v = tag.removePrefix("v")
                                    val apk = apkDe(c, v)
                                    // Ya descargado en una sesión anterior (mismo tamaño que en GitHub): no se baja otra vez.
                                    if (bytes > 0 && apk.length() == bytes) Estado.Listo(v, apk)
                                    else Estado.Nueva(v, url, j.optString("body"), tam, bytes)
                                }
                            }
                        }
                        404 -> Estado.SinPublicar("GitHub no muestra Releases (no hay ninguna o el repositorio es privado)")
                        else -> Estado.Error("GitHub respondió ${con.responseCode}")
                    }
                } finally {
                    con.disconnect()
                }
            }.getOrElse { Estado.Error(it.message ?: it.javaClass.simpleName) }
        }
        estado = r
        withContext(Dispatchers.IO) {
            when (r) {
                is Estado.Listo -> limpiar(c, conservar = r.version)
                is Estado.Nueva, is Estado.AlDia -> limpiar(c)
                else -> Unit
            }
        }
        if (r is Estado.Nueva) Bitacora.add(LogKind.SISTEMA, "Actualización disponible: ${r.version} (${r.mb} MB)")
        return r
    }

    /** Reintentos seguidos (sin avanzar) antes de rendirse; la descarga cortada se retoma donde quedó. */
    private const val REINTENTOS = 30

    /**
     * Baja el APK a la carpeta de descargas de la app, con porcentaje. Si se corta la conexión, el
     * parcial se conserva y se retoma con `Range` (reintentando solo), en vez de empezar de cero.
     */
    suspend fun descargar(c: Context, nueva: Estado.Nueva) {
        estado = Estado.Descargando(nueva.version, 0)
        val r = withContext(Dispatchers.IO) {
            val destino = apkDe(c, nueva.version)
            val parcial = File(destino.path + ".part")
            var fallos = 0
            while (true) {
                ensureActive()
                val antes = parcial.length()
                try {
                    bajarTramo(c, nueva, parcial)
                    if (nueva.bytes > 0 && parcial.length() != nueva.bytes) {
                        throw java.io.IOException("la descarga quedó incompleta")
                    }
                    destino.delete()
                    parcial.renameTo(destino)
                    return@withContext Estado.Listo(nueva.version, destino)
                } catch (e: Exception) {
                    ensureActive()
                    // Si el tramo avanzó algo, no cuenta como fallo seguido.
                    fallos = if (parcial.length() > antes) 1 else fallos + 1
                    val pct = if (nueva.bytes > 0) (parcial.length() * 100 / nueva.bytes).toInt() else 0
                    Bitacora.add(LogKind.SISTEMA, "Actualizador: conexión cortada al $pct % (${e.message}); reintento $fallos")
                    if (fallos > REINTENTOS) {
                        return@withContext Estado.Error(
                            "Se cortó la conexión al $pct %. Pulsa de nuevo para continuar desde ahí.",
                        )
                    }
                    delay(minOf(fallos, 5) * 2000L)
                }
            }
            @Suppress("UNREACHABLE_CODE")
            Estado.Error("")
        }
        estado = r
    }

    /** Un tramo de la descarga: continúa el parcial si el servidor acepta `Range`, o empieza de cero. */
    private fun bajarTramo(c: Context, nueva: Estado.Nueva, parcial: File) {
        var hecho = parcial.length()
        if (nueva.bytes > 0 && hecho > nueva.bytes) {
            parcial.delete()
            hecho = 0
        }
        val con = (URL(nueva.urlApk).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "CocoSurf/" + versionActual(c))
            if (hecho > 0) setRequestProperty("Range", "bytes=$hecho-")
        }
        try {
            val sigue = con.responseCode == 206
            if (!sigue) hecho = 0 // el servidor mandó todo desde el principio
            if (con.responseCode !in 200..299) throw java.io.IOException("GitHub respondió ${con.responseCode}")
            val total = if (nueva.bytes > 0) nueva.bytes else hecho + con.contentLengthLong
            var ultimo = -1
            con.inputStream.use { entrada ->
                java.io.FileOutputStream(parcial, sigue).use { salida ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = entrada.read(buf)
                        if (n < 0) break
                        salida.write(buf, 0, n)
                        hecho += n
                        if (total > 0) {
                            val pct = (hecho * 100 / total).toInt()
                            if (pct != ultimo) {
                                ultimo = pct
                                estado = Estado.Descargando(nueva.version, pct)
                            }
                        }
                    }
                }
            }
        } finally {
            con.disconnect()
        }
    }

    /** Abre el instalador de Android con el APK descargado (pide antes el permiso de «apps desconocidas»). */
    fun instalar(c: Context, listo: Estado.Listo) {
        runCatching {
            if (!c.packageManager.canRequestPackageInstalls()) {
                c.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                return
            }
            val uri = FileProvider.getUriForFile(c, c.packageName + ".fileprovider", listo.archivo)
            c.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { estado = Estado.Error("No se pudo abrir el instalador: ${it.message}") }
    }
}
