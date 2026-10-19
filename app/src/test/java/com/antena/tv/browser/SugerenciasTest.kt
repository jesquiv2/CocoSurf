package com.antena.tv.browser

import com.antena.tv.db.HistoryEntry
import com.antena.tv.db.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reglas puras de la barra de direcciones (Paso 4): qué es una búsqueda y qué
 * una dirección, cómo se agrupa el historial sin repetir y cómo se filtran y
 * ordenan las sugerencias.
 *
 * Sin dispositivo de por medio esto sólo fija la regla; el comportamiento real
 * (texto conservado, desplegable con el mando, navegación) se comprueba en el
 * emulador, no aquí.
 */
class SugerenciasTest {

    private val google = SearchEngine("google", "Google", "https://www.google.com/search?q=%s")
    private val ddg = SearchEngine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s")
    private val bing = SearchEngine("bing", "Bing", "https://www.bing.com/search?q=%s")
    private val motores = listOf(google, ddg, bing)

    private var t = 1_000L

    private fun visita(url: String, titulo: String, visitas: Int = 1, at: Long = t++) =
        HistoryEntry("h-$at-$url", url, titulo, at, visitas)

    private fun sugerir(historial: List<HistoryEntry>, texto: String = "", max: Int = MAX_SUGERENCIAS) =
        sugerencias(historial, motores, texto, max)

    /* ── Direcciones vs búsquedas ── */

    @Test
    fun `una pagina de resultados se reconoce como busqueda con su texto`() {
        assertEquals(ddg to "prueba", busquedaDe("https://duckduckgo.com/?q=prueba&ia=web", motores))
        assertEquals(bing to "hola mundo", busquedaDe("https://www.bing.com/search?q=hola+mundo&form=QBLH", motores))
    }

    @Test
    fun `la busqueda se recupera tal como se escribio`() {
        val codificada = "https://www.google.com/search?q=moz-extension%3A%2F%2FuBlock0%40raymondhill.net+dashboard"
        assertEquals(google to "moz-extension://uBlock0@raymondhill.net dashboard", busquedaDe(codificada, motores))
    }

    @Test
    fun `una direccion normal no es una busqueda`() {
        assertNull(busquedaDe("https://es.wikipedia.org/wiki/Wikipedia:Portada", motores))
        assertNull(busquedaDe("https://example.com/?q=algo", motores))
        assertNull(busquedaDe("https://duckduckgo.com/?ia=web", motores))
        assertNull(busquedaDe("", motores))
    }

    @Test
    fun `una busqueda se ofrece como busqueda y una pagina como direccion`() {
        val lista = sugerir(
            listOf(
                visita("https://duckduckgo.com/?q=prueba&ia=web", "prueba at DuckDuckGo"),
                visita("https://example.com/", "Example Domain"),
            ),
        )
        val busqueda = lista.single { it.tipo == TipoSugerencia.BUSQUEDA }
        val direccion = lista.single { it.tipo == TipoSugerencia.DIRECCION }
        assertEquals("prueba", busqueda.consulta)
        assertEquals("DuckDuckGo", busqueda.motor)
        assertEquals("https://example.com/", direccion.url)
        assertEquals("Example Domain", direccion.titulo)
        assertNull(direccion.consulta)
    }

    /* ── Agrupado: uso, no duplicados ── */

    @Test
    fun `la misma direccion es una sola sugerencia con el uso sumado`() {
        val lista = sugerir(
            listOf(
                visita("https://example.com/", "Example Domain", 1, 10),
                visita("https://example.com/", "Example Domain", 3, 40),
                visita("https://example.com/", "Otro titulo viejo", 1, 20),
            ),
        )
        assertEquals(1, lista.size)
        assertEquals(5, lista.first().visitas)
        assertEquals("Example Domain", lista.first().titulo)
    }

    @Test
    fun `la misma busqueda no se repite aunque se haga con otro motor`() {
        val lista = sugerir(
            listOf(
                visita("https://duckduckgo.com/?q=musica+para+estudiar", "musica para estudiar at DuckDuckGo"),
                visita("https://www.google.com/search?q=musica%20para%20estudiar", "musica para estudiar - Google"),
            ),
        )
        assertEquals(1, lista.size)
        assertEquals(2, lista.first().visitas)
        assertEquals("musica para estudiar", lista.first().consulta)
        assertEquals(TipoSugerencia.BUSQUEDA, lista.first().tipo)
    }

