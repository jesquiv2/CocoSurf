// M5 · motor de extensiones de Antena: GeckoRuntime único del proceso con su
// WebExtensionController. Todas las pestañas Gecko comparten este runtime y,
// por lo tanto, el mismo perfil y las mismas extensiones: instalar uBO aquí es
// instalarla para toda pestaña con motor Gecko.
package com.antena.tv.gecko

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.antena.tv.log.Bitacora
import com.antena.tv.log.LogKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/**
 * Id de la extensión interna del navegador (la que informa el scroll de la
 * página): es un ayudante de la app, no un complemento del usuario, así que
 * nunca ocupa un hueco propio en la barra.
 */
const val EXT_INTERNA = "navegador@antena.tv"

/**
 * Lo que ve la UI: sin tipos de Gecko fuera de este paquete.
 *
 * Paso 7 · además del nombre y el estado, se traen los datos que la extensión
 * publica de verdad y que el panel individual necesita
 * (`MetaData.getOptionsPageUrl()`, `getHomepageUrl()`, `getAmoListingUrl()`,
 * `getCreatorName()`, `getDescription()`); si alguno falta llega `null` y su
 * fila simplemente no existe.
 */
data class ExtInfo(
    val id: String,
    val nombre: String,
    val version: String?,
    val activa: Boolean,
    val descripcion: String? = null,
    val autor: String? = null,
    val opciones: String? = null,
    val web: String? = null,
    val ficha: String? = null,
)

/** Petición de la extensión de abrir SU interfaz propia (opciones/popup). */
data class PeticionExtension(val id: String, val nombre: String, val url: String?)

/**
 * Acción de barra de una extensión (icono + contador): es lo que uBO actualiza
 * con cada anuncio bloqueado (`browserAction.setBadgeText`). La ve la UI.
 */
data class AccionInfo(
    val extId: String,
    val nombre: String,
    /** Texto del contador (p. ej. «12»); vacío si la ext no informa. */
    val badge: String?,
)

object GeckoMotor {
    private const val TAG = "GeckoMotor"

    private var runtime: GeckoRuntime? = null

    private val _extensiones = MutableStateFlow<List<ExtInfo>>(emptyList())
    /** Extensiones instaladas, ya traducidas para la UI. */
    val extensiones: StateFlow<List<ExtInfo>> = _extensiones

    private val _aviso = MutableStateFlow<String?>(null)
    /** Mensaje de estado de la pantalla (instalando, listo, error). */
    val aviso: StateFlow<String?> = _aviso

    private val _scrollY = MutableStateFlow(-1)
    /** Último scroll informado por la extensión interna (-1 = sin información). */
    val scrollY: StateFlow<Int> = _scrollY

    private val _acciones = MutableStateFlow<List<AccionInfo>>(emptyList())
    /** Contadores/íconos de las extensiones con barra (uBO y compañía). */
    val acciones: StateFlow<List<AccionInfo>> = _acciones

    /**
     * Paso 7 · icono REAL de cada extensión (`MetaData.getIcon()` → `Image`),
     * ya decodificado a bitmap y listo para pintar. La UI que no encuentre aquí
     * su extensión usa la inicial estable de su nombre (Extensiones.kt).
     */
    private val _iconos = MutableStateFlow<Map<String, android.graphics.Bitmap>>(emptyMap())
    val iconos: StateFlow<Map<String, android.graphics.Bitmap>> = _iconos

    /** Iconos ya pedidos al motor (no se repite la petición en cada listado). */
    private val iconosPedidos = mutableSetOf<String>()

    /** Los iconos se piden a este tamaño y la UI los reduce a 18 dp. */
    private const val ICONO_PX = 96

    /**
     * Pulsación del icono de la barra de una extensión: le pide su interfaz
     * propia (popup/panel). Devuelve `false` cuando Gecko no puede atenderla
     * **ahora** —no hay acción declarada o la pestaña en pantalla no tiene sesión
     * (p. ej. el panel de inicio, que es Compose puro)—: en ese caso Gecko
     * resolvería `tabs.activeTab` como null y el clic revienta dentro del motor
     * («nativeTab is null») sin abrir nada, así que la UI ofrece en su lugar el
     * panel propio de la extensión.
     */
    fun clicAccion(extId: String): Boolean {
        val acc = accionesObj[extId] ?: return false
        // Reasegura la pestaña visible justo antes del clic: Gecko resuelve
        // `tabs.activeTab` al procesar «BrowserAction:Click» y tiene que dar con
        // la sesión de ahora (si da con null, wrapTab revienta y no hay panel).
        val sesion = sesionFijada ?: sesionActiva ?: return false
        marcarActiva(sesion)
        Log.i(TAG, "clic en la barra de $extId (badge=${acc.badgeText ?: "—"})")
        acc.click()
        return true
    }

