package com.antena.tv.browser

import com.antena.tv.gecko.ExtInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Paso 7 · identidad y controles por extensión: se fija aquí la regla pura
 * (qué huecos ocupa la barra, qué filas ofrece el panel de cada extensión y qué
 * se considera «algo propio que abrir»). Sin Gecko ni Compose por medio.
 *
 * Lo que NO se puede fijar aquí —y se comprueba en el emulador— es que el icono
 * salga del motor, que activar/desactivar cambie de verdad el estado y que el
 * popup de la extensión se abra: eso depende de GeckoView.
 */
class ExtensionesTest {

    private fun ext(
        id: String = "uBlock0@raymondhill.net",
        nombre: String = "uBlock Origin",
        version: String? = "1.62.0",
        activa: Boolean = true,
        descripcion: String? = null,
        autor: String? = null,
        opciones: String? = null,
        web: String? = null,
        ficha: String? = null,
    ) = ExtInfo(
        id = id,
        nombre = nombre,
        version = version,
        activa = activa,
        descripcion = descripcion,
        autor = autor,
        opciones = opciones,
        web = web,
        ficha = ficha,
    )

    /* ── La barra: un hueco por extensión, sin desbordar ── */

    @Test
    fun `sin extensiones no hay chips ni desborde`() {
        val r = repartoIconos(emptyList())
        assertTrue(r.visibles.isEmpty())
        assertTrue(r.ocultas.isEmpty())
        assertNull(r.rotuloDesborde)
    }

    @Test
    fun `tres extensiones caben enteras`() {
        val r = repartoIconos(listOf(ext("a"), ext("b"), ext("c")))
        assertEquals(listOf("a", "b", "c"), r.visibles.map { it.id })
        assertTrue(r.ocultas.isEmpty())
        assertNull(r.rotuloDesborde)
    }

    @Test
    fun `seis extensiones con cuatro huecos dejan una salida para las tres ultimas`() {
        val seis = (1..6).map { ext(id = "e$it", nombre = "Ext $it") }
        val r = repartoIconos(seis, huecos = 4)
        // El último hueco lo ocupa la salida «＋N»: tres chips y tres dentro.
        assertEquals(listOf("e1", "e2", "e3"), r.visibles.map { it.id })
        assertEquals(listOf("e4", "e5", "e6"), r.ocultas.map { it.id })
        assertEquals("＋3", r.rotuloDesborde)
    }

    @Test
    fun `justo los que caben no dejan desborde`() {
        val cuatro = (1..4).map { ext(id = "e$it", nombre = "Ext $it") }
        val r = repartoIconos(cuatro, huecos = 4)
        assertEquals(4, r.visibles.size)
        assertTrue(r.ocultas.isEmpty())
        assertNull(r.rotuloDesborde)
    }

    @Test
    fun `la extension interna del navegador nunca ocupa un hueco`() {
        val r = repartoIconos(listOf(ext(id = "navegador@antena.tv", nombre = "Antena"), ext("a")))
        assertEquals(listOf("a"), r.visibles.map { it.id })
    }

    @Test
    fun `un hueco imposible no revienta el reparto`() {
        // Un solo hueco para dos extensiones: no cabe ningún chip, pero las dos
        // siguen accesibles tras la salida de desborde.
        val r = repartoIconos(listOf(ext("a"), ext("b")), huecos = 1)
        assertTrue(r.visibles.isEmpty())
        assertEquals(listOf("a", "b"), r.ocultas.map { it.id })
        assertEquals("＋2", r.rotuloDesborde)
    }

    /* ── El panel individual: sólo lo que de verdad existe ── */

