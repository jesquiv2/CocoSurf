package com.antena.tv.archivos

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.antena.tv.log.Bitacora
import com.antena.tv.log.LogKind
import java.io.File
import java.io.IOException

/** Una unidad que se puede explorar: las descargas de la app, el almacenamiento interno, un USB… */
data class Volumen(val nombre: String, val ruta: File, val requierePermiso: Boolean)

enum class Tipo { CARPETA, VIDEO, AUDIO, IMAGEN, APK, OTRO }

object Almacenamiento {
    private val VIDEOS = setOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "ts", "mpg", "mpeg", "flv", "wmv", "3gp", "m2ts")
    private val AUDIOS = setOf("mp3", "m4a", "ogg", "oga", "wav", "flac", "aac", "opus", "wma")
    private val IMAGENES = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

    fun tipoDe(f: File): Tipo {
        if (f.isDirectory) return Tipo.CARPETA
        return when (f.extension.lowercase()) {
            in VIDEOS -> Tipo.VIDEO
            in AUDIOS -> Tipo.AUDIO
            in IMAGENES -> Tipo.IMAGEN
            "apk" -> Tipo.APK
            else -> Tipo.OTRO
        }
    }

    /** ¿La app puede ver todo el almacenamiento? (Android 11+: «Acceso a todos los archivos»). */
    fun tieneAccesoTotal(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true

    /** Abre la pantalla de Android donde se concede «Acceso a todos los archivos» a la app. */
    fun pedirAccesoTotal(c: Context) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 30) {
                c.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + c.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }.onFailure {
            runCatching {
                c.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    fun volumenes(c: Context): List<Volumen> {
        val lista = mutableListOf<Volumen>()
        c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let {
            lista += Volumen("Descargas de Coco Surf", it, false)
        }
        runCatching {
            val sm = c.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            if (Build.VERSION.SDK_INT >= 30) {
                sm.storageVolumes.forEach { v ->
                    val dir = v.directory
                    if (dir != null && v.state == Environment.MEDIA_MOUNTED) {
                        lista += Volumen(v.getDescription(c), dir, true)
                    }
                }
            }
        }
        if (lista.size == 1) {
            lista += Volumen("Almacenamiento interno", Environment.getExternalStorageDirectory(), true)
        }
        return lista
    }

    /** Contenido de una carpeta: primero carpetas, luego archivos, por nombre. null = sin permiso. */
    fun listar(dir: File): List<File>? {
        val hijos = dir.listFiles() ?: return null
        return hijos.filter { !it.name.startsWith(".") }
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
    }

    fun tamano(b: Long): String = ServidorArchivos.tamano(b)

    private fun uriDe(c: Context, f: File): Uri = FileProvider.getUriForFile(c, c.packageName + ".fileprovider", f)

    /** Instala un APK con el instalador de Android (pide antes el permiso de «apps desconocidas»). */
    fun instalarApk(c: Context, f: File): String? {
        return runCatching {
            if (!c.packageManager.canRequestPackageInstalls()) {
                c.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                return "Permite instalar apps desconocidas a Coco Surf y vuelve a pulsar el APK."
            }
            c.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uriDe(c, f), "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            null
        }.getOrElse { "No se pudo abrir el instalador: ${it.message}" }
    }

    /** Abre un archivo con la app que lo entienda (visor, lector de PDF…). */
    fun abrirConApp(c: Context, f: File): String? {
        return runCatching {
            c.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uriDe(c, f), ServidorArchivos.mimeDe(f.name))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            null
        }.getOrElse { "No hay una app que pueda abrir este archivo." }
    }

    fun eliminar(f: File): Boolean {
        val ok = runCatching { if (f.isDirectory) f.deleteRecursively() else f.delete() }.getOrDefault(false)
        Bitacora.add(LogKind.SISTEMA, "Archivos: ${if (ok) "eliminado" else "no se pudo eliminar"} ${f.name}")
        return ok
    }

    fun nuevaCarpeta(dir: File, nombre: String): Boolean = runCatching {
        File(dir, File(nombre).name).mkdirs()
    }.getOrDefault(false)
}

/**
 * Estado del servicio de compartir por red (vive mientras la app esté abierta, también si cambias de pestaña).
 */
object Compartir {
    var servidor by mutableStateOf<ServidorArchivos?>(null)
        private set
    var direccion by mutableStateOf<String?>(null)
        private set
    var carpeta by mutableStateOf<File?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun iniciar(dir: File) {
        detener()
        error = null
        val ip = ServidorArchivos.direccionLocal()
        if (ip == null) {
            error = "No hay conexión a una red local (Wi‑Fi o cable)."
            return
        }
        for (puerto in 8080..8090) {
            val s = ServidorArchivos(dir, ServidorArchivos.nuevoPin(), puerto)
            try {
                s.start(fi.iki.elonen.NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                servidor = s
                direccion = "http://$ip:$puerto"
                carpeta = dir
                Bitacora.add(LogKind.SISTEMA, "Servidor de archivos encendido en $direccion (carpeta ${dir.name})")
                return
            } catch (e: IOException) {
                // Puerto ocupado: se prueba el siguiente.
            }
        }
        error = "No se pudo abrir ningún puerto entre 8080 y 8090."
    }

    fun detener() {
        runCatching { servidor?.stop() }
        if (servidor != null) Bitacora.add(LogKind.SISTEMA, "Servidor de archivos apagado")
        servidor = null
        direccion = null
        carpeta = null
    }
}