    /** ¿Hay ahora mismo con qué abrir la interfaz propia de esa extensión? */
    fun puedeAbrirInterfaz(extId: String): Boolean =
        accionesObj[extId] != null && (sesionFijada ?: sesionActiva) != null

    /**
     * Último enlace/imagen señalado por el content script (focusin/keydown):
     * es lo que monta el menú contextual al mantener OK con el mando, porque
     * Gecko no expone evaluate() para preguntar a la página.
     */
    @Volatile
    var ultimoContexto: Pair<String?, String?> = null to null

    /** Objetos de barra por extensión (para clic y actualizaciones). */
    private var accionesObj = mapOf<String, WebExtension.Action>()

    /** Contadores por sesión: el badge de uBO es de la pestaña activa. */
    private var accionesPorSesion = mutableMapOf<GeckoSession?, MutableMap<String, AccionInfo>>()

    /** Sesión de la pestaña que está en pantalla (su contador ve la UI). */
    private var sesionActiva: GeckoSession? = null

    /**
     * Sesión que Gecko conoce como «activa» para las extensiones. Mientras el
     * panel (popup) de uBO ocupa su propia pestaña queda fijada a la pestaña
     * que lo pidió: uBO resuelve `tabs.getCurrent()` (activa) para el recuento
     * de «bloqueados en esta página», y si marcamos activa la pestaña del panel
     * el contador sale siempre 0.
     */
    private var sesionFijada: GeckoSession? = null

    /** Última sesión enviada a `setTabActive` (para no repetir la marca). */
    private var sesionMarcada: GeckoSession? = null

    /** Controlador de extensiones del runtime (p. ej. para marcar la pestaña activa). */
    private val controlador: WebExtensionController?
        get() = runtime?.webExtensionController

    /**
     * La extensión quiere abrir su panel (clic en el icono de su barra → popup
     * de uBO): la app crea una pestaña y devuelve su sesión, en la que Gecko
     * carga el popup. Si no hay callback, el clic no hace nada visible.
     */
    var alPopup: ((nombre: String) -> GeckoSession?)? = null

    /**
     * Paso 7 · la extensión pide abrir SU página de opciones
     * (`runtime.openOptionsPage()`): GeckoView no la abre por su cuenta (su
     * `openOptionsPage` interno no es público), así que la app carga
     * `MetaData.getOptionsPageUrl()` donde corresponda. Sin callback no se
     * pierde nada, pero tampoco se abre: por eso se registra el TabDelegate.
     */
    var alOpciones: ((PeticionExtension) -> Unit)? = null

    /**
     * Paso 7 · la extensión pide una pestaña nueva (`browser.tabs.create()`),
     * p. ej. el panel de control de uBO. Mismo contrato que alPopup: la app crea
     * la pestaña y devuelve su sesión (null = petición no atendida).
     */
    var alNuevaPestana: ((url: String, alFrente: Boolean) -> GeckoSession?)? = null

    /** La pestaña activa cambió: el chip 🛡 pasa a mostrar su contador. */
    fun activarSesion(session: GeckoSession?) {
        sesionActiva = session
        // Con el panel abierto, la pestaña que miran las extensiones es la de
        // origen: no se cambia la marca.
        if (sesionFijada == null) marcarActiva(session)
        publicarAcciones()
    }

    /** Sesión que las extensiones ven como «activa» (la de origen si hay panel). */
    fun sesionParaExtensiones(): GeckoSession? = sesionFijada ?: sesionActiva

    /**
     * El panel (popup) de una extensión abrió pestaña propia: fija la sesión
     * que las extensiones siguen viendo como activa (la pestaña de origen).
     * `null` desfija y devuelve la marca a la pestaña en pantalla.
     */
    fun fijarSesion(s: GeckoSession?) {
        val objetivo = s ?: sesionActiva
        sesionFijada = s
        // Ciclo completo y sin atajos: GeckoView marca como activa la sesión que
        // acaba de acoplar a su vista (el propio panel de la extensión), así que
        // hay que SOLTARLA y volver a marcar la pestaña del sitio. Con la lista
        // de pestañas activas en dos entradas, el panel pregunta por «la pestaña
        // activa» y recibe la suya: responde «no funciona en este sitio» y no
        // deja ni aplicar ni quitar la máscara.
        runCatching {
            // La sesión que GeckoView acaba de acoplar a su vista (el panel).
            sesionActiva?.takeIf { it !== objetivo }?.let {
                controlador?.setTabActive(it, false)
            }
            // Y la que quedó marcada antes, si es otra.
            sesionMarcada?.takeIf { it !== sesionActiva && it !== objetivo }?.let {
                controlador?.setTabActive(it, false)
            }
            objetivo?.let { controlador?.setTabActive(it, true) }
        }.onFailure { Log.w(TAG, "setTabActive (fijar) falló: ${it.message}") }
        sesionMarcada = objetivo
        publicarAcciones()
    }

