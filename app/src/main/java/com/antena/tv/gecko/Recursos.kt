package com.antena.tv.gecko

import android.app.ActivityManager
import android.content.Context
import android.net.TrafficStats
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.view.Choreographer
import java.io.File

/**
 * Qué consume el aparato y quién: memoria por proceso de la app (interfaz, páginas,
 * complementos, vídeo, gráficos), CPU, red, estado térmico y fotogramas de la
 * interfaz. Alimenta el monitor en pantalla y el registro que se guarda en disco
 * para explicar, después, por qué se colgó o se cerró la aplicación.
 *
 * Nota honesta: Android sólo deja leer a una app su propia memoria y sus propios
 * procesos (los de Firefox cuentan, son suyos). No hay forma de medir cada pestaña
 * ni cada complemento por separado: las páginas comparten un proceso y los
 * complementos otro, así que se informa por proceso.
 */
object Recursos {
    data class Proceso(val etiqueta: String, val mb: Long)

    data class Muestra(
        val t: Long,
        val libreMb: Long,
        val totalMb: Long,
        val presion: Memoria.Presion,
        val appMb: Long,
        val paginasMb: Long,
        val complementosMb: Long,
        val graficosMb: Long,
        val otrosMb: Long,
        /** % de CPU del aparato (null si Android no deja leerlo). */
        val cpuTotal: Float?,
        /** % de CPU de la app (sus procesos). */
        val cpuApp: Float?,
        val redKbs: Float,
        val fps: Int,
        /** 0 normal … 6 apagado de emergencia; -1 desconocido. */
        val termico: Int,
        val vivas: Int,
        val url: String,
    )

    // ── Contadores del muestreo anterior ──
    private var tAnterior = 0L
    private var statTotalAnterior = 0L
    private var statOcupadoAnterior = 0L
    private var ticksAppAnterior = -1L
    private var redAnterior = -1L

