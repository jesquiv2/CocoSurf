package com.antena.tv.browser

import com.antena.tv.db.HistoryEntry
import com.antena.tv.db.SearchEngine
import java.net.URLDecoder
import java.text.Normalizer
import java.util.Locale

/** Cuántas sugerencias enseña el desplegable (el paso pide entre 10 y 15). */
const val MAX_SUGERENCIAS = 15

/**
 * De dónde sale una sugerencia. La barra las trata distinto a propósito:
 * una **dirección** se abre tal cual; una **búsqueda** recupera su texto en la
 * barra para poder repetirla con el motor que el usuario tenga elegido ahora.
 */
enum class TipoSugerencia { DIRECCION, BUSQUEDA }

/**
 * Fila del desplegable de la barra de direcciones. Siempre nace del historial
 * real del navegador: nunca se inventa una entrada.
 */
data class Sugerencia(
    val tipo: TipoSugerencia,
    /** Título de la página, o el texto buscado si es una búsqueda. */
    val titulo: String,
    /** Dirección a abrir (en una búsqueda, la de sus resultados). */
    val url: String,
    /** Texto a recuperar en la barra; sólo en las búsquedas. */
    val consulta: String? = null,
    /** Motor con el que se hizo la búsqueda (informativo: no se cambia). */
    val motor: String? = null,
    /** Veces que se ha usado (uso real, no filas duplicadas). */
    val visitas: Int = 1,
    val ultima: Long = 0L,
)

/** Sin acentos ni mayúsculas: «Música» y «musica» son lo mismo al buscar. */
internal fun plegar(s: String): String =
    Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

/**
 * ¿Esta URL es la página de resultados de algún motor conocido? Devuelve el
 * motor y el texto buscado.
 *
 * El texto se extrae de la propia plantilla del motor (`…?q=%s`): así una
 * búsqueda hecha desde la barra —que en el historial es sólo una URL más— puede
 * volver a ofrecerse como *búsqueda*, con su texto, y no como una dirección.
 */
fun busquedaDe(url: String, engines: List<SearchEngine>): Pair<SearchEngine, String>? {
    if (url.isEmpty()) return null
    engines.forEach { motor ->
        val corte = motor.template.indexOf("%s")
        if (corte <= 0) return@forEach
        val pre = motor.template.substring(0, corte)
        val post = motor.template.substring(corte + 2)
        if (!url.startsWith(pre)) return@forEach
        val cola = url.substring(pre.length).let { if (post.isEmpty()) it else it.substringBefore(post) }
        // La consulta termina donde empieza otro parámetro, el fragmento o la URL.
        val fin = listOf(cola.indexOf('&'), cola.indexOf('#')).filter { it >= 0 }.minOrNull() ?: cola.length
        val crudo = cola.substring(0, fin)
        if (crudo.isEmpty()) return@forEach
        val consulta = runCatching {
            URLDecoder.decode(crudo.replace("+", " "), "UTF-8")
        }.getOrDefault(crudo.replace("+", " "))
        if (consulta.isNotBlank()) return motor to consulta
    }
    return null
}

/**
 * Sugerencias para la barra a partir del historial **ya cargado** en memoria
 * (`HistoryDao.recent()`, como mucho 500 filas): filtrar aquí evita una consulta
 * a disco por cada pulsación y mantiene el trabajo fuera del hilo principal.
 *
 * - Una dirección por URL; una búsqueda por consulta (aunque se haya hecho con
 *   motores distintos). El uso se **acumula** en la misma sugerencia.
 * - Con texto escrito, se busca en la URL, el dominio, el título y la consulta
 *   (no sólo al principio), sin acentos y sin distinguir mayúsculas.
 * - Orden: primero lo que empieza igual a lo escrito, después lo más usado y
 *   por último lo más reciente.
 */
fun sugerencias(
    historial: List<HistoryEntry>,
    engines: List<SearchEngine>,
    texto: String,
    max: Int = MAX_SUGERENCIAS,
): List<Sugerencia> {
    val direcciones = LinkedHashMap<String, Sugerencia>()
    val busquedas = LinkedHashMap<String, Sugerencia>()

    historial.forEach { e ->
        val uso = e.visits.coerceAtLeast(1)
        val busqueda = busquedaDe(e.url, engines)
        if (busqueda == null) {
            val previa = direcciones[e.url]
            direcciones[e.url] = if (previa == null) {
                Sugerencia(
                    tipo = TipoSugerencia.DIRECCION,
                    titulo = e.title.ifBlank { hostOf(e.url) ?: e.url },
                    url = e.url,
                    visitas = uso,
                    ultima = e.visitedAt,
                )
            } else {
                previa.copy(
                    titulo = if (e.visitedAt >= previa.ultima && e.title.isNotBlank()) e.title else previa.titulo,
                    visitas = previa.visitas + uso,
                    ultima = maxOf(previa.ultima, e.visitedAt),
                )
            }
        } else {
            val (motor, consulta) = busqueda
            val clave = plegar(consulta)
            val previa = busquedas[clave]
            busquedas[clave] = if (previa == null) {
                Sugerencia(
                    tipo = TipoSugerencia.BUSQUEDA,
                    titulo = consulta,
                    url = e.url,
                    consulta = consulta,
                    motor = motor.name,
                    visitas = uso,
                    ultima = e.visitedAt,
                )
            } else {
                val masNueva = e.visitedAt >= previa.ultima
                previa.copy(
                    url = if (masNueva) e.url else previa.url,
                    motor = if (masNueva) motor.name else previa.motor,
                    visitas = previa.visitas + uso,
                    ultima = maxOf(previa.ultima, e.visitedAt),
                )
            }
        }
    }

    val todas = direcciones.values + busquedas.values
    val q = plegar(texto.trim())
    if (q.isEmpty()) {
        return todas
            .sortedWith(compareByDescending<Sugerencia> { it.visitas }.thenByDescending { it.ultima })
            .take(max)
    }

    return todas
        .mapNotNull { s ->
            val campos = listOfNotNull(s.url, hostOf(s.url), s.titulo, s.consulta).map(::plegar)
            if (campos.none { it.contains(q) }) null
            else s to campos.any { it.startsWith(q) }
        }
        .sortedWith(
            compareByDescending<Pair<Sugerencia, Boolean>> { it.second }
                .thenByDescending { it.first.visitas }
                .thenByDescending { it.first.ultima },
        )
        .take(max)
        .map { it.first }
}