    private fun marcarActiva(session: GeckoSession?) {
        if (sesionMarcada === session) return
        // Gecko no adivina cuál es la pestaña visible: sólo la app lo dice con
        // `WebExtension.setTabActive`. Sin eso `tabs.activeTab` sigue null y el
        // clic del chip 🛡 revienta dentro de GeckoView con «nativeTab is null»
        // (ext-android.js wrapTab), antes de llegar a onTogglePopup: ni contador
        // ni panel de uBO.
        runCatching {
            sesionMarcada?.let { controlador?.setTabActive(it, false) }
            session?.let { controlador?.setTabActive(it, true) }
            sesionMarcada = session
            Log.i(TAG, "pestaña marcada activa para extensiones")
        }.onFailure {
            Log.w(TAG, "setTabActive falló: ${it.message}")
        }
    }

    private fun publicarAcciones() {
        val base = accionesPorSesion[null].orEmpty()
        val activas = accionesPorSesion[sesionFijada ?: sesionActiva].orEmpty()
        _acciones.value = (base + activas).values.sortedBy { it.nombre.lowercase() }
    }

    /** Se registra al listar: Gecko avisa de cada cambio de barra (badge). */
    private val actionDelegate = object : WebExtension.ActionDelegate {
        override fun onBrowserAction(
            extension: WebExtension,
            session: GeckoSession?,
            action: WebExtension.Action,
        ) = guardarAccion(extension, session, action)

        override fun onPageAction(
            extension: WebExtension,
            session: GeckoSession?,
            action: WebExtension.Action,
        ) = guardarAccion(extension, session, action)

        override fun onTogglePopup(
            extension: WebExtension,
            action: WebExtension.Action,
        ): GeckoResult<GeckoSession>? = popup(extension, action)

        override fun onOpenPopup(
            extension: WebExtension,
            action: WebExtension.Action,
        ): GeckoResult<GeckoSession>? = popup(extension, action)
    }

    /**
     * Paso 7 · lo que la propia extensión pide: sus opciones y pestañas nuevas.
     * Se registra por extensión en `refrescar()`.
     */
    private val tabDelegate = object : WebExtension.TabDelegate {
        override fun onOpenOptionsPage(extension: WebExtension) {
            val url = extension.metaData.optionsPageUrl
            Log.i(TAG, "${extension.id} pide sus opciones: ${url ?: "(sin URL)"}")
            alOpciones?.invoke(
                PeticionExtension(
                    id = extension.id,
                    nombre = extension.metaData.name ?: extension.id,
                    url = url,
                ),
            )
        }

        override fun onNewTab(
            extension: WebExtension,
            details: WebExtension.CreateTabDetails,
        ): GeckoResult<GeckoSession>? {
            val url = details.url ?: return null
            val cb = alNuevaPestana ?: return null
            val sesion = cb(url, details.active == true) ?: return null
            Log.i(TAG, "${extension.id} abre pestaña: $url")
            return GeckoResult.fromValue(sesion)
        }
    }

    private fun popup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? {
        val cb = alPopup ?: return null
        val nombre = extension.metaData.name ?: extension.id
        val sesion = cb(nombre) ?: return null
        Log.i(TAG, "popup de $nombre abierto en pestaña")
        return GeckoResult.fromValue(sesion)
    }

    private fun guardarAccion(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
        if (!extension.metaData.enabled) {
            accionesObj -= extension.id
            accionesPorSesion.values.forEach { it -= extension.id }
            publicarAcciones()
            return
        }
        accionesObj += extension.id to action
        val info = AccionInfo(
            extId = extension.id,
            nombre = extension.metaData.name ?: extension.id,
            badge = action.badgeText?.takeIf { it.isNotBlank() },
        )
        accionesPorSesion.getOrPut(session) { mutableMapOf() }[extension.id] = info
        publicarAcciones()
    }

