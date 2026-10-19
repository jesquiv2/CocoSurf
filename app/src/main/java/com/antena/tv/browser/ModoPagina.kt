package com.antena.tv.browser

/**
 * Cómo se presentan las páginas: «móvil» (lo que el sitio sirve a un teléfono) o
 * «escritorio». En una pantalla grande el diseño móvil estira los vídeos verticales
 * de Facebook, Instagram o TikTok y los deja cortados por los lados; el de escritorio
 * los muestra en su columna central completos. Por eso, por defecto, esos sitios
 * (y sólo esos) se piden como escritorio.
 */
object ModoPagina {
    /** "auto": escritorio sólo en [SITIOS]; "escritorio": siempre; "movil": nunca. */
    @Volatile
    var preferencia: String = "auto"

    val SITIOS = listOf("facebook.com", "fb.com", "fb.watch", "instagram.com", "tiktok.com")

    fun debeEscritorio(url: String): Boolean = when (preferencia) {
        "escritorio" -> true
        "movil" -> false
        else -> {
            val host = hostOf(url)?.lowercase().orEmpty()
            SITIOS.any { host == it || host.endsWith(".$it") }
        }
    }
}
