package com.antena.tv.browser

import android.content.Context
import android.view.KeyEvent
import com.antena.tv.gecko.GeckoMotor
import com.antena.tv.log.Bitacora
import com.antena.tv.log.LogKind
import java.net.URLEncoder
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.BasicSelectionActionDelegate
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse

/**
 * Una pestaña · motor GeckoView (Firefox) — el motor ÚNICO de Antena desde
 * 0.3.0-m4 (PLAN §20: swap completo; WebView eliminada). Sostiene su GeckoView
 * fuera de la composición para que la página sobreviva a cambios de pestaña
 * activa, y expone los mismos callbacks con los que BrowserScreen ya trabajaba.
 *
 * Ventajas sobre la WebView anterior: las extensiones (uBO…) navegan en todas
 * las pestañas, PDF.js y el reproductor de vídeo vienen de serie, y el sitio se
 * ve como Firefox Android (no «WebView embebida»).
 */
class WebTab(
    val id: Int,
    context: Context,
    /**
     * `laAbreGecko` = ventana emergente (OnNewSession): GeckoView exige que la
     * sesión devuelta esté SIN abrir y es él quien la abre con su id interno
     * (afirma «Must use an unopened GeckoSession instance» y crashea la app si
     * ya está abierta). En ese caso la vista se acopla al abrirse.
     */
    sesion: GeckoSession = GeckoSession(),
    private val laAbreGecko: Boolean = false,
) {
    val view: GeckoView = GeckoView(context)
    val session: GeckoSession = sesion
    /** Paso 5 · la barra nativa de selección la arranca GeckoView con `Activity`. */
    private val actividad = context as? android.app.Activity

    var onTitle: (String) -> Unit = {}
    var onUrl: (String) -> Unit = {}
    var onProgress: (Int) -> Unit = {}
    var onHistory: (Boolean) -> Unit = {}
    /** Descarga detectada (archivo que Gecko no puede mostrar en pantalla). */
    var onDownload: (url: String, mime: String?, disposition: String?) -> Unit = { _, _, _ -> }
    /** Página terminada (o fallida): url y si la carga tuvo éxito. */
    var onLoaded: (url: String, ok: Boolean) -> Unit = { _, _ -> }
    /** Scroll vertical de la página (px): alimenta el plegado de la barra. */
    var onScroll: (scrollY: Int) -> Unit = {}
    /** Enlace descargable/offline (PDF/media/archivos): la app lo gestiona. */
    var onIntercept: (url: String) -> Boolean = { false }
    /** Mantener OK / pulsación larga sobre enlace o imagen → menú contextual. */
    var onContext: (link: String?, img: String?) -> Unit = { _, _ -> }
    /** Paso 5 · texto seleccionado (no editable) → menú con Copiar/Buscar/Compartir. */
    var onSelection: (texto: String, seleccion: GeckoSession.SelectionActionDelegate.Selection) -> Unit =
        { _, _ -> }
    /** target=_blank / window.open: devuelve la sesión de la pestaña nueva. */
    var onNewWindow: (userGesture: Boolean, url: String) -> GeckoSession? = { _, _ -> null }
    /** window.close(): la pestaña se retira sola. */
    var onWindowClosed: (WebTab) -> Unit = {}
    /** La página pide (o deja) la pantalla completa: vídeos de YouTube, anime… */
    var onPantallaCompleta: (Boolean) -> Unit = {}
    /** El proceso de la página cayó (falta de memoria o fallo): hay que recrearla. */
    var onProcesoCaido: (motivo: String) -> Unit = {}

    /** Última URL conocida de la sesión (onPageStop no la trae). */
    var url = ""
        private set
    var title = ""
        private set
    /** Scroll vertical actual (para «ir arriba» y el gesto ↑ al tope). */
    var scrollY = 0
        private set

    /** ¿Se puede volver atrás? (onCanGoBack de Gecko). */
    var canGoBack = false
        private set

    /** ¿Se puede ir adelante? (onCanGoForward de Gecko). */
    var canGoForward = false
        private set

    /** Paso 5 · selección activa: el handle con copy()/paste()/selectAll()… */
    var seleccion: GeckoSession.SelectionActionDelegate.Selection? = null
        private set
    /** Paso 5 · texto de la última selección: alimenta «mantener OK» con el mando. */
    var ultimoTextoSeleccionado = ""
        private set
    /** Paso 5 · selección que el usuario descartó (cerró su menú): no se reabre por ese texto. */
    var textoSeleccionadoDescartado = ""

    init {
        engancha(GeckoMotor.runtime(context), session, abrir = !laAbreGecko)
        if (laAbreGecko) {
            acoplarViewAlAbrir()
        } else {
            view.setSession(session)
        }
        GeckoMotor.registrarSesion(session)
    }

    /**
     * La sesión de una ventana emergente la abre GeckoView (OnNewSession):
     * en cuanto esté abierta se acopla la vista de la pestaña.
     */
    private fun acoplarViewAlAbrir(intentos: Int = 0) {
        if (session.isOpen) {
            runCatching { view.setSession(session) }
        } else if (intentos < 60) {
            // 50 ms por frame: como mucho 3 s antes de rendirse.
            view.postDelayed({ acoplarViewAlAbrir(intentos + 1) }, 50)
        }
    }

    /** Conecta los delegates de Gecko con los callbacks de la pestaña. */
    private fun engancha(
        runtime: org.mozilla.geckoview.GeckoRuntime,
        s: GeckoSession,
        abrir: Boolean = true,
    ) {
        if (abrir && !s.isOpen) s.open(runtime)

        s.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                permissions: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                if (url != null) {
                    this@WebTab.url = url
                    onUrl(url)
                }
            }

            override fun onCanGoBack(session: GeckoSession, puedeVolver: Boolean) {
                this@WebTab.canGoBack = puedeVolver
                onHistory(puedeVolver)
            }

            override fun onCanGoForward(session: GeckoSession, puedeAvanzar: Boolean) {
                this@WebTab.canGoForward = puedeAvanzar
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny> {
                // Modo de página: si el sitio pide otro (escritorio ↔ móvil), se cambia en
                // la sesión y se vuelve a pedir la dirección con el nuevo «user agent».
                if (request.uri.startsWith("http") &&
                    request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_CURRENT
                ) {
                    val quiere = ModoPagina.debeEscritorio(request.uri)
                    val ajustes = session.settings
                    val esc = ajustes.userAgentMode == GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                    if (quiere != esc) {
                        ajustes.userAgentMode = if (quiere) {
                            GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                        } else {
                            GeckoSessionSettings.USER_AGENT_MODE_MOBILE
                        }
                        ajustes.viewportMode = if (quiere) {
                            GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                        } else {
                            GeckoSessionSettings.VIEWPORT_MODE_MOBILE
                        }
                        val destino = request.uri
                        view.post { session.loadUri(destino) }
                        return GeckoResult.deny()
                    }
                }
                // PDF/media/archivos: la app los baja o los abre sin navegar
                // (mismo papel que shouldOverrideUrlLoading en la WebView).
                if (onIntercept(request.uri)) return GeckoResult.deny()
                return GeckoResult.allow()
            }

            override fun onNewSession(
                session: GeckoSession,
                url: String,
            ): GeckoResult<GeckoSession>? {
                // Ventana emergente: BrowserScreen crea la pestaña y devuelve
                // su sesión; Gecko carga la URL ahí. Sin pestaña → bloqueada.
                // ¿El usuario tocó o pulsó OK hace un momento? Si no, la página
                // abrió la ventana por su cuenta (publicidad).
                val gesto = android.os.SystemClock.uptimeMillis() -
                    com.antena.tv.cursor.Puntero.ultimaAccion < 1500
                val nueva = onNewWindow(gesto, url) ?: return null
                return GeckoResult.fromValue(nueva)
            }

            override fun onLoadError(
                session: GeckoSession,
                url: String?,
                error: WebRequestError,
            ): GeckoResult<String>? {
                if (url != null && url.startsWith("http")) {
                    Bitacora.add(
                        LogKind.ERROR,
                        "No se pudo cargar (${motivoGecko(error.code)}) · $url",
                    )
                }
                // null → Gecko muestra su propia página de error.
                return null
            }
        }

        s.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                this@WebTab.title = title ?: ""
                onTitle(this@WebTab.title)
            }

            override fun onCloseRequest(session: GeckoSession) {
                // window.close(): la pestaña se retira sola.
                onWindowClosed(this@WebTab)
            }

            // Sin este aviso Gecko no sabe que la app acepta la pantalla completa y el
            // vídeo se queda dentro del recuadro de la página.
            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                onPantallaCompleta(fullScreen)
            }

            // El motor murió: si fue Android quien lo mató por falta de memoria llega
            // aquí; se anota en la bitácora y la pantalla recrea la pestaña.
            override fun onKill(session: GeckoSession) {
                Bitacora.add(LogKind.ERROR, "El motor de la página fue cerrado por Android (falta de memoria) · $url")
                onProcesoCaido("cerrado por falta de memoria")
            }

            override fun onCrash(session: GeckoSession) {
                Bitacora.add(LogKind.ERROR, "El motor de la página falló (crash) · $url")
                onProcesoCaido("falló")
            }

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                // Descarga de archivo (ZIP, APK, .xpi suelto…): se entrega al
                // gestor del sistema, como hacía el DownloadListener de WebView.
                val mime = response.hdr("content-type")
                val disp = response.hdr("content-disposition")
                response.uri?.let { onDownload(it, mime, disp) }
            }

            override fun onContextMenu(
                session: GeckoSession,
                screenX: Int,
                screenY: Int,
                element: GeckoSession.ContentDelegate.ContextElement,
            ) {
                val img = if (element.type == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE) {
                    element.srcUri
                } else {
                    null
                }
                onContext(element.linkUri, img)
            }
        }

        s.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                this@WebTab.url = url
                onUrl(url)
                onProgress(1)
                if (url.startsWith("http")) Bitacora.add(LogKind.PAGINA, "Cargando $url")
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                onProgress(progress)
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                onProgress(if (success) 100 else 0)
                onLoaded(url, success)
            }
        }

        s.scrollDelegate = object : GeckoSession.ScrollDelegate {
            override fun onScrollChanged(session: GeckoSession, x: Int, y: Int) {
                scrollY = y
                onScroll(y)
            }
        }

        // Paso 5 · selección de texto. En campos editables (y de contraseña) se
        // REENVÍA a la barra nativa de Gecko: es la que sabe cortar/pegar con el
        // portapapeles y respeta el comportamiento de los formularios. Sobre
        // texto plano el menú propio añade lo que la barra nativa no da: «Buscar»
        // con el motor configurado y «Compartir», con el diseño del navegador.
        val act = actividad
        if (act != null) {
            s.selectionActionDelegate = object : BasicSelectionActionDelegate(act) {
                override fun onShowActionRequest(
                    session: GeckoSession,
                    selection: GeckoSession.SelectionActionDelegate.Selection,
                ) {
                    val editable =
                        selection.flags and GeckoSession.SelectionActionDelegate.FLAG_IS_EDITABLE != 0
                    if (editable || selection.text.isBlank()) {
                        super.onShowActionRequest(session, selection)
                        return
                    }
                    // El usuario cerró el menú de ESTA selección: no se vuelve a
                    // abrir por el mismo texto (sí por una selección nueva).
                    if (selection.text == textoSeleccionadoDescartado) return
                    seleccion = selection
                    ultimoTextoSeleccionado = selection.text
                    onSelection(selection.text, selection)
                }

                override fun onHideAction(session: GeckoSession, reason: Int) {
                    if (reason == GeckoSession.SelectionActionDelegate.HIDE_REASON_NO_SELECTION) {
                        seleccion = null
                        ultimoTextoSeleccionado = ""
                        textoSeleccionadoDescartado = ""
                    }
                    super.onHideAction(session, reason)
                }
            }
        }
    }

    fun load(newUrl: String) {
        url = newUrl
        session.loadUri(newUrl)
    }

    fun goBack() {
        session.goBack()
    }

    fun reload() {
        session.reload()
    }

    fun goForward() {
        session.goForward()
    }

    fun salirPantallaCompleta() {
        runCatching { session.exitFullScreen() }
    }

    /** La página tal como se ve, en PDF (lo genera el propio motor). */
    fun comoPdf(): GeckoResult<java.io.InputStream> = session.saveAsPdf()

    /** Cursor del mando contra un borde: desplaza la página (px de pantalla). */
    fun desplazar(dx: Float, dy: Float) {
        session.panZoomController.scrollBy(
            org.mozilla.geckoview.ScreenLength.fromPixels(dx.toDouble()),
            org.mozilla.geckoview.ScreenLength.fromPixels(dy.toDouble()),
            // Se llama cada ~16 ms: salto inmediato por fotograma = deslizamiento fluido.
            org.mozilla.geckoview.PanZoomController.SCROLL_BEHAVIOR_AUTO,
        )
    }

    /** Botón flotante «ir arriba»: Gecko no expone scrollTo, se simulan ↑. */
    fun scrollToTop() {
        if (scrollY <= 0) return
        repeat(80) {
            view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))
        }
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_UP))
    }

    fun destroy() {
        GeckoMotor.olvidarSesion(session)
        // setSession no admite null en GeckoView 141: se cierra la sesión y la
        // vista se descarta con la pestaña.
        runCatching { session.close() }
    }
}