    /**
     * Última transición **ya anunciada** (id de extensión + estado). Un mismo
     * suceso llega por dos caminos —la respuesta de la operación que pidió la UI
     * y el aviso del AddonManager de Gecko— y debe contarse una sola vez en el
     * aviso de la pantalla y en la Bitácora.
     */
    private var anunciado: Pair<String, String>? = null

    /** .xpi que pidió instalar la app (contexto para los avisos de error). */
    @Volatile
    private var urlEnCurso: String? = null

    /**
     * Hay una instalación pedida desde la app en marcha. Mientras dure, el aviso
     * lo da la propia operación (que conoce la URL y el código de error) y el
     * delegate de Gecko sólo refresca la lista, para no duplicar el mensaje.
     */
    @Volatile
    private var instalacionPropia = false

    /**
     * Publica una transición de extensión una sola vez: fija el aviso de la
     * pantalla y, si procede, la entrada de Bitácora. Devuelve `false` cuando ese
     * mismo estado ya se anunció (el duplicado que llegó por la otra vía).
     */
    private fun anunciar(
        id: String?,
        estado: String,
        aviso: String,
        bitacora: String? = null,
        kind: LogKind = LogKind.SISTEMA,
    ): Boolean {
        val clave = (id ?: "?") to estado
        if (anunciado == clave) return false
        anunciado = clave
        _aviso.value = aviso
        if (bitacora != null) Bitacora.add(kind, bitacora)
        return true
    }

    /**
     * Avisos del propio Gecko sobre el ciclo de vida de las extensiones
     * (`WebExtensionController.AddonManagerDelegate`, presente en la versión de
     * GeckoView del proyecto: verificado en `geckoview-141.0.20250806102122`).
     *
     * Es lo que mantiene la lista sincronizada de verdad: una instalación hecha
     * desde la web de AMO («Agregar a Firefox»), una desinstalación o un cambio
     * de estado llegan aquí aunque la app no los haya pedido, sin sondear nada ni
     * dejar temporizadores vivos.
     *
     * La lista de INSTALADAS se sigue leyendo de `list()`, así que una
     * instalación fallida nunca aparece como instalada.
     */
    private val addonManagerDelegate = object : WebExtensionController.AddonManagerDelegate {
        override fun onInstalled(extension: WebExtension) {
            val nombre = extension.metaData.name ?: extension.id
            val version = extension.metaData.version
            Log.i(TAG, "gecko: OnInstalled ${extension.id} v$version")
            urlEnCurso = null
            instalacionPropia = false
            anunciar(
                extension.id,
                "instalada",
                "INSTALADA: $nombre v$version",
                "Extensión instalada: $nombre v$version",
            )
            refrescar()
        }

        override fun onInstallationFailed(extension: WebExtension?, error: WebExtension.InstallException) {
            val detalle = describirFallo(error)
            val id = extension?.id
            Log.e(TAG, "gecko: OnInstallationFailed ${id ?: "?"} · $detalle · ${urlEnCurso ?: "—"}", error)
            // La instalación pedida desde la app ya avisa ella misma (con la URL
            // y el código de error): aquí sólo se refresca la lista.
            if (!instalacionPropia) {
                anunciar(
                    id,
                    "fallo",
                    "ERROR al instalar: $detalle",
                    "Instalación fallida: $detalle",
                    LogKind.ERROR,
                )
            }
            urlEnCurso = null
            instalacionPropia = false
            refrescar()
        }

        override fun onUninstalled(extension: WebExtension) {
            val nombre = extension.metaData.name ?: extension.id
            Log.i(TAG, "gecko: OnUninstalled ${extension.id}")
            anunciar(
                extension.id,
                "desinstalada",
                "DESINSTALADA: $nombre",
                "Extensión desinstalada: $nombre",
            )
            refrescar()
        }

        override fun onEnabled(extension: WebExtension) {
            Log.i(TAG, "gecko: OnEnabled ${extension.id}")
            anunciar(
                extension.id,
                "activada",
                "${extension.metaData.name ?: extension.id}: ACTIVADA",
            )
            refrescar()
        }

        override fun onDisabled(extension: WebExtension) {
            Log.i(TAG, "gecko: OnDisabled ${extension.id}")
            anunciar(
                extension.id,
                "desactivada",
                "${extension.metaData.name ?: extension.id}: DESACTIVADA",
            )
            refrescar()
        }
    }