    @Test
    fun `una extension con interfaz y opciones ofrece las filas en orden`() {
        val e = ext(
            opciones = "moz-extension://abc/dashboard.html",
            web = "https://github.com/gorhill/uBlock",
            ficha = "https://addons.mozilla.org/es-ES/firefox/addon/ublock-origin/",
        )
        val filas = filasExtension(e, tieneInterfaz = true)
        assertEquals(
            listOf(
                AccionExtension.AbrirInterfaz,
                AccionExtension.Opciones,
                AccionExtension.SitioAutor,
                AccionExtension.FichaAmo,
                AccionExtension.Alternar,
                AccionExtension.Administrar,
            ),
            filas.map { it.accion },
        )
    }

    @Test
    fun `sin interfaz propia no se ofrece abrirla`() {
        val filas = filasExtension(ext(), tieneInterfaz = false)
        assertFalse(filas.any { it.accion == AccionExtension.AbrirInterfaz })
        // Lo que sí existe siempre: el estado y la administración.
        assertEquals(
            listOf(AccionExtension.Alternar, AccionExtension.Administrar),
            filas.map { it.accion },
        )
    }

    @Test
    fun `una extension sin opciones ni web ni ficha no inventa filas`() {
        val filas = filasExtension(ext(), tieneInterfaz = true)
        assertEquals(
            listOf(AccionExtension.AbrirInterfaz, AccionExtension.Alternar, AccionExtension.Administrar),
            filas.map { it.accion },
        )
    }

    @Test
    fun `una opcion con esquema raro no se ofrece`() {
        val filas = filasExtension(ext(opciones = "javascript:alert(1)"), tieneInterfaz = true)
        assertFalse(filas.any { it.accion == AccionExtension.Opciones })
        assertNull(urlSegura("javascript:alert(1)"))
        assertNull(urlSegura("  "))
        assertNull(urlSegura(null))
    }

    @Test
    fun `el enlace de la ficha y el del autor son opcionales`() {
        assertEquals("https://ejemplo.org/", urlSegura("https://ejemplo.org/"))
        assertEquals("moz-extension://abc/panel.html", urlSegura("moz-extension://abc/panel.html"))
    }

    @Test
    fun `la etiqueta de alternar nombra la accion real que se hara`() {
        val activa = filasExtension(ext(activa = true), tieneInterfaz = false).first()
        val apagada = filasExtension(ext(activa = false), tieneInterfaz = false).first()
        assertEquals("Desactivar esta extensión", activa.etiqueta)
        assertEquals("Activar esta extensión", apagada.etiqueta)
    }

    /* ── Estado, subtítulo e icono alternativo ── */

    @Test
    fun `el estado visible corresponde al estado real`() {
        assertEquals("● ACTIVA", etiquetaEstado(ext(activa = true)))
        assertEquals("○ DESACTIVADA", etiquetaEstado(ext(activa = false)))
    }

    @Test
    fun `el subtitulo solo junta datos que existen`() {
        assertEquals("v1.62.0 · Raymond Hill", subtituloExtension(ext(autor = "Raymond Hill")))
        assertEquals("v1.62.0", subtituloExtension(ext()))
        assertEquals("v? · Raymond Hill", subtituloExtension(ext(version = null, autor = "Raymond Hill")))
        assertEquals("v?", subtituloExtension(ext(version = null)))
    }

    @Test
    fun `sin icono del motor queda una inicial estable y distinguible`() {
        assertEquals("UO", inicialExtension("uBlock Origin"))
        assertEquals("DR", inicialExtension("Dark Reader"))
        assertEquals("S", inicialExtension("SponsorBlock"))
        assertEquals("?", inicialExtension(""))
        // Estable: el mismo nombre da siempre el mismo glifo.
        assertEquals(inicialExtension("Dark Reader"), inicialExtension("Dark Reader"))
    }

    @Test
    fun `el desborde se rotula con lo que queda dentro`() {
        assertEquals("＋3", rotuloDesborde(3))
        assertEquals("＋1", rotuloDesborde(1))
    }

    @Test
    fun `se distingue la extension del propio navegador`() {
        assertTrue(esInterna("navegador@antena.tv"))
        assertFalse(esInterna("uBlock0@raymondhill.net"))
    }
}
