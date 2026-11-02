package com.antena.tv.gecko

import android.os.SystemClock

/**
 * Último estado que informó la precarga de video de la página (extensión interna): segundos de
 * colchón del reproductor, segundos ya guardados en disco por delante y si el servidor da problemas.
 * Lo lee el monitor en pantalla; si la página deja de informar, el dato caduca.
 */
object PrecargaEstado {
    data class Estado(
        val servidor: String,
        val colchonS: Float,
        val cacheS: Float,
        val malo: Boolean,
        val pausa: Boolean,
    )

    @Volatile private var ultimo: Estado? = null
    @Volatile private var cuando = 0L

    fun poner(e: Estado) {
        ultimo = e
        cuando = SystemClock.elapsedRealtime()
    }

    /** El estado vigente, o null si la página no informa desde hace unos segundos. */
    fun actual(): Estado? = ultimo?.takeIf { SystemClock.elapsedRealtime() - cuando < 5000 }
}