    /** Procesos de la app con su memoria (PSS) en MB. */
    fun procesos(context: Context): List<Proceso> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val propios = am.runningAppProcesses
            ?.filter { it.processName == context.packageName || it.processName.startsWith(context.packageName + ":") }
            .orEmpty()
        if (propios.isEmpty()) return emptyList()
        val info = runCatching { am.getProcessMemoryInfo(propios.map { it.pid }.toIntArray()) }.getOrNull()
            ?: return emptyList()
        return propios.mapIndexed { i, p ->
            val sufijo = p.processName.substringAfter(':', "")
            val etiqueta = when {
                sufijo.isEmpty() -> "Interfaz"
                sufijo.startsWith("tab") -> "Páginas web"
                sufijo.startsWith("gpu") -> "Gráficos"
                sufijo.startsWith("ext") -> "Complementos"
                sufijo.startsWith("media") -> "Vídeo"
                sufijo.startsWith("socket") -> "Red"
                else -> sufijo
            }
            Proceso(etiqueta, info[i].totalPss / 1024L)
        }
    }

    fun muestrear(context: Context, vivas: Int, url: String): Muestra {
        val ahora = SystemClock.elapsedRealtime()
        val dt = ((ahora - tAnterior).coerceAtLeast(1)) / 1000f
        val mem = Memoria.leer(context)
        val procs = procesos(context)
        fun suma(vararg nombres: String) = procs.filter { it.etiqueta in nombres }.sumOf { it.mb }
        val app = suma("Interfaz")
        val paginas = suma("Páginas web")
        val complementos = suma("Complementos")
        val graficos = suma("Gráficos")
        val otros = procs.sumOf { it.mb } - app - paginas - complementos - graficos

        // CPU total del aparato (/proc/stat; puede estar vetado en algunos Android).
        var cpuTotal: Float? = null
        runCatching {
            val linea = File("/proc/stat").bufferedReader().use { it.readLine() }
            val v = linea.trim().split(Regex("\\s+")).drop(1).map { it.toLong() }
            val total = v.sum()
            val inactivo = v[3] + (v.getOrNull(4) ?: 0L)
            val ocupado = total - inactivo
            if (statTotalAnterior > 0 && total > statTotalAnterior) {
                cpuTotal = (ocupado - statOcupadoAnterior) * 100f / (total - statTotalAnterior)
            }
            statTotalAnterior = total
            statOcupadoAnterior = ocupado
        }

        // CPU de la app: ticks de sus procesos (los que Android deje leer) / capacidad del aparato.
        var cpuApp: Float? = null
        runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val pids = am.runningAppProcesses
                ?.filter { it.processName == context.packageName || it.processName.startsWith(context.packageName + ":") }
                ?.map { it.pid }.orEmpty()
            var ticks = 0L
            for (pid in pids) {
                runCatching {
                    val campos = File("/proc/$pid/stat").readText().substringAfterLast(')').trim().split(' ')
                    ticks += campos[11].toLong() + campos[12].toLong()   // utime + stime
                }
            }
            if (ticks == 0L) ticks = Process.getElapsedCpuTime() / 10        // sólo este proceso (ms → ticks)
            if (ticksAppAnterior >= 0 && dt > 0.2f) {
                val hz = 100f
                val nucleos = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
                cpuApp = ((ticks - ticksAppAnterior) / hz / dt / nucleos * 100f).coerceIn(0f, 100f)
            }
            ticksAppAnterior = ticks
        }

        // Red (bytes recibidos por la app).
        var redKbs = 0f
        runCatching {
            val rx = TrafficStats.getUidRxBytes(Process.myUid())
            if (rx >= 0 && redAnterior >= 0) redKbs = ((rx - redAnterior) / 1024f / dt).coerceAtLeast(0f)
            redAnterior = rx
        }

        val termico = if (Build.VERSION.SDK_INT >= 29) {
            runCatching {
                (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus
            }.getOrDefault(-1)
        } else {
            -1
        }

        tAnterior = ahora
        return Muestra(
            t = System.currentTimeMillis(),
            libreMb = mem.libreMb,
            totalMb = mem.totalMb,
            presion = mem.presion,
            appMb = app,
            paginasMb = paginas,
            complementosMb = complementos,
            graficosMb = graficos,
            otrosMb = otros.coerceAtLeast(0),
            cpuTotal = cpuTotal,
            cpuApp = cpuApp,
            redKbs = redKbs,
            fps = FpsInterfaz.fps,
            termico = termico,
            vivas = vivas,
            url = url,
        )
    }

    // ── Registro en disco: lo último antes de un cierre de golpe ──

    private fun archivo(context: Context) = File(context.filesDir, "recursos.log")

    fun guardar(context: Context, m: Muestra) {
        runCatching {
            val f = archivo(context)
            f.appendText(
                "${m.t}|${m.libreMb}|${m.totalMb}|${m.presion.name}|${m.appMb}|${m.paginasMb}|${m.complementosMb}|" +
                    "${m.graficosMb}|${m.cpuTotal?.toInt() ?: -1}|${m.cpuApp?.toInt() ?: -1}|${m.vivas}|" +
                    "${m.url.replace('|', ' ').replace('\n', ' ')}\n",
            )
            if (f.length() > 120_000) {
                f.writeText(f.readLines().takeLast(300).joinToString("\n", postfix = "\n"))
            }
        }
    }

    /**
     * Resumen de lo que pasaba en los últimos minutos de la sesión anterior: es la
     * pista de por qué terminó de golpe (memoria agotándose, CPU saturada, qué página).
     */
    fun resumenCierre(context: Context): String? = runCatching {
        val f = archivo(context)
        if (!f.exists()) return null
        val l = f.readLines().takeLast(40).mapNotNull { linea ->
            val p = linea.split('|', limit = 12)
            if (p.size < 12) null else p
        }
        if (l.isEmpty()) return null
        val ultima = l.last()
        val libres = l.map { it[1].toLong() }
        val hora = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(ultima[0].toLong()))
        val cpu = ultima[8].toInt().takeIf { it >= 0 }?.let { " · CPU $it %" }.orEmpty()
        "Antes del cierre (último dato a las $hora): memoria libre ${ultima[1]} MB de ${ultima[2]} " +
            "(mínimo reciente ${libres.min()} MB, presión ${ultima[3].lowercase()}) · " +
            "páginas ${ultima[5]} MB, complementos ${ultima[6]} MB, gráficos ${ultima[7]} MB$cpu · " +
            "pestañas vivas ${ultima[10]} · página: ${ultima[11]}"
    }.getOrNull()
}

/** Fotogramas por segundo de la interfaz de la app (sólo mientras se pide). */
object FpsInterfaz {
    @Volatile
    var fps = 0
        private set

    private var cuenta = 0
    private var desde = 0L
    private var activo = false

    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!activo) return
            cuenta++
            val ms = frameTimeNanos / 1_000_000
            if (desde == 0L) desde = ms
            if (ms - desde >= 1000) {
                fps = cuenta
                cuenta = 0
                desde = ms
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun encender() {
        if (activo) return
        activo = true
        cuenta = 0
        desde = 0
        Choreographer.getInstance().postFrameCallback(callback)
    }

    fun apagar() {
        activo = false
        fps = 0
    }
}
