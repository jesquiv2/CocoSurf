package com.antena.tv.gecko

import android.app.ActivityManager
import android.content.Context

/**
 * Control de memoria. Un televisor barato tiene 1–2 GB y el motor (Firefox) más
 * un vídeo web pueden agotarlos: el aparato entero se cuelga y hay que desenchufarlo.
 * Por eso la app mantiene SIEMPRE una reserva libre y, antes de quedarse sin ella,
 * suelta lo que no se está viendo (pestañas de fondo) y, en el límite, pausa la
 * página activa en vez de arrastrar al televisor.
 *
 * Escalones (porcentaje de la memoria libre sobre el total):
 *  · BAJA     < 25 %  → sólo queda una pestaña viva.
 *  · CRÍTICA  < 17 %  → se sueltan todas las de fondo y no se abren más.
 *  · EXTREMA  < 12 %  → se recicla la página activa (se recarga limpia) antes de que el
 *                      televisor se cuelgue; si vuelve a pasar enseguida, se pausa.
 */
object Memoria {
    enum class Presion { NORMAL, BAJA, CRITICA, EXTREMA }

    data class Estado(
        val totalMb: Long,
        val libreMb: Long,
        val presion: Presion,
        /** Aparato justo de memoria: ≤ 3 GB o marcado «low RAM» por Android. */
        val pocaRam: Boolean,
        /** Libre mínimo que se intenta conservar (MB). */
        val reservaMb: Long,
    )

    fun leer(context: Context): Estado {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val total = mi.totalMem
        val libre = mi.availMem
        val pocaRam = am.isLowRamDevice || total <= 3L * 1024 * 1024 * 1024
        val presion = when {
            mi.lowMemory || libre < maxOf(mi.threshold, total * 12 / 100) -> Presion.EXTREMA
            libre < maxOf(mi.threshold * 12 / 10, total * 17 / 100) -> Presion.CRITICA
            libre < maxOf(mi.threshold * 2, total * 25 / 100) -> Presion.BAJA
            else -> Presion.NORMAL
        }
        return Estado(total / MB, libre / MB, presion, pocaRam, total * 15 / 100 / MB)
    }

    /** ¿Cuántas pestañas web pueden estar «vivas» a la vez (con su motor en marcha)? */
    fun maxVivas(context: Context): Int {
        val e = leer(context)
        return when {
            e.totalMb <= 1536 -> 2
            e.pocaRam -> 3
            e.totalMb <= 4096 -> 5
            else -> 8
        }
    }

    /** Una línea legible de cada escalón. */
    fun etiqueta(p: Presion): String = when (p) {
        Presion.NORMAL -> "Normal"
        Presion.BAJA -> "Baja"
        Presion.CRITICA -> "Crítica"
        Presion.EXTREMA -> "Extrema"
    }

    private const val MB = 1024L * 1024L
}