    @Test
    fun `sin texto primero lo mas usado y luego lo mas reciente`() {
        val lista = sugerir(
            listOf(
                visita("https://poco.com/", "Poco", 1, 100),
                visita("https://mucho.com/", "Mucho", 9, 10),
                visita("https://medio.com/", "Medio", 4, 50),
            ),
        )
        assertEquals(listOf("mucho.com", "medio.com", "poco.com"), lista.map { hostOf(it.url) })
    }

    /* ── Filtro ── */

    @Test
    fun `encuentra por dominio aunque no sea el principio de la direccion`() {
        val historial = listOf(
            visita("https://es.wikipedia.org/wiki/Wikipedia:Portada", "Wikipedia, la enciclopedia libre"),
            visita("https://www.marca.com/", "MARCA - Diario online"),
        )
        val lista = sugerir(historial, "wiki")
        assertEquals(1, lista.size)
        assertEquals("https://es.wikipedia.org/wiki/Wikipedia:Portada", lista.first().url)
    }

    @Test
    fun `encuentra por titulo de la pagina`() {
        val historial = listOf(
            visita("https://developer.mozilla.org/en-US/", "MDN Web Docs"),
            visita("https://github.com/", "GitHub · Change is constant"),
        )
        val lista = sugerir(historial, "mozilla")
        assertEquals(1, lista.size)
        assertEquals("MDN Web Docs", lista.first().titulo)
    }

    @Test
    fun `encuentra una busqueda anterior por su texto y sin acentos`() {
        val historial = listOf(
            // URL codificada como la deja el navegador: «música».
            visita("https://duckduckgo.com/?q=m%C3%BAsica&ia=web", "música at DuckDuckGo"),
            visita("https://example.com/", "Example Domain"),
        )
        val lista = sugerir(historial, "musica")
        assertEquals(1, lista.size)
        assertEquals("música", lista.first().consulta)
    }

    @Test
    fun `lo que empieza igual va antes que lo que solo contiene`() {
        val lista = sugerir(
            listOf(
                // «api.github.com» contiene «git» pero no empieza por él; su
                // título tampoco (si empezara, el desempate no probaría nada).
                visita("https://api.github.com/repos", "Repos API", 1, 10),
                visita("https://github.com/", "GitHub", 1, 5),
            ),
            "git",
        )
        assertEquals("github.com", hostOf(lista.first().url))
    }

    @Test
    fun `sin coincidencias la lista queda vacia`() {
        val lista = sugerir(listOf(visita("https://example.com/", "Example Domain")), "zzzz")
        assertTrue(lista.isEmpty())
    }

    /* ── Límites ── */

    @Test
    fun `ofrece como mucho quince sugerencias`() {
        val historial = (1..30).map { visita("https://sitio$it.com/", "Sitio $it") }
        assertEquals(MAX_SUGERENCIAS, sugerir(historial).size)
        assertEquals(MAX_SUGERENCIAS, sugerir(historial, "sitio").size)
    }

    @Test
    fun `con doce direcciones ofrece las doce`() {
        val historial = (1..12).map { visita("https://sitio$it.com/", "Sitio $it") }
        assertEquals(12, sugerir(historial, "sitio").size)
    }

    @Test
    fun `un historial vacio no ofrece nada ni falla`() {
        assertTrue(sugerir(emptyList()).isEmpty())
        assertTrue(sugerir(emptyList(), "algo").isEmpty())
    }

    /* ── hostOf (ahora sin android.net.Uri, para poder probarlo aquí) ── */

    @Test
    fun `el host se saca igual que antes`() {
        assertEquals("example.com", hostOf("https://example.com/"))
        assertEquals("es.wikipedia.org", hostOf("https://es.wikipedia.org/wiki/Portada"))
        assertEquals("antena.interna", hostOf("antena://antena.interna"))
        assertEquals("localhost", hostOf("http://localhost:8787/x"))
        assertEquals("host", hostOf("https://usuario:clave@host:8080/x"))
        assertEquals("example.com", hostOf("example.com"))
        assertEquals("about:blank", hostOf("about:blank"))
        assertNull(hostOf(""))
    }
}