    /** Objetos Gecko por id, para las operaciones de la UI. */
    private var objetos = mapOf<String, WebExtension>()
    private var helper: WebExtension? = null
    private val sesiones = mutableListOf<GeckoSession>()

    /** Runtime compartido (arrancándolo la primera vez que se pide). */
    @Synchronized
    fun runtime(context: Context): GeckoRuntime {
        runtime?.let { return it }
        // Memoria: sin «fission» todas las webs comparten un solo proceso de contenido
        // (con él, cada sitio y cada iframe abre el suyo y un TV de 2 GB no aguanta),
        // y el motor recorta cachés por sí solo cuando Android avisa de poca memoria.
        val ajustes = org.mozilla.geckoview.GeckoRuntimeSettings.Builder()
            .fissionEnabled(false)
            .lowMemoryDetection(true)
            .build()
        val rt = GeckoRuntime.create(context.applicationContext, ajustes)
        // Sin PromptDelegate no hay «listener» para el evento de instalación y
        // TODO install() falla con InstallException (bug encontrado en el spike):
        // el clic del usuario en «Instalar» ya es el consentimiento.
        rt.webExtensionController.setPromptDelegate(promptDelegate)
        // Avisos del ciclo de vida de las extensiones (instalada, desinstalada,
        // fallo, activada/desactivada): mantienen la lista sincronizada sin
        // sondear y llegan aunque la instalación la haya pedido una página.
        rt.webExtensionController.setAddonManagerDelegate(addonManagerDelegate)
        // Extensión interna (ensureBuiltIn): informa el scroll de la página para
        // plegar la barra superior con el mando, igual que en las pestañas WebView.
        rt.webExtensionController
            .ensureBuiltIn("resource://android/assets/gecko-ext/", EXT_INTERNA)
            .accept(
                { ext ->
                    helper = ext
                    ext?.setMessageDelegate(delegate, "antena")
                    sesiones.toList().forEach { s ->
                        ext?.let { s.webExtensionController.setMessageDelegate(it, delegate, "antena") }
                    }
                    refrescar()
                },
                { e -> Log.w(TAG, "extensión interna ERROR: ${e?.message}") },
            )
        runtime = rt
        return rt
    }

    /** Arranca el motor si hacía falta y refresca la lista. Llamar en hilo UI. */
    @Synchronized
    fun arrancar(context: Context) {
        runtime(context)
        refrescar()
    }

    /** Sesión Gecko recién creada: engancha el delegate de la extensión interna. */
    fun registrarSesion(session: GeckoSession) {
        sesiones += session
        helper?.let { session.webExtensionController.setMessageDelegate(it, delegate, "antena") }
        enganchaAcciones(session)
    }

    /**
     * Conecta la barra de extensiones con una sesión concreta.
     *
     * Gecko entrega dos clases de aviso por caminos distintos: los globales por
     * el EventDispatcher del runtime (ahí ya hay delegate) y los de la pestaña
     * por el de *su* sesión. Si en esa sesión no se registra ningún delegate
     * de barra, `actionDelegateFor()` devuelve null y Gecko descarta el aviso
     * sin avisar: el chip nunca vería el contador de uBO en la página.
     */
    private fun enganchaAcciones(session: GeckoSession) {
        objetos.values.forEach { ext ->
            runCatching { session.webExtensionController.setActionDelegate(ext, actionDelegate) }
        }
    }

    /**
     * La pestaña de esta sesión se cierra: se suelta **antes** de `close()` para
     * que Gecko deje de verla como la pestaña activa mientras su ventana aún
     * existe. Si no, `tabs.activeTab` queda apuntando a una ventana muerta y la
     * siguiente API de extensión (abrir el popup de un chip, por ejemplo)
     * responde «ExtensionError: Invalid window ID» y el clic no hace nada.
     */
    fun olvidarSesion(session: GeckoSession) {
        sesiones.remove(session)
        accionesPorSesion.remove(session)
        if (sesionActiva === session) sesionActiva = null
        // Mientras el panel (popup) de una extensión ocupa su propia pestaña, la
        // marca activa queda fijada a la pestaña de origen: si esa pestaña se
        // cierra, el panel se lleva la marca consigo (nadie vuelve a marcarla) y
        // las extensiones se quedarían sin pestaña activa para siempre.
        if (sesionFijada === session) sesionFijada = null
        if (sesionMarcada === session) {
            runCatching { controlador?.setTabActive(session, false) }
            sesionMarcada = null
            // La marca vuelve a la pestaña que sigue en pantalla.
            sesionActiva?.let { marcarActiva(it) }
        }
        publicarAcciones()
    }

