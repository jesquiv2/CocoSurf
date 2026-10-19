package com.antena.tv.log

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Tipos de evento de la bitácora (PLAN.md §19 · Fase 6). */
enum class LogKind(val label: String) {
    SITIO("Sitio"),
    PAGINA("Página"),
    ERROR("Error"),
    SISTEMA("Sistema"),
}

/** Un evento con su marca de tiempo: lo que se lee en la pantalla Bitácora. */
data class LogEntry(val at: Long, val kind: LogKind, val text: String) {
    /** «14:05:33» si es de hoy; «16/10 14:05:33» si es de otro día. */
    fun hora(): String {
        val hoy = DIA.get()!!.format(Date())
        val suyo = DIA.get()!!.format(Date(at))
        return (if (hoy == suyo) FORMATO else FORMATO_LARGO).get()!!.format(Date(at))
    }

    /** Texto plano de la entrada (para copiarla al portapapeles). */
    fun completa(): String = "${hora()} [${kind.label}] $text"

    private companion object {
        // ThreadLocal: cada hilo con su propio formateador (SimpleDateFormat no es seguro).
        val FORMATO = ThreadLocal.withInitial { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
        val FORMATO_LARGO = ThreadLocal.withInitial { SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()) }
        val DIA = ThreadLocal.withInitial { SimpleDateFormat("yyyyMMdd", Locale.US) }
    }
}

/**
 * Bitácora de la aplicación: lo que pasó, más reciente arriba, legible desde
 * Información del sistema sin conectar adb.
 *
 * Se guarda en disco (`bitacora.log`) para que sobreviva a los reinicios: así se
 * puede ver también lo que pasó **antes** de que el televisor se colgara. Una marca
 * de «sesión activa» distingue un cierre normal de uno de golpe (el sistema mató la
 * app, se acabó la memoria o se reinició el aparato).
 */
object Bitacora {
    private const val MAX = 600
    private const val ARCHIVO_CIERRE = "cierre.log"
    private const val ARCHIVO_HISTORIAL = "bitacora.log"
    private const val ARCHIVO_SESION = "sesion.activa"
    private const val MAX_BYTES_ARCHIVO = 400_000L

    private val lock = Any()
    private val items = ArrayList<LogEntry>(64)

    @Volatile private var contexto: Context? = null
    @Volatile private var instalado = false

    /** La sesión anterior no cerró con normalidad (la pantalla añade el resumen de recursos). */
    @Volatile
    var cierreAnormal = false
        private set

    /** Registra un evento. Se puede llamar desde cualquier hilo. */
    fun add(kind: LogKind, text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val entrada = LogEntry(System.currentTimeMillis(), kind, t)
        synchronized(lock) {
            items.add(0, entrada)
            while (items.size > MAX) items.removeAt(items.size - 1)
            runCatching {
                archivoHistorial()?.appendText(
                    "${entrada.at}|${kind.name}|${t.replace('\n', ' ').replace('\r', ' ')}\n",
                )
            }
        }
    }

    /** Copia de los eventos, del más reciente al más antiguo. */
    fun entries(): List<LogEntry> = synchronized(lock) { ArrayList(items) }

    fun clear() = synchronized(lock) {
        items.clear()
        runCatching { archivoHistorial()?.delete() }
    }

    /** Todo el registro como texto, del más antiguo al más reciente (para exportarlo). */
    fun comoTexto(cabecera: String): String = buildString {
        appendLine(cabecera)
        appendLine("=".repeat(60))
        entries().asReversed().forEach { appendLine(it.completa()) }
    }

    /**
     * Arranque: recupera el historial y el cierre de la sesión anterior e instala el
     * capturador de excepciones no controladas. Llamar una sola vez, al crear la
     * actividad, antes de componer nada.
     */
    fun init(context: Context) {
        if (contexto == null) contexto = context.applicationContext
        if (instalado) return
        instalado = true
        cargarHistorial()
        recuperarCierrePrevio()
        // ¿La sesión anterior terminó sin cierre normal? (la marca sigue ahí)
        val marca = archivoSesion()
        runCatching {
            if (marca != null && marca.exists()) {
                cierreAnormal = true
                add(
                    LogKind.ERROR,
                    "La sesión anterior terminó de golpe: el sistema cerró la app, " +
                        "se agotó la memoria o se reinició el aparato",
                )
            }
            marca?.writeText(System.currentTimeMillis().toString())
        }
        add(LogKind.SISTEMA, "—— Nueva sesión ——")
        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { hilo, error ->
            val detalle = describe(error)
            runCatching {
                archivo()?.appendText("${LogEntry(System.currentTimeMillis(), LogKind.ERROR, detalle).hora()} $detalle en ${hilo.name}\n")
            }
            add(LogKind.ERROR, "Excepción no controlada: $detalle (${hilo.name})")
            anterior?.uncaughtException(hilo, error)
        }
    }

    /** La app se cerró a propósito (el usuario salió): quita la marca de «sesión activa». */
    fun cierreLimpio() {
        add(LogKind.SISTEMA, "Cierre normal de la aplicación")
        runCatching { archivoSesion()?.delete() }
    }

    private fun archivo(): File? = contexto?.let { File(it.filesDir, ARCHIVO_CIERRE) }
    private fun archivoHistorial(): File? = contexto?.let { File(it.filesDir, ARCHIVO_HISTORIAL) }
    private fun archivoSesion(): File? = contexto?.let { File(it.filesDir, ARCHIVO_SESION) }

    /** Carga los últimos eventos guardados (de sesiones anteriores) y recorta el archivo si creció. */
    private fun cargarHistorial() {
        val f = archivoHistorial() ?: return
        runCatching {
            if (!f.exists()) return
            val lineas = f.readLines()
            val previas = lineas.takeLast(MAX).mapNotNull { l ->
                val p = l.split('|', limit = 3)
                if (p.size < 3) return@mapNotNull null
                val at = p[0].toLongOrNull() ?: return@mapNotNull null
                val kind = LogKind.entries.firstOrNull { it.name == p[1] } ?: return@mapNotNull null
                LogEntry(at, kind, p[2])
            }
            synchronized(lock) { items.addAll(previas.asReversed()) }
            if (f.length() > MAX_BYTES_ARCHIVO) {
                f.writeText(lineas.takeLast(1500).joinToString("\n", postfix = "\n"))
            }
        }
    }

    /** Trae a la bitácora el motivo del último cierre por error y limpia el archivo. */
    private fun recuperarCierrePrevio() {
        val f = archivo() ?: return
        runCatching {
            if (!f.exists()) return
            val texto = f.readText().trim()
            f.delete()
            if (texto.isNotEmpty()) {
                add(LogKind.ERROR, "La sesión anterior terminó por: ${texto.lines().joinToString(" · ")}")
            }
        }
    }

    /** Una línea legible de una excepción: tipo, mensaje y dónde ocurrió. */
    private fun describe(error: Throwable): String {
        val mensaje = error.message?.lineSequence()?.firstOrNull().orEmpty().trim()
        val donde = error.stackTrace.firstOrNull()?.let {
            "${it.className.substringAfterLast('.')}.${it.methodName}"
        }
        return buildString {
            append(error.javaClass.simpleName)
            if (mensaje.isNotEmpty()) append(": ").append(mensaje)
            if (donde != null) append(" @ ").append(donde)
        }
    }
}
