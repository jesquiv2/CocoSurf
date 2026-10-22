package com.antena.tv.gecko

import java.io.File

/**
 * A dónde se va la memoria de TODO el aparato (no sólo de la app).
 *
 * Android no deja a una app medir la memoria de otras apps, pero sí leer `/proc/meminfo`, que reparte
 * la RAM del aparato en grandes cubos: libre, caché de archivos, apps y servicios, kernel… Lo que
 * no cae en ninguno es memoria que usan los controladores (vídeo, gráficos, ION/DMA/CMA):
 * en un televisor suele ser la parte grande y es la que explica «¿a dónde se fue la RAM?».
 *
 * La lista de procesos de las demás apps no es accesible desde dentro de una app (Android lo veta);
 * para eso está el script `herramientas/memoria-tv.ps1`, que la saca desde el PC con adb.
 */
object MemoriaSistema {
    data class Desglose(
        val totalMb: Long,
        val libreMb: Long,
        /** Memoria que Android puede recuperar enseguida (caché incluida). */
        val disponibleMb: Long,
        /** Archivos en caché (se sueltan solos si hace falta). */
        val cacheMb: Long,
        /** Memoria de apps y servicios (heap, pilas…). */
        val appsMb: Long,
        /** Kernel: estructuras internas, pilas, tablas de páginas. */
        val kernelMb: Long,
        /** Memoria compartida (incluida en la caché): ventanas, ashmem, buffers gráficos. */
        val compartidaMb: Long,
        /** Memoria que no está en ninguno de los cubos: controladores de vídeo/gráficos (ION, DMA, CMA). */
        val sinContabilizarMb: Long,
        val swapUsadoMb: Long,
        val cmaTotalMb: Long,
        val cmaUsadaMb: Long,
    )

    private fun leerCampos(): Map<String, Long>? = runCatching {
        File("/proc/meminfo").readLines().mapNotNull { l ->
            val p = l.split(':', limit = 2)
            if (p.size < 2) return@mapNotNull null
            val n = p[1].trim().split(' ').firstOrNull()?.toLongOrNull() ?: return@mapNotNull null
            p[0].trim() to n      // kB
        }.toMap()
    }.getOrNull()

    /** Lectura barata (un archivo de texto): se puede pedir cada pocos segundos. */
    fun leer(): Desglose? {
        val m = leerCampos() ?: return null
        fun mb(k: String) = (m[k] ?: 0L) / 1024L
        val total = mb("MemTotal")
        if (total <= 0) return null
        val libre = mb("MemFree")
        val cache = mb("Cached") + mb("Buffers")
        val apps = mb("AnonPages")
        val kernel = mb("Slab") + mb("KernelStack") + mb("PageTables")
        val contabilizado = libre + cache + apps + kernel
        val cmaTotal = mb("CmaTotal")
        return Desglose(
            totalMb = total,
            libreMb = libre,
            disponibleMb = if (m.containsKey("MemAvailable")) mb("MemAvailable") else libre + cache,
            cacheMb = cache,
            appsMb = apps,
            kernelMb = kernel,
            compartidaMb = mb("Shmem"),
            sinContabilizarMb = (total - contabilizado).coerceAtLeast(0),
            swapUsadoMb = (mb("SwapTotal") - mb("SwapFree")).coerceAtLeast(0),
            cmaTotalMb = cmaTotal,
            cmaUsadaMb = (cmaTotal - mb("CmaFree")).coerceAtLeast(0),
        )
    }

    /** Una línea para la Bitácora. */
    fun resumen(): String = leer()?.let {
        "RAM del aparato → libre ${it.libreMb} · caché ${it.cacheMb} · apps ${it.appsMb} · kernel ${it.kernelMb} · " +
            "controladores de vídeo/gráficos ${it.sinContabilizarMb}" +
            (if (it.swapUsadoMb > 0) " · swap ${it.swapUsadoMb}" else "") + " (MB)"
    } ?: "RAM del aparato: no se pudo leer"
}
