// Paso 7 · identidad y controles INDIVIDUALES por extensión: la lógica pura que
// decide qué ocupa la barra, qué ofrece el panel de cada extensión y qué cuenta
// como «algo propio que abrir». Sin GeckoView ni Compose por medio (se prueba en
// JVM, igual que Contexto.kt del paso 5).
//
// Regla central, heredada del paso 5: **no se ofrece nada que no se pueda
// ejecutar**. Si una extensión no publica página de opciones, ni sitio, ni ficha
// en AMO, esas filas no existen; si un dato no lo informa el motor, no se
// inventa. Y un icono: el real de la extensión si el motor lo entrega
// (`MetaData.getIcon()`), y si no, la inicial estable de su nombre.
package com.antena.tv.browser

import com.antena.tv.gecko.EXT_INTERNA
import com.antena.tv.gecko.ExtInfo

/** Huecos de icono que caben en la barra antes de agrupar el resto. */
const val HUECOS_BARRA = 4

/** ¿Es la extensión del propio navegador? Nunca ocupa un hueco de la barra. */
fun esInterna(id: String): Boolean = id == EXT_INTERNA

/** Reparto de la barra: los chips que se ven y los que quedan tras la salida «＋N». */
data class RepartoIconos(
    val visibles: List<ExtInfo>,
    val ocultas: List<ExtInfo>,
    /** Rótulo del chip de desborde («＋3»), o null si caben todas. */
    val rotuloDesborde: String?,
)

/** Rótulo del chip que agrupa las extensiones que no caben. */
fun rotuloDesborde(cuantas: Int): String = "＋$cuantas"

/**
 * Un chip por extensión instalada (excepto la interna). Si no caben todas, el
 * último hueco se reserva para la salida que agrupa el resto: así ninguna
 * extensión pierde su acceso (y sigue con sus controles individuales en el panel
 * de todas y en Complementos), sin desbordar la barra.
 */
fun repartoIconos(extensiones: List<ExtInfo>, huecos: Int = HUECOS_BARRA): RepartoIconos {
    val utiles = extensiones.filterNot { esInterna(it.id) }
    if (utiles.size <= huecos) return RepartoIconos(utiles, emptyList(), null)
    // huecos - 1 para el chip de desborde; si no hay ni para eso, todo dentro.
    val visibles = utiles.take((huecos - 1).coerceAtLeast(0))
    val ocultas = utiles.drop(visibles.size)
    if (visibles.isEmpty()) return RepartoIconos(emptyList(), ocultas, rotuloDesborde(ocultas.size))
    return RepartoIconos(visibles, ocultas, rotuloDesborde(ocultas.size))
}

/* ── Panel individual de una extensión ── */

/** Qué hace cada fila del panel. */
enum class AccionExtension {
    /** Abrir la interfaz propia (popup/panel) de la extensión. */
    AbrirInterfaz,
    /** Abrir su página de opciones (`MetaData.getOptionsPageUrl()`). */
    Opciones,
    /** Abrir el sitio de su autor (`MetaData.getHomepageUrl()`). */
    SitioAutor,
    /** Abrir su ficha en addons.mozilla.org (`MetaData.getAmoListingUrl()`). */
    FichaAmo,
    /** Activar o desactivar esta extensión (real, con EnableSource.USER). */
    Alternar,
    /** Ir a Complementos: la lista de todas, con instalar/desinstalar. */
    Administrar,
}

data class FilaExtension(val etiqueta: String, val accion: AccionExtension)

/**
 * Filas del panel de UNA extensión. `tieneInterfaz` lo decide quien conoce el
 * motor (hay acción de barra ⇒ la extensión tiene con qué responder al clic).
 */
fun filasExtension(ext: ExtInfo, tieneInterfaz: Boolean): List<FilaExtension> = buildList {
    if (tieneInterfaz) {
        add(FilaExtension("Abrir su panel", AccionExtension.AbrirInterfaz))
    }
    if (urlSegura(ext.opciones) != null) {
        add(FilaExtension("Opciones y panel de control", AccionExtension.Opciones))
    }
    if (urlSegura(ext.web) != null) {
        add(FilaExtension("Sitio del autor", AccionExtension.SitioAutor))
    }
    if (urlSegura(ext.ficha) != null) {
        add(FilaExtension("Ficha en addons.mozilla.org", AccionExtension.FichaAmo))
    }
    add(
        FilaExtension(
            if (ext.activa) "Desactivar esta extensión" else "Activar esta extensión",
            AccionExtension.Alternar,
        ),
    )
    add(FilaExtension("Administrar todas las extensiones", AccionExtension.Administrar))
}

/** Estado real de la extensión, tal como lo informa `list()`. */
fun etiquetaEstado(ext: ExtInfo): String = if (ext.activa) "● ACTIVA" else "○ DESACTIVADA"

/** Subtítulo del panel: sólo los datos que el motor entrega de verdad. */
fun subtituloExtension(ext: ExtInfo): String {
    val version = "v${ext.version?.takeIf { it.isNotBlank() } ?: "?"}"
    val autor = ext.autor?.takeIf { it.isNotBlank() } ?: return version
    return "$version · $autor"
}

/**
 * Icono alternativo cuando el motor no entrega imagen: las iniciales del nombre
 * (primera letra y, si hay segunda palabra con mayúscula propia, su inicial).
 * Es estable y distinguible entre extensiones, que es lo que se pide.
 */
fun inicialExtension(nombre: String): String {
    val palabras = nombre.split(' ', '-', '_').filter { p -> p.any { it.isLetterOrDigit() } }
    val primera = palabras.firstOrNull()?.firstOrNull { it.isLetterOrDigit() } ?: return "?"
    val segunda = palabras.drop(1).firstOrNull()?.firstOrNull { it.isUpperCase() }
    val inicial = primera.uppercaseChar().toString()
    return if (segunda != null) inicial + segunda.uppercaseChar() else inicial
}

/**
 * URL que la app está dispuesta a abrir: sólo `moz-extension:` (las páginas
 * propias de la extensión) y http/https. Cualquier otro esquema (`javascript:`,
 * `data:`, vacío) se descarta en vez de intentar abrirlo.
 */
fun urlSegura(url: String?): String? {
    val limpia = url?.trim().orEmpty()
    if (limpia.isEmpty()) return null
    val ok = limpia.startsWith("moz-extension://") ||
        limpia.startsWith("https://") ||
        limpia.startsWith("http://")
    return limpia.takeIf { ok }
}
