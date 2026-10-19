package com.antena.tv.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fondo de la página de inicio. Por defecto no hay imagen (más ligero); el usuario puede
 * elegir una foto aleatoria de internet o su propia imagen: desde un archivo del aparato,
 * una dirección web o «Usar como fondo» sobre una imagen de cualquier página.
 */
object FondoInicio {
    const val NINGUNO = "ninguno"
    const val ALEATORIO = "aleatorio"
    const val IMAGEN = "imagen"

    var modo by mutableStateOf(NINGUNO)
        private set

    /** Se sube cada vez que cambia la imagen guardada, para que la página la vuelva a leer. */
    var version by mutableStateOf(0)
        private set

    private var cargado = false

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("fondo_inicio", Context.MODE_PRIVATE)
    private fun archivo(c: Context) = File(c.applicationContext.filesDir, "fondo_inicio.jpg")

    fun cargar(c: Context) {
        if (cargado) return
        cargado = true
        modo = prefs(c).getString("modo", NINGUNO) ?: NINGUNO
        if (modo == IMAGEN && !archivo(c).exists()) modo = NINGUNO
    }

    fun elegirModo(c: Context, nuevo: String) {
        // «Mi imagen» sólo tiene sentido si ya hay una guardada.
        modo = if (nuevo == IMAGEN && !archivo(c).exists()) NINGUNO else nuevo
        prefs(c).edit().putString("modo", modo).apply()
    }

    fun hayImagen(c: Context) = archivo(c).exists()

    /** Lee la imagen guardada, reducida al ancho de la pantalla (un televisor no sobra de RAM). */
    suspend fun leerImagen(c: Context): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val f = archivo(c)
            if (!f.exists()) return@runCatching null
            val lim = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, lim)
            var muestreo = 1
            while (lim.outWidth / (muestreo * 2) >= 1280) muestreo *= 2
            val op = BitmapFactory.Options().apply {
                inSampleSize = muestreo
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(f.path, op)
        }.getOrNull()
    }

    /** Foto aleatoria de internet (sólo si el usuario la pidió). */
    suspend fun leerAleatoria(): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL("https://picsum.photos/1280/720").openConnection()
                .apply { connectTimeout = 6000; readTimeout = 8000 }
            val op = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
            BitmapFactory.decodeStream(conn.getInputStream(), null, op)
        }.getOrNull()
    }

    /** Guarda una imagen elegida en el aparato. Devuelve `true` si era una imagen válida. */
    suspend fun usarArchivo(c: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            c.contentResolver.openInputStream(uri)?.use { entrada -> guardar(c, entrada.readBytes()) } ?: false
        }.getOrDefault(false)
    }

    /** Descarga una imagen desde una dirección web y la guarda. */
    suspend fun usarUrl(c: Context, direccion: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(direccion.trim()).openConnection().apply {
                connectTimeout = 8000
                readTimeout = 15000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11) CocoSurf")
            }
            val bytes = conn.getInputStream().use { it.readBytes() }
            guardar(c, bytes)
        }.getOrDefault(false)
    }

    private fun guardar(c: Context, bytes: ByteArray): Boolean {
        if (bytes.isEmpty() || bytes.size > 25 * 1024 * 1024) return false
        // Comprueba que de verdad es una imagen antes de reemplazar la anterior.
        val lim = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, lim)
        if (lim.outWidth <= 0 || lim.outHeight <= 0) return false
        archivo(c).writeBytes(bytes)
        version++
        elegirModo(c, IMAGEN)
        return true
    }

    fun quitar(c: Context) {
        archivo(c).delete()
        version++
        elegirModo(c, NINGUNO)
    }
}