    fun refrescar() {
        val rt = runtime ?: return
        rt.webExtensionController.list().accept(
            { exts ->
                val orden = exts.orEmpty()
                    .filter { it.id != "toolkit@mozilla.org" }
                    .sortedBy { (it.metaData.name ?: it.id).lowercase() }
                objetos = orden.associateBy { it.id }
                // Barra de extensiones: al registrarse Gecko entrega el estado
                // actual (icono y contador) de cada una.
                orden.forEach { ext ->
                    runCatching { ext.setActionDelegate(actionDelegate) }
                    // Paso 7 · la extensión puede pedir sus opciones o una pestaña
                    // nueva desde su propia interfaz (uBO: «Abrir panel de control»).
                    runCatching { ext.setTabDelegate(tabDelegate) }
                    cargarIcono(ext)
                }
                // Iconos de extensiones que ya no están: no se acumulan.
                val vigentes = orden.map { it.id }.toSet()
                iconosPedidos.retainAll(vigentes)
                if (_iconos.value.keys.any { it !in vigentes }) {
                    _iconos.value = _iconos.value.filterKeys { it in vigentes }
                }
                // Pestañas abiertas antes de que la extensión se enumerara
                // (o antes de instalar uBO): engancha también sus contadores.
                sesiones.toList().forEach { enganchaAcciones(it) }
                _extensiones.value = orden.map {
                    ExtInfo(
                        id = it.id,
                        nombre = it.metaData.name ?: it.id,
                        version = it.metaData.version,
                        activa = it.metaData.enabled,
                        descripcion = it.metaData.description,
                        autor = it.metaData.creatorName,
                        opciones = it.metaData.optionsPageUrl,
                        web = it.metaData.homepageUrl,
                        ficha = it.metaData.amoListingUrl,
                    )
                }
            },
            { e ->
                Log.w(TAG, "list ERROR: ${e?.message}")
                _aviso.value = "No se pudo leer la lista: ${e?.message}"
            },
        )
    }

    /**
     * Paso 7 · pide al motor el icono real de la extensión y lo publica cuando
     * está decodificado. Si el motor no da imagen (`MetaData.getIcon()` nulo) no
     * se marca como pedido: si más adelante la ofrece, se recoge en el siguiente
     * listado. Un fallo de decodificación se registra y deja el icono alternativo
     * de la UI (nunca un hueco en blanco).
     */
    private fun cargarIcono(ext: WebExtension) {
        val id = ext.id
        if (id in iconosPedidos) return
        val imagen = ext.metaData.icon ?: return
        iconosPedidos += id
        runCatching {
            imagen.getBitmap(ICONO_PX).accept(
                { bmp -> if (bmp != null) _iconos.value = _iconos.value + (id to bmp) },
                { e -> Log.w(TAG, "icono de $id no disponible: ${e?.message}") },
            )
        }.onFailure { Log.w(TAG, "no se pudo pedir el icono de $id: ${it.message}") }
    }

    /** ENTER sobre una extensión: la activa si está desactivada y viceversa. */
    fun alternar(id: String) {
        val ext = objetos[id] ?: return refrescar()
        val ctrl = runtime?.webExtensionController ?: return
        val activando = !ext.metaData.enabled
        val destino = if (activando) "ACTIVADA" else "DESACTIVADA"
        val futuro = if (activando) {
            ctrl.enable(ext, WebExtensionController.EnableSource.USER)
        } else {
            ctrl.disable(ext, WebExtensionController.EnableSource.USER)
        }
        futuro.accept(
            {
                Log.i(TAG, "toggle $id → $destino")
                anunciar(id, destino.lowercase(), "${ext.metaData.name ?: id}: $destino")
                refrescar()
            },
            { e ->
                Log.e(TAG, "toggle ERROR: ${e?.message}", e)
                _aviso.value = "ERROR al cambiar el estado: ${e?.message}"
                refrescar()
            },
        )
    }

    fun desinstalar(id: String) {
        val ext = objetos[id] ?: return refrescar()
        runtime?.webExtensionController?.uninstall(ext)?.accept(
            {
                Log.i(TAG, "DESINSTALADA $id")
                anunciar(
                    id,
                    "desinstalada",
                    "DESINSTALADA: ${ext.metaData.name ?: id}",
                    "Extensión desinstalada: ${ext.metaData.name ?: id}",
                )
                refrescar()
            },
            { e ->
                _aviso.value = "ERROR al desinstalar: ${e?.message}"
                refrescar()
            },
        )
    }

