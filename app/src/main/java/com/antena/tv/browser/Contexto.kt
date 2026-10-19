package com.antena.tv.browser

import java.net.URLEncoder

/**
 * Paso 5 · menú contextual de las páginas web: la lógica pura que decide qué
 * muestra el menú, sin GeckoView ni Compose por medio (se prueba en JVM).
 *
 * El objetivo llega de dos caminos: la pulsación larga táctil (ContextElement
 * de Gecko, en WebTab) o «mantener OK» con el mando (caché de la extensión
 * interna). La selección de texto llega por SelectionActionDelegate, que
 * entrega el texto y el handle `Selection` con el que se ejecutan copiar/
 * pegar/etc. (ese handle vive en WebTab; aquí sólo se decide la forma del menú).
 *
 * Regla central: no aparece ninguna acción que no se pueda ejecutar. Sin
 * enlace no hay filas de enlace; sin texto seleccionado no hay filas de texto;
 * sin nada, sólo queda copiar la dirección de la página.
 */

/** Objetivo del menú: un enlace, una imagen, texto seleccionado, o varios. */
data class ContextTarget(
    val link: String? = null,
    val img: String? = null,
    /** Texto seleccionado en la página (null si no hay selección). */
    val texto: String? = null,
)

/** Acción concreta que una fila del menú ejecuta. */
enum class AccionContexto {
    // Enlace
    Abrir,
    AbrirEnPestana,
    AbrirSegundoPlano,
    CopiarVinculo,
    CompartirVinculo,
    GuardarVinculo,
    // Imagen
    GuardarImagen,
    CopiarImagen,
    CopiarVinculoImagen,
    BusquedaVisual,
    // Texto seleccionado
    CopiarSeleccion,
    BuscarSeleccion,
    CompartirSeleccion,
    SeleccionarTexto,
    // Página
    CopiarPagina,
    Atras,
    Adelante,
    Actualizar,
    TraducirPagina,
    GuardarPdf,
    Imprimir,
    NuevaPestana,
}

/** Fila ya decidida: encabezado de sección (una sola vez por grupo), etiqueta y acción. */
data class FilaContexto(val seccion: String?, val etiqueta: String, val accion: AccionContexto)

/** Las filas del menú para un objetivo, en orden Texto → Enlace → Imagen → Página. */
fun filasContexto(t: ContextTarget): List<FilaContexto> {
    val texto = t.texto?.trim()
    val link = t.link
    val img = t.img
    return buildList {
        if (!texto.isNullOrEmpty()) {
            add(FilaContexto("Texto", "Copiar", AccionContexto.CopiarSeleccion))
            add(FilaContexto(null, "Buscar", AccionContexto.BuscarSeleccion))
            add(FilaContexto(null, "Compartir", AccionContexto.CompartirSeleccion))
            add(FilaContexto(null, "Seleccionar texto", AccionContexto.SeleccionarTexto))
        }
        if (link != null) {
            add(FilaContexto("Enlace", "Abrir", AccionContexto.Abrir))
            add(FilaContexto(null, "Abrir en pestaña nueva", AccionContexto.AbrirEnPestana))
            add(FilaContexto(null, "Abrir en segundo plano", AccionContexto.AbrirSegundoPlano))
            add(FilaContexto(null, "Copiar vínculo", AccionContexto.CopiarVinculo))
            add(FilaContexto(null, "Compartir vínculo", AccionContexto.CompartirVinculo))
            add(FilaContexto(null, "Guardar vínculo como…", AccionContexto.GuardarVinculo))
        }
        if (img != null) {
            add(FilaContexto("Imagen", "Guardar imagen", AccionContexto.GuardarImagen))
            add(FilaContexto(null, "Copiar imagen", AccionContexto.CopiarImagen))
            add(FilaContexto(null, "Copiar vínculo de la imagen", AccionContexto.CopiarVinculoImagen))
            add(FilaContexto(null, "Búsqueda visual de la imagen", AccionContexto.BusquedaVisual))
        }
        if (isEmpty()) {
            add(FilaContexto(null, "Copiar dirección de la página", AccionContexto.CopiarPagina))
        }
    }
}

/**
 * Las acciones de la página entera, que van siempre al final del menú (con o sin
 * enlace, imagen o texto): navegar, actualizar, traducir, PDF, imprimir.
 * «Atrás» y «Adelante» sólo aparecen si hay historial a donde ir.
 */
fun filasPagina(puedeAtras: Boolean, puedeAdelante: Boolean): List<FilaContexto> = buildList {
    var primera = true
    fun fila(etiqueta: String, accion: AccionContexto) {
        add(FilaContexto(if (primera) "Página" else null, etiqueta, accion))
        primera = false
    }
    if (puedeAtras) fila("Atrás", AccionContexto.Atras)
    if (puedeAdelante) fila("Adelante", AccionContexto.Adelante)
    fila("Actualizar", AccionContexto.Actualizar)
    fila("Traducir al español", AccionContexto.TraducirPagina)
    fila("Guardar como PDF", AccionContexto.GuardarPdf)
    fila("Imprimir…", AccionContexto.Imprimir)
    fila("Pestaña nueva", AccionContexto.NuevaPestana)
    fila("Copiar dirección de la página", AccionContexto.CopiarPagina)
}

/** Título del menú: describe el elemento sobre el que se abrió. */
fun tituloContexto(t: ContextTarget): String = when {
    t.link != null && t.img != null -> "Imagen y enlace"
    t.img != null -> "Imagen"
    !t.texto.isNullOrBlank() -> "Texto seleccionado"
    t.link != null -> "Enlace"
    else -> "Página"
}

/**
 * Previsualización bajo el título: texto seleccionado, imagen, enlace o la
 * propia página. Nunca se muestra aquí una contraseña: los campos editables
 * (y los de contraseña) no pasan por este menú, se quedan con la barra nativa.
 */
fun muestraContexto(t: ContextTarget, pagina: String): String =
    t.texto?.takeIf { it.isNotBlank() } ?: t.img ?: t.link ?: pagina

/**
 * URL de búsqueda con el motor configurado (`template` con %s). Devuelve null
 * si el motor no tiene plantilla: mejor no buscar que buscar mal.
 */
fun urlDeBusqueda(texto: String, template: String): String? {
    if (!template.contains("%s")) return null
    return template.replace("%s", URLEncoder.encode(texto, "UTF-8"))
}
