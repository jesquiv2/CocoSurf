package com.antena.tv.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Paso 5 · menú contextual: qué filas muestra el menú según el elemento sobre
 * el que se pulse (texto seleccionado, enlace, imagen o página).
 *
 * La regla que fija este test: no aparece ninguna acción que no se pueda
 * ejecutar (sin enlace no hay filas de enlace; sin selección no hay filas de
 * texto), y el orden de las secciones es Texto → Enlace → Imagen → Página.
 */
class ContextoTest {

    @Test
    fun `la seleccion de texto ofrece copiar buscar compartir y seleccionar`() {
        val filas = filasContexto(ContextTarget(texto = "hola mundo"))
        assertEquals(
            listOf("Copiar", "Buscar", "Compartir", "Seleccionar texto"),
            filas.map { it.etiqueta },
        )
        // La sección se declara una sola vez, en la primera fila del grupo.
        assertEquals("Texto", filas.first().seccion)
        assertEquals(null, filas[1].seccion)
        assertEquals(
            listOf(
                AccionContexto.CopiarSeleccion,
                AccionContexto.BuscarSeleccion,
                AccionContexto.CompartirSeleccion,
                AccionContexto.SeleccionarTexto,
            ),
            filas.map { it.accion },
        )
    }

    @Test
    fun `una seleccion vacia no ofrece nada de texto`() {
        // Texto en blanco = no hay selección: sólo queda el menú de página.
        assertEquals(
            listOf(AccionContexto.CopiarPagina),
            filasContexto(ContextTarget(texto = "")).map { it.accion },
        )
        assertEquals(
            listOf(AccionContexto.CopiarPagina),
            filasContexto(ContextTarget(texto = "   ")).map { it.accion },
        )
    }

    @Test
    fun `el enlace ofrece abrir pestaña nueva segundo plano copiar compartir guardar`() {
        val filas = filasContexto(ContextTarget(link = "https://x.test/a"))
        assertEquals(
            listOf(
                "Abrir",
                "Abrir en pestaña nueva",
                "Abrir en segundo plano",
                "Copiar vínculo",
                "Compartir vínculo",
                "Guardar vínculo como…",
            ),
            filas.map { it.etiqueta },
        )
        assertEquals("Enlace", filas.first().seccion)
    }

    @Test
    fun `la imagen conserva sus filas y suma «usar como fondo»`() {
        val filas = filasContexto(ContextTarget(img = "https://x.test/i.png"))
        assertEquals(
            listOf(
                "Guardar imagen",
                "Copiar imagen",
                "Usar como fondo de inicio",
                "Copiar vínculo de la imagen",
                "Búsqueda visual de la imagen",
            ),
            filas.map { it.etiqueta },
        )
        assertEquals("Imagen", filas.first().seccion)
    }

    @Test
    fun `imagen y enlace juntos muestran las dos secciones`() {
        val filas = filasContexto(ContextTarget(link = "https://x.test/a", img = "https://x.test/i.png"))
        assertEquals(6 + 5, filas.size)
        assertEquals("Enlace", filas.first().seccion)
        assertTrue(filas.any { it.seccion == "Imagen" })
    }

    @Test
    fun `con seleccion y enlace el texto va primero`() {
        val filas = filasContexto(ContextTarget(link = "https://x.test/a", texto = "hola"))
        assertEquals("Texto", filas.first().seccion)
        assertTrue(filas.any { it.seccion == "Enlace" })
        assertEquals(4 + 6, filas.size)
    }

    @Test
    fun `sin elemento solo se copia la direccion de la pagina`() {
        val filas = filasContexto(ContextTarget())
        assertEquals(listOf("Copiar dirección de la página"), filas.map { it.etiqueta })
        assertEquals(listOf(AccionContexto.CopiarPagina), filas.map { it.accion })
        assertEquals(null, filas.single().seccion)
    }

    @Test
    fun `la pagina siempre ofrece actualizar traducir pdf imprimir y pestaña nueva`() {
        val filas = filasPagina(puedeAtras = false, puedeAdelante = false)
        assertEquals(
            listOf(
                AccionContexto.Actualizar, AccionContexto.TraducirPagina, AccionContexto.GuardarPdf,
                AccionContexto.Imprimir, AccionContexto.NuevaPestana, AccionContexto.CopiarPagina,
            ),
            filas.map { it.accion },
        )
        assertEquals("Página", filas.first().seccion)
        assertEquals(1, filas.count { it.seccion != null })
    }

    @Test
    fun `atras y adelante solo aparecen si hay historial`() {
        val con = filasPagina(puedeAtras = true, puedeAdelante = true).map { it.accion }
        assertEquals(AccionContexto.Atras, con[0])
        assertEquals(AccionContexto.Adelante, con[1])
        val sin = filasPagina(puedeAtras = false, puedeAdelante = true).map { it.accion }
        assertEquals(AccionContexto.Adelante, sin[0])
        assertEquals(false, sin.contains(AccionContexto.Atras))
    }

    @Test
    fun `el titulo del menu describe el elemento`() {
        assertEquals("Imagen y enlace", tituloContexto(ContextTarget(link = "l", img = "i")))
        assertEquals("Imagen", tituloContexto(ContextTarget(img = "i")))
        assertEquals("Texto seleccionado", tituloContexto(ContextTarget(texto = "hola")))
        assertEquals("Texto seleccionado", tituloContexto(ContextTarget(link = "l", texto = "hola")))
        assertEquals("Enlace", tituloContexto(ContextTarget(link = "l")))
        assertEquals("Página", tituloContexto(ContextTarget()))
    }

    @Test
    fun `la previsualizacion muestra lo que hay, en orden texto-imagen-enlace-pagina`() {
        val pagina = "https://principal.test/"
        assertEquals("hola", muestraContexto(ContextTarget(texto = "hola"), pagina))
        assertEquals("https://x/i.png", muestraContexto(ContextTarget(img = "https://x/i.png"), pagina))
        assertEquals("https://x/a", muestraContexto(ContextTarget(link = "https://x/a"), pagina))
        assertEquals(pagina, muestraContexto(ContextTarget(), pagina))
    }

    @Test
    fun `la busqueda usa el motor configurado y codifica el texto`() {
        assertEquals(
            "https://duckduckgo.com/?q=hola+mundo",
            urlDeBusqueda("hola mundo", "https://duckduckgo.com/?q=%s"),
        )
        assertEquals(
            "https://motor.test/buscar?q=se%C3%B1al",
            urlDeBusqueda("señal", "https://motor.test/buscar?q=%s"),
        )
    }

    @Test
    fun `un motor sin plantilla no genera busqueda`() {
        assertNull(urlDeBusqueda("hola", "https://x.test/"))
    }

    private fun assertTrue(cond: Boolean) = org.junit.Assert.assertTrue(cond)
}