/** Cabecera de la respuesta, sin importar mayúsculas. */
private fun WebResponse.hdr(name: String): String? =
    headers?.entries?.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

/** La retroalimentación de navegación de Gecko traducida a algo legible. */
private fun motivoGecko(code: Int): String = when {
    code and WebRequestError.ERROR_CATEGORY_NETWORK != 0 -> "sin conexión o servidor caído"
    code and WebRequestError.ERROR_CATEGORY_SECURITY != 0 -> "problema de seguridad (certificado)"
    code and WebRequestError.ERROR_CATEGORY_URI != 0 -> "dirección no válida"
    code and WebRequestError.ERROR_CATEGORY_CONTENT != 0 -> "el sitio no respondió"
    code and WebRequestError.ERROR_CATEGORY_PROXY != 0 -> "error de proxy"
    code and WebRequestError.ERROR_CATEGORY_SAFEBROWSING != 0 -> "sitio bloqueado por seguridad"
    else -> "error $code"
}

/**
 * Decide si la entrada del usuario es una dirección o una búsqueda:
 * «example.com» y «https://…» navegan; «hola mundo» busca con el motor.
 */
fun resolveInput(raw: String, engineTemplate: String): String {
    val t = raw.trim()
    if (t.isEmpty()) return ""

    val esquema = Regex("^[a-z][a-z0-9+.-]*://.*", RegexOption.IGNORE_CASE)
    val looksLikeUrl = when {
        t.contains(' ') -> false
        // Cualquier esquema con :// (http, https, moz-extension…).
        esquema.matches(t) -> true
        // Páginas internas del motor (about:config, about:memory…).
        t.startsWith("about:") -> true
        t.startsWith("http://") || t.startsWith("https://") -> true
        Regex("^[\\w.-]+\\.[a-z]{2,}(:\\d+)?([/?#].*)?$", RegexOption.IGNORE_CASE).matches(t) -> true
        Regex("^(localhost|\\d{1,3}(\\.\\d{1,3}){3})(:\\d+)?([/?#].*)?$").matches(t) -> true
        else -> false
    }

    return if (looksLikeUrl) {
        if (esquema.matches(t) || t.startsWith("about:")) t
        else if (t.startsWith("http://") || t.startsWith("https://")) t
        else "https://$t"
    } else {
        engineTemplate.replace("%s", URLEncoder.encode(t, "UTF-8"))
    }
}

/** Autoridad de una URL con esquema: `https://usuario@host:8080/x` → `host`. */
private val AUTORIDAD = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://([^/?#]*)")

/**
 * Host legible de una URL para las pestañas sin título.
 *
 * Se extrae con una expresión regular en vez de `android.net.Uri` para que la
 * lógica de la barra de direcciones (que ordena las sugerencias por dominio)
 * sea **pura** y se pueda probar en la JVM sin dispositivo. Los resultados son
 * los mismos: sin esquema (`example.com`, `about:blank`) se devuelve la cadena
 * tal cual, como hacía `Uri.parse(...).host ?: url`.
 */
fun hostOf(url: String): String? {
    if (url.isEmpty()) return null
    val autoridad = AUTORIDAD.find(url)?.groupValues?.get(1).orEmpty()
    val host = autoridad.substringAfterLast('@').substringBefore(':')
    return host.ifEmpty { url }
}
