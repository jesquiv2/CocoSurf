package com.antena.tv.browser

import com.antena.tv.gecko.ExtInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sincronización catálogo ↔ instaladas (Paso 3): la sección «disponibles» es el
 * catálogo menos lo que Gecko informa como instalado en `list()`.
 *
 * Sin dispositivo de por medio esto sólo fija la regla pura (comparación de id y
 * de nombre); el ciclo real —instalar, desinstalar, salir y volver— se comprueba
 * en el emulador, no aquí.
 */
class ComplementosTiendaTest {

    private fun instalada(id: String, nombre: String, activa: Boolean = true) =
        ExtInfo(id = id, nombre = nombre, version = "1.0", activa = activa)

    @Test
    fun `sin nada instalado esta todo el catalogo`() {
        assertEquals(TIENDA.size, addonsDisponibles(emptyList()).size)
    }

    @Test
    fun `una instalada desaparece de disponibles`() {
        val disponibles = addonsDisponibles(
            listOf(instalada("uBlock0@raymondhill.net", "uBlock Origin")),
        )
        assertFalse(disponibles.any { it.nombre == "uBlock Origin" })
        assertTrue(disponibles.any { it.nombre == "Dark Reader" })
        assertTrue(disponibles.any { it.nombre == "SponsorBlock" })
    }

    @Test
    fun `el id manda aunque el nombre no coincida`() {
        val disponibles = addonsDisponibles(listOf(instalada("addon@darkreader.org", "Otro nombre")))
        assertFalse(disponibles.any { it.id == "addon@darkreader.org" })
    }

    @Test
    fun `el nombre sirve de respaldo si el id cambiara`() {
        val disponibles = addonsDisponibles(listOf(instalada("id-nuevo@ejemplo.org", "SponsorBlock")))
        assertFalse(disponibles.any { it.nombre == "SponsorBlock" })
    }

    @Test
    fun `una instalada desactivada sigue fuera de disponibles`() {
        val disponibles =
            addonsDisponibles(listOf(instalada("sponsorBlocker@ajay.app", "SponsorBlock", activa = false)))
        assertFalse(disponibles.any { it.nombre == "SponsorBlock" })
    }

    @Test
    fun `al desinstalar vuelve solo esa`() {
        val conTodo = TIENDA.map { instalada(it.id, it.nombre) }
        assertTrue(addonsDisponibles(conTodo).isEmpty())
        val trasQuitar = addonsDisponibles(conTodo.filterNot { it.id == "addon@darkreader.org" })
        assertEquals(listOf("Dark Reader"), trasQuitar.map { it.nombre })
    }

    @Test
    fun `otras extensiones instaladas no afectan al catalogo`() {
        // La extensión interna del navegador no está en la tienda.
        val disponibles = addonsDisponibles(listOf(instalada("navegador@antena.tv", "Antena")))
        assertEquals(TIENDA.size, disponibles.size)
    }

    @Test
    fun `el catalogo no repite ids ni nombres`() {
        assertEquals(TIENDA.size, TIENDA.map { it.id.lowercase() }.toSet().size)
        assertEquals(TIENDA.size, TIENDA.map { it.nombre.lowercase() }.toSet().size)
    }

    /* ── Paso 8 · catálogo ampliado y enlaces oficiales ── */

    @Test
    fun `el catalogo incluye las extensiones nuevas evaluadas`() {
        val ids = TIENDA.map { it.id.lowercase() }.toSet()
        // Ids tal como los declara el manifiesto firmado de cada .xpi en AMO.
        assertTrue("falta Chrome Mask", ids.contains("chrome-mask@overengineer.dev"))
        assertTrue("falta Privacy Badger", ids.contains("jid1-mnnxcxisbpnsxq@jetpack"))
        assertTrue("falta Google Search Fixer", ids.contains("{58c32ac4-0d6c-4d6f-ae2c-96aaf8ffcb66}"))
    }

    /**
     * Guarda contra el fallo real que hizo fracasar SponsorBlock (Paso 1): la
     * ficha y el `.xpi` tienen que ser del **mismo** slug de AMO. Un slug
     * inventado devuelve 404 y Gecko sólo sabe decir «InstallException null».
     */
    @Test
    fun `el slug del xpi y el de la ficha coinciden`() {
        TIENDA.forEach { addon ->
            val delXpi = slugDeAmox(addon.xpi)
            val deFicha = slugDeAmox(addon.ficha)
            assertNotNull("${addon.nombre}: el .xpi no es un enlace de AMO", delXpi)
            assertNotNull("${addon.nombre}: la ficha no es un enlace de AMO", deFicha)
            assertEquals("${addon.nombre}: .xpi y ficha deben ser del mismo complemento", deFicha, delXpi)
        }
    }

    @Test
    fun `el catalogo ya no usa el slug inexistente de sponsorblock`() {
        val sponsor = TIENDA.first { it.id == "sponsorBlocker@ajay.app" }
        assertFalse(sponsor.xpi.contains("sponsorblock-for-youtube"))
        assertTrue(sponsor.xpi.endsWith("/sponsorblock/latest.xpi"))
    }

    @Test
    fun `cada ficha del catalogo apunta a los enlaces firmados de AMO`() {
        TIENDA.forEach {
            assertTrue("${it.nombre}: .xpi no oficial", it.xpi.startsWith("https://addons.mozilla.org/firefox/downloads/latest/"))
            assertTrue("${it.nombre}: el .xpi no es el «latest» firmado", it.xpi.endsWith("/latest.xpi"))
            assertTrue("${it.nombre}: ficha no oficial", it.ficha.startsWith("https://addons.mozilla.org/"))
        }
    }

    @Test
    fun `el catalogo explica por que una ficha no cubre todo`() {
        // Sólo puede llevar nota quien tenga una limitación real y documentada;
        // si aparece una nota, tiene que decir qué falta y por qué.
        TIENDA.filter { it.nota != null }.forEach {
            assertTrue("${it.nombre}: nota vacía", it.nota!!.isNotBlank())
            assertTrue("${it.nombre}: la nota no explica la limitación", it.nota!!.length > 40)
        }
    }
}
