package com.antena.tv.log

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.antena.tv.gecko.Memoria
import java.util.Locale

/**
 * Medidas de velocidad para la Bitácora: hitos del arranque (contados desde que Android crea el
 * proceso de la app), tiempo de cada página que se abre y bloqueos de la interfaz. Sirven para
 * saber con datos cuánto espera de verdad el usuario y en qué parte del camino se va ese tiempo.
 */
object Tiempos {
    private const val TAG = "Tiempos"

    private var contexto: Context? = null
    private val inicioProceso = Process.getStartElapsedRealtime()

    private var paginas = 0
    private var sumaMs = 0L
    private var maxMs = 0L

    /** Segundos desde que Android creó el proceso de la app. */
    fun segundosDesdeInicio(): Double = (SystemClock.elapsedRealtime() - inicioProceso) / 1000.0

    fun iniciar(c: Context) {
        contexto = c.applicationContext
        hito("ventana creada")
        vigilarInterfaz()
    }

    private fun s(ms: Long) = String.format(Locale.US, "%.1f s", ms / 1000.0)

    private fun libres(): String =
        contexto?.let { runCatching { " · libres ${Memoria.leer(it).libreMb} MB" }.getOrNull() }.orEmpty()

    /** Un paso del arranque (motor creado, bloqueador listo, primera página…). */
    fun hito(texto: String) {
        val msg = "Arranque · $texto (a los ${String.format(Locale.US, "%.1f", segundosDesdeInicio())} s de abrir la app)"
        Log.i(TAG, msg)
        Bitacora.add(LogKind.SISTEMA, msg)
    }

    /**
     * Una página terminó de cargar. `previaMs`: espera antes de que el motor empezara a pedirla (arranque en
     * frío del proceso de contenido, etc.); `pintadoMs`: hasta el primer contenido visible (0 = no hubo).
     */
    fun pagina(url: String, ok: Boolean, previaMs: Long, pintadoMs: Long, totalMs: Long) {
        val host = runCatching { java.net.URI(url).host }.getOrNull() ?: url
        paginas++
        val completa = previaMs + totalMs
        if (ok) {
            sumaMs += completa
            if (completa > maxMs) maxMs = completa
        }
        val primera = if (paginas == 1) " · PRIMERA página, a los ${String.format(Locale.US, "%.1f", segundosDesdeInicio())} s de abrir la app" else ""
        val msg = "Tiempo de carga · $host · " +
            (if (pintadoMs > 0) "primer contenido ${s(previaMs + pintadoMs)}" else "sin contenido visible") +
            " · completa ${s(completa)}" + (if (previaMs > 400) " (de ellos ${s(previaMs)} esperando al motor)" else "") +
            (if (ok) "" else " · FALLÓ") + libres() + primera +
            (if (paginas > 1) " · media ${s(sumaMs / paginas)}, peor ${s(maxMs)}" else "")
        Log.i(TAG, msg)
        Bitacora.add(LogKind.PAGINA, msg)
    }

    /** Avisa si la interfaz se queda sin atender al usuario más de medio segundo (cursor pegado, botones sin respuesta). */
    private fun vigilarInterfaz() {
        val h = Handler(Looper.getMainLooper())
        var esperado = SystemClock.uptimeMillis() + 250
        h.postDelayed(object : Runnable {
            override fun run() {
                val ahora = SystemClock.uptimeMillis()
                val retraso = ahora - esperado
                if (retraso >= 500) {
                    val msg = "Interfaz sin responder ${s(retraso)}" + libres()
                    Log.w(TAG, msg)
                    Bitacora.add(LogKind.ERROR, msg)
                }
                esperado = ahora + 250
                h.postDelayed(this, 250)
            }
        }, 250)
    }
}