    /**
     * Descarga el .xpi de la URL y lo monta (mismo camino que la tienda).
     *
     * Antes de pedirle la descarga a Gecko se comprueba el enlace con un HEAD:
     * un slug equivocado de AMO responde 404 y Gecko sólo devuelve
     * `InstallException` con código -1 y **sin mensaje**, que es imposible de
     * distinguir de «sin conexión». Con la comprobación el aviso dice qué pasó
     * (y con qué enlace).
     */
    fun instalar(url: String) {
        val rt = runtime ?: return
        val destino = url.trim()
        if (destino.isEmpty()) {
            _aviso.value = "ERROR: falta la dirección del .xpi"
            return
        }
        _aviso.value = "Comprobando el enlace…\n$destino"
        urlEnCurso = destino
        instalacionPropia = true
        Thread {
            val estado = estadoHttp(destino)
            Handler(Looper.getMainLooper()).post { instalarConGecko(rt, destino, estado) }
        }.apply {
            name = "antena-comprobar-xpi"
            isDaemon = true
        }.start()
    }

    private fun instalarConGecko(rt: GeckoRuntime, url: String, estado: Int) {
        // 404/410 = el enlace ya no existe: instalar aquí sólo produciría el
        // opaco «InstallException null». El resto de códigos se anotan y se
        // intenta igual (muchos servidores no contestan bien a un HEAD).
        if (estado == 404 || estado == 410) {
            val texto = "ERROR: el enlace responde HTTP $estado (el complemento ya no " +
                "está en esa dirección)\n$url"
            Log.e(TAG, "install abortado: HTTP $estado en $url")
            Bitacora.add(LogKind.ERROR, "Instalación cancelada · HTTP $estado · $url")
            _aviso.value = texto
            return
        }
        if (estado >= 400) Log.w(TAG, "el enlace responde HTTP $estado; se intenta igual")
        _aviso.value = "Descargando e instalando…\n$url"
        rt.webExtensionController.install(url).accept(
            { ext ->
                Log.i(TAG, "install OK: ${ext?.id} v${ext?.metaData?.version}")
                val nombre = ext?.metaData?.name ?: ext?.id
                anunciar(
                    ext?.id,
                    "instalada",
                    "INSTALADA: $nombre v${ext?.metaData?.version}",
                    "Extensión instalada: $nombre v${ext?.metaData?.version}",
                )
                refrescar()
            },
            { e -> errorInstalar(url, e) },
        )
    }

    /**
     * Fallo de instalación con la causa disponible: Gecko no rellena el mensaje
     * (de ahí el «InstallException null»), pero sí el código de error.
     * La lista de extensiones NO se toca: sigue siendo la que informa Gecko, así
     * que una instalación fallida no aparece como instalada.
     */
    private fun errorInstalar(url: String, e: Throwable?) {
        val detalle = describirFallo(e)
        Log.e(TAG, "install ERROR: $detalle · $url", e)
        val id = (e as? WebExtension.InstallException)?.extensionId?.takeIf { it.isNotBlank() }
        anunciar(
            id ?: url,
            "fallo",
            "ERROR al instalar: $detalle\n$url",
            "Instalación fallida: $detalle · $url",
            LogKind.ERROR,
        )
        urlEnCurso = null
        instalacionPropia = false
    }

    /** Traduce la excepción de Gecko a una razón legible en es-419. */
    private fun describirFallo(e: Throwable?): String {
        if (e == null) return "la tienda no devolvió detalle (¿sin conexión?)"
        val causa = e.cause?.let { c ->
            " · causa: ${c.javaClass.simpleName}${c.message?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}"
        }.orEmpty()
        if (e is WebExtension.InstallException) {
            val motivo = when (e.code) {
                WebExtension.InstallException.ErrorCodes.ERROR_NETWORK_FAILURE ->
                    "no se pudo descargar el .xpi (enlace roto o sin conexión)"
                WebExtension.InstallException.ErrorCodes.ERROR_INCORRECT_HASH ->
                    "el archivo descargado no coincide con la firma (descarga corrupta)"
                WebExtension.InstallException.ErrorCodes.ERROR_CORRUPT_FILE ->
                    "el archivo descargado está dañado"
                WebExtension.InstallException.ErrorCodes.ERROR_FILE_ACCESS ->
                    "no se pudo leer el archivo descargado"
                WebExtension.InstallException.ErrorCodes.ERROR_SIGNEDSTATE_REQUIRED ->
                    "AMO exige una versión firmada; el .xpi no está firmado"
                WebExtension.InstallException.ErrorCodes.ERROR_UNEXPECTED_ADDON_TYPE ->
                    "el archivo no es una extensión válida"
                WebExtension.InstallException.ErrorCodes.ERROR_UNEXPECTED_ADDON_VERSION ->
                    "versión no admitida por esta instalación de Gecko"
                WebExtension.InstallException.ErrorCodes.ERROR_INCORRECT_ID ->
                    "el identificador de la extensión no coincide con el esperado"
                WebExtension.InstallException.ErrorCodes.ERROR_INVALID_DOMAIN ->
                    "el dominio del .xpi no está autorizado"
                WebExtension.InstallException.ErrorCodes.ERROR_BLOCKLISTED ->
                    "el complemento está en la lista de bloqueo de Mozilla"
                WebExtension.InstallException.ErrorCodes.ERROR_INCOMPATIBLE ->
                    "el complemento no es compatible con este motor"
                WebExtension.InstallException.ErrorCodes.ERROR_UNSUPPORTED_ADDON_TYPE ->
                    "tipo de complemento no soportado en Android"
                WebExtension.InstallException.ErrorCodes.ERROR_ADMIN_INSTALL_ONLY ->
                    "sólo se puede instalar por política administrativa"
                WebExtension.InstallException.ErrorCodes.ERROR_SOFT_BLOCKED ->
                    "Mozilla recomienda no instalar este complemento"
                WebExtension.InstallException.ErrorCodes.ERROR_USER_CANCELED ->
                    "instalación cancelada"
                WebExtension.InstallException.ErrorCodes.ERROR_POSTPONED ->
                    "instalación aplazada"
                else -> "fallo no clasificado"
            }
            val id = e.extensionId?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
            return "$motivo (código ${e.code})$id$causa"
        }
        return "${e.javaClass.simpleName}${e.message?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: " (sin mensaje)"}$causa"
    }

    /** Código HTTP del enlace .xpi, o -1 si no se pudo comprobar. */
    private fun estadoHttp(url: String): Int = runCatching {
        val c = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "HEAD"
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Antena/1.0")
        }
        try {
            c.responseCode
        } finally {
            c.disconnect()
        }
    }.getOrDefault(-1)

    /** Concede los permisos que pida la extensión al instalarse/actualizarse. */
    private val promptDelegate = object : WebExtensionController.PromptDelegate {
        override fun onInstallPromptRequest(
            extension: WebExtension,
            permissions: Array<String>,
            origins: Array<String>,
        ): GeckoResult<WebExtension.PermissionPromptResponse> {
            Log.i(TAG, "prompt instalar ${extension.id}: permisos=${permissions.size} orígenes=${origins.size}")
            return GeckoResult.fromValue(WebExtension.PermissionPromptResponse(true, true, true))
        }

        override fun onInstallPromptRequest(
            extension: WebExtension,
            permissions: Array<String>,
            origins: Array<String>,
            optional: Array<String>,
        ): GeckoResult<WebExtension.PermissionPromptResponse> {
            Log.i(TAG, "prompt instalar ${extension.id} (con opcionales)")
            return GeckoResult.fromValue(WebExtension.PermissionPromptResponse(true, true, true))
        }

        override fun onUpdatePrompt(
            old: WebExtension,
            new: WebExtension,
            permissions: Array<String>,
            origins: Array<String>,
        ): GeckoResult<AllowOrDeny> = GeckoResult.allow()

        override fun onOptionalPrompt(
            extension: WebExtension,
            permissions: Array<String>,
            origins: Array<String>,
        ): GeckoResult<AllowOrDeny> = GeckoResult.allow()
    }

    /** Recibe los informes de la extensión interna (posición de scroll). */
    private val delegate = object : WebExtension.MessageDelegate {
        override fun onMessage(
            nativeApp: String,
            message: Any,
            sender: WebExtension.MessageSender,
        ): GeckoResult<Any>? {
            runCatching {
                val obj = org.json.JSONObject(message.toString())
                when {
                    obj.has("y") -> _scrollY.value = obj.optInt("y", -1)
                    // Cachear el elemento señalado: «foco» lo refresca al
                    // navegar con el D-pad y «contexto» al mantener OK; el
                    // menú lo monta la app con este par (link, img).
                    obj.optString("tag") == "contexto" || obj.optString("tag") == "foco" -> {
                        ultimoContexto =
                            obj.optString("link").ifBlank { null } to obj.optString("img").ifBlank { null }
                    }
                }
            }
            return null
        }
    }
}
