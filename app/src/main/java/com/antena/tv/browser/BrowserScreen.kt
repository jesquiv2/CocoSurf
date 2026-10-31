package com.antena.tv.browser

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.antena.tv.db.AntenaDatabase
import com.antena.tv.db.DownloadEntry
import com.antena.tv.db.Favorite
import com.antena.tv.db.Folder
import com.antena.tv.db.HistoryEntry
import com.antena.tv.db.SearchEngine
import com.antena.tv.db.Setting
import com.antena.tv.db.registrar
import com.antena.tv.gecko.GeckoMotor
import com.antena.tv.gecko.Memoria
import com.antena.tv.log.Bitacora
import com.antena.tv.log.LogEntry
import com.antena.tv.log.LogKind
import com.antena.tv.MainActivity
import org.mozilla.geckoview.StorageController
import com.antena.tv.cursor.CursorScreen
import com.antena.tv.cursor.Puntero
import com.antena.tv.cursor.PunteroOverlay
import com.antena.tv.probe.Probe
import com.antena.tv.probe.ProbeScreen
import com.antena.tv.ui.Ambar
import com.antena.tv.ui.AntenaType
import com.antena.tv.ui.Azul
import com.antena.tv.ui.Casco
import com.antena.tv.ui.Escudo
import com.antena.tv.ui.Fosforo
import com.antena.tv.ui.FosforoTenue
import com.antena.tv.ui.Noche
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PageTab(
    val id: Int,
    val title: String,
    val url: String,
    val creadaAt: Long = System.currentTimeMillis(),
)

/** Fase 9 · pestaña cerrada, para restaurarla desde «Buscar pestañas». */
data class TabCerrada(val id: Int, val title: String, val url: String, val cerradaAt: Long)

/** Qué overlay mutante está encima del navegador. */
/* Extensiones que la app gestiona fuera del navegador (Fase 4). */
private val MEDIA_EXTS = listOf(
    ".mp4", ".webm", ".mkv", ".mov", ".avi", ".mp3", ".ogg", ".m4a", ".wav", ".flac", ".aac",
)
private val FILE_EXTS = listOf(
    ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".odt", ".ods",
    ".zip", ".rar", ".7z", ".apk", ".csv", ".epub", ".iso", ".exe",
)

/** Fase 6 · el agente de usuario se anota en la bitácora una sola vez por proceso. */
private var agenteAnotado = false

/**
 * Paso 2 · vistas internas que viven como **pestañas** normales (no overlays):
 * su URL las identifica igual que `cocosurf://inicio` identifica a la página de
 * inicio, y el área de contenido las dibuja con Compose en vez de GeckoView.
 * Así se alternan, se conservan y se cierran como cualquier otra pestaña.
 */
private const val INTERNA_INICIO = "cocosurf://inicio"
private const val INTERNA_HISTORIAL = "cocosurf://historial"
private const val INTERNA_DESCARGAS = "cocosurf://descargas"
private const val INTERNA_CONFIG = "cocosurf://config"
private const val INTERNA_ACERCA = "cocosurf://acerca"
private const val INTERNA_CLAVES = "cocosurf://claves"
private const val INTERNA_ARCHIVOS = "cocosurf://archivos"

/** Título de una vista interna; `null` cuando la URL es de una página web. */
private fun tituloInterna(url: String): String? = when (url) {
    INTERNA_INICIO -> "Inicio"
    INTERNA_HISTORIAL -> "Historial"
    INTERNA_DESCARGAS -> "Descargas"
    INTERNA_CONFIG -> "Configuración"
    INTERNA_ACERCA -> "Acerca de"
    INTERNA_CLAVES -> "Contraseñas"
    INTERNA_ARCHIVOS -> "Archivos"
    else -> null
}

private sealed interface Overlay {
    data object Menu : Overlay
    /** Panel rápido (Historial / Descargas) flotando bajo la barra. */
    data class Panel(val tipo: PanelTipo) : Overlay
    /** «Borrar datos de navegación». */
    data object BorrarDatos : Overlay
    data object Favorites : Overlay
    data object System : Overlay
    data object Diagnostics : Overlay
    /** Fase 6 · bitácora: lo que pasó, legible sin cable adb. */
    data object Bitacora : Overlay
    /** Fase 9 · panel «Buscar pestañas». */
    data object Buscar : Overlay
    /** M5 · administrador de extensiones del motor Gecko. */
    data object Complementos : Overlay
    /** Paso 7 · panel individual de UNA extensión (icono, estado y controles). */
    data class Extension(val id: String) : Overlay
    data class Pdf(val entry: com.antena.tv.db.DownloadEntry) : Overlay
    data class Media(val url: String) : Overlay
    data class Star(val existing: Favorite?) : Overlay
    /** Fase 5 · menú contextual sobre un enlace o una imagen. */
    data class ContextMenu(val target: ContextTarget) : Overlay
}

@Composable
fun BrowserScreen() {
    val context = LocalContext.current
    val composeView = LocalView.current
    val scope = rememberCoroutineScope()
    val db = remember { AntenaDatabase.get(context) }
    val keyboard = LocalSoftwareKeyboardController.current
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Throwable) {
            null
        }
    }

    var tabs by remember {
        mutableStateOf(listOf(PageTab(0, "Inicio", "cocosurf://inicio")))
    }
    var activeId by remember { mutableStateOf(0) }
    var nextId by remember { mutableStateOf(1) }
    // Pestaña que aloja el panel (popup) de una extensión: mientras esté en
    // pantalla, las extensiones siguen mirando la pestaña que lo abrió.
    var popupId by remember { mutableStateOf(-1) }
    // …y la pestaña que lo abrió, para devolverle la marca activa cada vez que
    // se vuelve al panel (si no, el propio panel pasa a ser la pestaña activa
    // y él mismo responde «no funciona en este sitio»).
    var popupOrigen by remember { mutableStateOf(-1) }
    // Fase 9 · pestañas cerradas, para restaurarlas.
    var cerradas by remember { mutableStateOf<List<TabCerrada>>(emptyList()) }
    var engines by remember { mutableStateOf(AntenaDatabase.DEFAULT_ENGINES) }
    var engineId by remember { mutableStateOf(AntenaDatabase.DEFAULT_SEARCH_ENGINE) }
    var homepage by remember { mutableStateOf("cocosurf://inicio") }
    var addressValue by remember { mutableStateOf(TextFieldValue("")) }
    var editing by remember { mutableStateOf(false) }
    var wasEditing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    // Panel rápido fijado como columna a la derecha (null = ninguno).
    var panelFijo by remember { mutableStateOf<PanelTipo?>(null) }

    // Paso 2 · estado propio de cada pestaña interna (filtro del historial,
    // posición de scroll…) para que se conserve al cambiar de pestaña.
    val tabStateHolder = rememberSaveableStateHolder()
    // Última página web visitada: «Usar esta página» en Configuración. Antes la
    // opción leía la pestaña activa (el overlay estaba sobre ella); como pestaña
    // propia ya no puede ser ella misma, así que se recuerda la última web.
    var ultimaWeb by remember { mutableStateOf("") }

    // Contenido de datos para paneles.
    var favorites by remember { mutableStateOf<List<Favorite>>(emptyList()) }
    var folders by remember { mutableStateOf<List<Folder>>(emptyList()) }
    var folderVersion by remember { mutableIntStateOf(0) }
    var history by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }
    var downloads by remember { mutableStateOf<List<DownloadEntry>>(emptyList()) }
    var historyFilter by remember { mutableStateOf(HistoryFilter.HOUR) }
    var favFolderSel by remember { mutableStateOf<String?>(null) }

    // Fase 6 · bitácora: se lee al abrir el panel desde el menú.
    var logEntries by remember { mutableStateOf<List<LogEntry>>(emptyList()) }

    // Coco Surf · al ver una página la tira de pestañas y la barra de direcciones
    // se esconden hacia arriba; «Atrás» las vuelve a bajar. `chromeOpen` es lo que
    // el usuario pidió; lo que se ve (`chromeVisible`) también cuenta las vistas
    // internas, que nunca esconden la barra.
    var chromeOpen by remember { mutableStateOf(true) }
    // Cursor del mando (tecla Menú lo enciende/apaga).
    var cursorEnabled by remember { mutableStateOf(true) }
    // Vídeo a pantalla completa (YouTube, anime…): sin barras ni paneles.
    var pantallaCompleta by remember { mutableStateOf(false) }
    // Se incrementa para recrear la vista de la pestaña activa (motor caído).
    var recarga by remember { mutableIntStateOf(0) }
    // Pestañas pausadas por memoria extrema: muestran un aviso con «Reintentar».
    var pausadas by remember { mutableStateOf(setOf<Int>()) }
    // Recordar las pestañas al cerrar la app y traerlas al abrirla.
    var restaurarPestanas by remember { mutableStateOf(true) }
    var pestanasCargadas by remember { mutableStateOf(false) }
    // Reproductor de video/audio a pantalla completa y carpeta con la que se abre «Archivos».
    var reproductorArchivo by remember { mutableStateOf<java.io.File?>(null) }
    var carpetaArchivos by remember { mutableStateOf<java.io.File?>(null) }
    var precalentarMotor by remember { mutableStateOf(true) }
    // Contraseñas: lo que Gecko pregunta (guardar) u ofrece (rellenar) en la página activa.
    var claveGuardar by remember { mutableStateOf<PeticionGuardar?>(null) }
    var claveElegir by remember { mutableStateOf<PeticionElegir?>(null) }
    // Últimas lecturas de recursos (gráficos del monitor).
    val muestras = remember { androidx.compose.runtime.mutableStateListOf<com.antena.tv.gecko.Recursos.Muestra>() }
    // ¿La página está desplazada? (para el botón «ir arriba» y el ↑ al tope).
    var scrolled by remember { mutableStateOf(false) }

    // Fase 3 · sugerencias en vivo bajo la barra de direcciones: direcciones y
    // búsquedas del historial real (nunca entradas inventadas).
    var suggestions by remember { mutableStateOf<List<Sugerencia>>(emptyList()) }
    var sugsOpen by remember { mutableStateOf(true) }
    var fieldHasFocus by remember { mutableStateOf(false) }
    val sugFr = remember { FocusRequester() }
    val barFr = remember { FocusRequester() }
    val fieldFr = remember { FocusRequester() }
    val menuFr = remember { FocusRequester() }

    // Fase 7 · barra de herramientas configurable.
    var tbFav by remember { mutableStateOf(true) }
    var tbHist by remember { mutableStateOf(true) }
    var tbDown by remember { mutableStateOf(true) }
    var tbFloat by remember { mutableStateOf(true) }
    var tbExt by remember { mutableStateOf(true) }
    var velocidadId by remember { mutableStateOf("normal") }
    var modoPaginaId by remember { mutableStateOf("auto") }
    // Extensiones con su botón en la barra (null = aún sin elegir: sólo los bloqueadores).
    var extAncladas by remember { mutableStateOf<Set<String>?>(null) }

    // Fase 4 · PDF cuya descarga está en curso y abrirá el visor al terminar.
    val pendingPdf = remember { mutableStateOf<DownloadEntry?>(null) }

    fun setTb(key: String, value: Boolean) {
        when (key) {
            "fav" -> tbFav = value
            "hist" -> tbHist = value
            "down" -> tbDown = value
            "ext" -> tbExt = value
            else -> tbFloat = value
        }
        scope.launch(Dispatchers.IO) {
            db.settingDao().put(Setting("tb_$key", if (value) "1" else "0"))
        }
    }

    fun revealChrome() {
        chromeOpen = true
    }

    fun toast(msg: String) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    val webTabs = remember { mutableMapOf<Int, WebTab>() }
    // Pestañas web cuyo motor se soltó (por memoria o porque cayó): se recargan al volver.
    val descartadas = remember { mutableSetOf<Int>() }
    // Fase 8 · ventanas emergentes: pestañas que una página abrió con
    // window.close() (se pueden retirar solas) y sus identificadores.
    // Se recuerdan porque webTab() captura estos objetos al crearse.
    val popupCloseHandler = remember { mutableStateOf<(WebTab) -> Unit>({}) }
    val popupIds = remember { mutableSetOf<Int>() }

    val activeTab = tabs.firstOrNull { it.id == activeId } ?: tabs.first()
    val engine = engines.firstOrNull { it.id == engineId }
        ?: AntenaDatabase.DEFAULT_ENGINES.first()

    fun updateTab(id: Int, transform: (PageTab) -> PageTab) {
        tabs = tabs.map { if (it.id == id) transform(it) else it }
    }

    fun refreshData() = scope.launch(Dispatchers.IO) {
        val f = db.favoriteDao().all()
        val fo = db.folderDao().all()
        val h = db.historyDao().recent()
        val d = db.downloadDao().all()
        withContext(Dispatchers.Main) {
            favorites = f
            folders = fo
            history = h
            downloads = d
        }
    }

    /**
     * Una descarga acaba de empezar: se abre el panel de Descargas (junto a su botón) para ver el avance
     * y, si la pidió una ventana emergente que se quedó en blanco, esa pestaña se cierra sola.
     */
    fun descargaIniciada(idTab: Int) {
        val t = tabs.firstOrNull { it.id == idTab }
        val enBlanco = idTab in popupIds || t == null || t.url.isBlank() || t.url == "about:blank"
        if (enBlanco && tabs.size > 1) {
            scope.launch {
                delay(600)
                popupIds.remove(idTab)
                webTabs.remove(idTab)?.destroy()
                val idx = tabs.indexOfFirst { it.id == idTab }
                if (idx >= 0 && tabs.size > 1) {
                    tabs = tabs.filterNot { it.id == idTab }
                    if (activeId == idTab) activeId = tabs[minOf(idx, tabs.lastIndex)].id
                }
            }
        }
        if (overlay == null || overlay is Overlay.Panel) {
            refreshData()
            overlay = Overlay.Panel(PanelTipo.DESCARGAS)
        }
    }

    // Fase 4 · enlaces descargables pulsados en la página.
    fun handleLink(url: String, idTab: Int = activeId): Boolean {
        val path = runCatching { android.net.Uri.parse(url).path ?: "" }.getOrDefault("").lowercase()
        return when {
            path.endsWith(".xpi") -> {
                // Un .xpi pulsado en una página (o en AMO) es una extensión:
                // se monta en el motor en vez de guardarlo como archivo.
                toast("Instalando extensión…")
                GeckoMotor.instalar(url)
                true
            }
            path.endsWith(".pdf") -> {
                // Se descarga y, al terminar, el receiver abre el visor interno.
                startSystemDownload(context, url, "application/pdf", null) { entry ->
                    pendingPdf.value = entry
                    scope.launch(Dispatchers.IO) { db.downloadDao().insert(entry) }
                }
                descargaIniciada(idTab)
                true
            }
            // Videos, audios y archivos: se descargan (con su avance a la vista). Se reproducen o
            // instalan después desde Archivos; antes se abría un reproductor que no tenía sentido.
            MEDIA_EXTS.any { path.endsWith(it) } || FILE_EXTS.any { path.endsWith(it) } -> {
                startSystemDownload(context, url, null, null) { entry ->
                    scope.launch(Dispatchers.IO) { db.downloadDao().insert(entry) }
                }
                descargaIniciada(idTab)
                true
            }
            else -> false
        }
    }

    fun webTab(id: Int, laAbreGecko: Boolean = false): WebTab = webTabs.getOrPut(id) {
        if (webTabs.isEmpty()) {
            // El proceso de contenido ya está caliente: la sesión de calentamiento se suelta pasados unos segundos.
            scope.launch { delay(4_000); GeckoMotor.liberarPrecalentamiento() }
        }
        if (!GeckoMotor.bloqueadorListo && webTabs.isEmpty()) {
            toast("Primera carga: el motor y el bloqueador están arrancando…")
        }
        WebTab(id, context, laAbreGecko = laAbreGecko).also { w ->
            if (!agenteAnotado) {
                agenteAnotado = true
                Bitacora.add(
                    LogKind.SISTEMA,
                    "Motor de página: GeckoView (Firefox) · extensiones habilitadas",
                )
            }
            if (id == activeId) GeckoMotor.activarSesion(w.session)
            w.onIntercept = { u -> handleLink(u, id) }
            w.onClaveGuardar = { p ->
                claveGuardar?.responder?.invoke(false)
                claveGuardar = PeticionGuardar(p.entry, p.actualiza) { si -> claveGuardar = null; p.responder(si) }
            }
            w.onClaveElegir = { p ->
                claveElegir?.responder?.invoke(null)
                claveElegir = PeticionElegir(p.opciones) { e -> claveElegir = null; p.responder(e) }
            }
            // Fase 5 · mantener OK / pulsación larga → menú contextual de la página.
            w.onContext = { link, img ->
                if (id == activeId) {
                    // Mientras el menú está abierto la página no retiene el foco.
                    w.view.isFocusable = false
                    w.view.clearFocus()
                    overlay = Overlay.ContextMenu(ContextTarget(link = link, img = img))
                }
            }
            // Paso 5 · texto seleccionado (no editable) → menú con Copiar/Buscar/
            // Compartir. El handle para ejecutar vive en w.seleccion.
            w.onSelection = { texto, _ ->
                if (id == activeId && overlay == null) {
                    w.view.isFocusable = false
                    w.view.clearFocus()
                    overlay = Overlay.ContextMenu(ContextTarget(texto = texto))
                }
            }
            // Mientras la pestaña muestra el dashboard (cocosurf://), la sesión
            // que queda detrás en about:blank —la carga goHome() para cortar el
            // sonido de la página anterior— no debe pisar la URL ni el título:
            // eso cerraba el dashboard y «Inicio» se quedaba en una página en
            // blanco sin favoritos. Otras ubicaciones sí se aceptan (p. ej. al
            // volver atrás desde el dashboard).
            w.onTitle = { t ->
                updateTab(id) { if (it.url.startsWith("cocosurf://")) it else it.copy(title = t) }
            }
            w.onUrl = { u ->
                updateTab(id) {
                    if (it.url.startsWith("cocosurf://") && u.startsWith("about:")) it
                    else it.copy(url = u)
                }
            }
            w.onProgress = { p -> if (id == activeId) progress = p }
            w.onHistory = { h -> if (id == activeId) canGoBack = h }
            w.onLoaded = { u, ok ->
                if (u.startsWith("http")) {
                    val title = w.title
                    // Fase 6 · bitácora: el sitio visitado, con su título.
                    val etiqueta = title.trim().ifBlank { hostOf(u) ?: u }
                    Bitacora.add(
                        if (ok) LogKind.SITIO else LogKind.ERROR,
                        when {
                            !ok -> "No se pudo cargar $u"
                            etiqueta == u -> u
                            else -> "$etiqueta · $u"
                        },
                    )
                    if (ok) {
                        scope.launch(Dispatchers.IO) {
                            // Alta sin duplicar: si la dirección ya estaba en el
                            // historial, se actualiza su uso en la fila que ya
                            // existe en vez de añadir otra igual.
                            db.historyDao().registrar(u, title)
                            // Refresco inmediato: el sitio aparece en Historial sin esperar.
                            refreshData()
                        }
                    }
                    // Fase 5 · la página toma el foco: con el mando se desplaza
                    // (flechas), ↓ entra y ↑ a la barra sale hacia los controles.
                    if (id == activeId && !editing && overlay == null) {
                        w.view.post { w.view.requestFocus() }
                        // Coco Surf · página cargada: las barras suben y dejan la
                        // pantalla entera para ella (Atrás las vuelve a bajar).
                        chromeOpen = false
                        // Al cambiar el alto disponible Gecko recoloca la página
                        // sin avisar del scroll: se parte de «arriba» y los
                        // eventos reales de scroll lo corrigen.
                        scrolled = false
                    }
                }
            }
            w.onDownload = { url, mime, disposition ->
                if (url.substringBefore('?').endsWith(".xpi", ignoreCase = true)) {
                    // Un .xpi no es un archivo: se monta como extensión.
                    GeckoMotor.instalar(url)
                } else {
                    startSystemDownload(context, url, mime, disposition) { entry ->
                        scope.launch(Dispatchers.IO) { db.downloadDao().insert(entry) }
                    }
                    descargaIniciada(id)
                }
            }
            w.onScroll = { y ->
                if (id == activeId) scrolled = y > 40
            }
            // Fase 8 · target=_blank / window.open → pestaña nueva.
            // Con gesto del usuario va al frente; sin él (popunder de
            // publicidad) va al fondo, y si hay demasiadas pestañas se bloquea.
            w.onNewWindow = { gesture, destino ->
                // Anti-popups: sólo se abre si el usuario acaba de tocar o pulsar OK.
                // Los «popunder» y las pestañas que las páginas abren solas se
                // descartan (y se avisa para que se sepa qué pasó).
                val permitido = gesture && tabs.size < 12
                if (!permitido) {
                    Bitacora.add(
                        LogKind.ERROR,
                        "Ventana emergente bloqueada · ${hostOf(destino) ?: destino}",
                    )
                    toast("Ventana emergente bloqueada")
                    null
                } else {
                    val t = PageTab(nextId, "Nueva pestaña", "about:blank")
                    nextId += 1
                    popupIds.add(t.id)
                    tabs = tabs + t
                    Bitacora.add(
                        LogKind.PAGINA,
                        "Ventana emergente ${if (gesture) "al frente" else "en segundo plano"} · pestaña ${t.id}",
                    )
                    // OnNewSession: la sesión se devuelve SIN abrir y Gecko
                    // la abre (y la acopla la vista de la pestaña nueva).
                    val nueva = webTab(t.id, laAbreGecko = true)
                    if (gesture) {
                        activeId = t.id
                        progress = 1
                        revealChrome()
                    }
                    // Gecko carga la URL en la sesión devuelta (onNewSession).
                    nueva.session
                }
            }
            w.onWindowClosed = { t -> popupCloseHandler.value(t) }
            // Pantalla completa del vídeo: barras fuera, sistema en modo inmersivo.
            w.onPantallaCompleta = { fs -> if (id == activeId) pantallaCompleta = fs }
            // El motor de la pestaña cayó: se recrea y recarga sola (sin dejar la página muerta).
            w.onProcesoCaido = { motivo ->
                Bitacora.add(LogKind.ERROR, "Pestaña $id recreada tras caer el motor ($motivo)")
                webTabs.remove(id)
                descartadas += id
                recarga++
                toast("La página se cerró por falta de memoria; se vuelve a cargar")
            }
        }
    }

    /** M5 · pestaña con motor Gecko (se crea bajo demanda, como WebTab). */
    fun destroy(id: Int) {
        webTabs.remove(id)?.destroy()
    }

    // ── Memoria ──────────────────────────────────────────────────────────
    // Pestañas web cuyo motor se soltó para ahorrar memoria: siguen en la tira y
    // se recargan al volver a ellas.
    val ultimoUso = remember { mutableMapOf<Int, Long>() }

    fun guardarTeclaPrecision() {
        val t = Puntero.teclaPrecision
        scope.launch(Dispatchers.IO) { db.settingDao().put(Setting("cursor_tecla_prec", t.toString())) }
    }

    /** Guarda la configuración del monitor (posición, cifras, tecla…). */
    fun guardarMonitor() {
        val pos = MonitorConfig.posicion
        val graf = MonitorConfig.graficas
        val met = MonitorConfig.metricas.joinToString(",")
        val tecla = MonitorConfig.tecla
        val vis = MonitorConfig.visible
        scope.launch(Dispatchers.IO) {
            db.settingDao().put(Setting("mon_pos", pos))
            db.settingDao().put(Setting("mon_graf", if (graf) "1" else "0"))
            db.settingDao().put(Setting("mon_met", met))
            db.settingDao().put(Setting("mon_tecla", tecla.toString()))
            db.settingDao().put(Setting("mon_vis", if (vis) "1" else "0"))
        }
    }

    /**
     * Suelta el motor de las pestañas web de fondo, las menos usadas primero, hasta
     * dejar `conservar` vivas (la activa cuenta). Devuelve cuántas descartó.
     */
    fun descartarFondo(conservar: Int, motivo: String): Int {
        val candidatas = webTabs.keys
            .filter { it != activeId && it != popupId && it !in popupIds }
            .sortedBy { ultimoUso[it] ?: 0L }
        var vivas = webTabs.size
        var quitadas = 0
        for (id in candidatas) {
            if (vivas <= conservar) break
            val t = tabs.firstOrNull { it.id == id } ?: continue
            // Las vistas internas no tienen página que recargar: no se tocan.
            if (!t.url.startsWith("http")) continue
            destroy(id)
            descartadas += id
            vivas--
            quitadas++
            Bitacora.add(LogKind.SISTEMA, "Memoria: «${t.title}» descartada ($motivo)")
        }
        return quitadas
    }

    /** Memoria al límite: se suelta el motor de la página activa y se ofrece reintentar. */
    fun pausarActiva() {
        val id = activeId
        destroy(id)
        descartadas -= id
        pausadas = pausadas + id
        recarga++
        toast("Memoria al límite: página pausada para proteger el aparato")
    }

    var ultimoReciclaje = 0L

    /**
     * Reciclar: se suelta el motor de la página activa y se vuelve a cargar limpia.
     * Páginas de desplazamiento infinito (Facebook, TikTok) acumulan cientos de MB;
     * recargarlas a tiempo evita que el televisor se cuelgue.
     */
    fun reciclarActiva(motivo: String) {
        val id = activeId
        ultimoReciclaje = System.currentTimeMillis()
        Bitacora.add(LogKind.ERROR, "Página reciclada ($motivo) · ${activeTab.url}")
        destroy(id)
        descartadas += id
        recarga++
        toast("Poca memoria: la página se recargó para evitar que el televisor se cuelgue")
    }

    /** Antes de abrir otra página en una pestaña nueva: que quepa. */
    fun prepararMemoria() {
        val tope = Memoria.maxVivas(context)
        descartarFondo(maxOf(1, tope - 1), "pestaña nueva")
    }

    fun submit(raw: String) {
        val target = resolveInput(raw, engine.template)
        if (target.isEmpty()) return
        editing = false
        revealChrome()
        // PDF/media/archivos tipeados: la app los gestiona sin navegar.
        if (handleLink(target)) return
        updateTab(activeId) { it.copy(url = target, title = hostOf(target) ?: target) }
        progress = 1
        val w = webTab(activeId)
        w.load(target)
        w.view.requestFocus()
    }

    fun openUrl(url: String) {
        // Gestiones especiales (PDF/media): no se navega.
        if (handleLink(url)) return
        editing = false
        overlay = null
        revealChrome()
        updateTab(activeId) { it.copy(url = url, title = hostOf(url) ?: url) }
        progress = 1
        val w = webTab(activeId)
        w.load(url)
        w.view.requestFocus()
    }

    /**
     * Paso 2 · abre (o trae al frente) una vista interna como pestaña. Si ya hay
     * una pestaña con esa misma URL se activa en lugar de duplicarla: pulsar dos
     * veces «Configuración» no llena la tira de pestañas repetidas.
     */
    fun abrirInterna(url: String) {
        val titulo = tituloInterna(url) ?: return
        overlay = null
        editing = false
        revealChrome()
        val existente = tabs.firstOrNull { it.url == url }
        if (existente != null) {
            activeId = existente.id
            progress = 0
            return
        }
        val t = PageTab(nextId, titulo, url)
        nextId += 1
        tabs = tabs + t
        activeId = t.id
        progress = 0
        Bitacora.add(LogKind.SISTEMA, "Pestaña interna «$titulo»")
    }

    /** Fase 5 · abre un enlace en otra pestaña, sin salir de la actual. */
    fun openInNewTab(url: String, background: Boolean) {
        // Con la memoria en la reserva no se abren más pestañas: es lo que cuelga al TV.
        if (Memoria.leer(context).presion >= Memoria.Presion.CRITICA) {
            Bitacora.add(LogKind.ERROR, "Pestaña nueva rechazada por poca memoria · $url")
            toast("Poca memoria: cierra una pestaña antes de abrir otra")
            return
        }
        prepararMemoria()
        val t = PageTab(nextId, hostOf(url) ?: url, url)
        nextId += 1
        tabs = tabs + t
        val w = webTab(t.id)
        w.load(url)
        if (!background) {
            activeId = t.id
            progress = 1
            revealChrome()
        }
    }

    /**
     * Paso 2 · abrir una dirección desde una vista interna (Historial): la página
     * va a una pestaña web nueva y la pestaña del historial sigue donde estaba
     * (antes el overlay del historial se cerraba al navegar y se perdía el sitio).
     */
    fun abrirDesdeVistaInterna(url: String) {
        if (handleLink(url)) return
        openInNewTab(url, background = false)
    }

    fun newTab() {
        val t = PageTab(nextId, "Inicio", homepage)
        nextId += 1
        tabs = tabs + t
        activeId = t.id
        progress = 0
        revealChrome()
        // El dashboard se muestra como contenido cuando la URL es interna.
    }

    /**
     * M5 · pestaña que navega con las extensiones: ya no existe pestaña sin
     * motor Gecko, así que «probar extensiones» es abrir la tienda AMO.
     */
    fun nuevaPestañaGecko() {
        val destino = if (homepage.startsWith("http")) {
            homepage
        } else {
            "https://addons.mozilla.org/es-ES/firefox/extensions/"
        }
        val t = PageTab(nextId, "Pestaña con extensiones", destino)
        nextId += 1
        tabs = tabs + t
        activeId = t.id
        progress = 1
        revealChrome()
        Bitacora.add(LogKind.SISTEMA, "Nueva pestaña (extensiones) · $destino")
        val w = webTab(t.id)
        w.load(destino)
        w.view.requestFocus()
    }

    fun closeTab(id: Int) {
        // Fase 9 · lo que se cierra queda en «Cerrado recientemente».
        val cerrada = tabs.firstOrNull { it.id == id && it.url.isNotBlank() }
        if (cerrada != null) {
            cerradas =
                (listOf(TabCerrada(cerrada.id, tabLabel(cerrada), cerrada.url, System.currentTimeMillis())) + cerradas)
                    .take(60)
        }
        destroy(id)
        if (tabs.size == 1) {
            val fresh = PageTab(nextId, "Inicio", homepage)
            nextId += 1
            tabs = listOf(fresh)
            activeId = fresh.id
        } else {
            val idx = tabs.indexOfFirst { it.id == id }
            tabs = tabs.filterNot { it.id == id }
            if (activeId == id) {
                activeId = tabs[minOf(idx, tabs.lastIndex)].id
            }
        }
    }

    // Fase 8 · window.close(): la pestaña de la ventana emergente se retira
    // sola. No pasa por closeTab para no llenar «Cerrado recientemente».
    popupCloseHandler.value = { cerrada ->
        val id = webTabs.entries.firstOrNull { it.value === cerrada }?.key
        if (id != null && popupIds.remove(id)) {
            destroy(id)
            if (tabs.size > 1) {
                val idx = tabs.indexOfFirst { it.id == id }
                tabs = tabs.filterNot { it.id == id }
                if (activeId == id) {
                    activeId = tabs[minOf(idx, tabs.lastIndex)].id
                }
            }
        }
    }

    /** Fase 9 · salta a una pestaña abierta desde el panel de búsqueda. */
    fun irATab(tab: PageTab) {
        activeId = tab.id
        progress = 0
        overlay = null
        revealChrome()
    }

    /** Fase 9 · recupera una pestaña cerrada. */
    fun restaurarTab(c: TabCerrada) {
        cerradas = cerradas.filterNot { it.id == c.id }
        // Paso 2 · si esa vista interna ya está abierta, se trae al frente.
        if (tituloInterna(c.url) != null) {
            abrirInterna(c.url)
            return
        }
        val t = PageTab(nextId, c.title, c.url)
        nextId += 1
        tabs = tabs + t
        activeId = t.id
        overlay = null
        revealChrome()
        if (c.url.startsWith("cocosurf://")) {
            progress = 0
        } else {
            progress = 1
            webTab(t.id).load(c.url)
        }
    }

    fun goHome() {
        revealChrome()
        if (homepage.startsWith("cocosurf://")) {
            editing = false
            updateTab(activeId) { it.copy(url = homepage, title = "Inicio") }
            progress = 0
            webTabs[activeId]?.load("about:blank")
        } else {
            openUrl(homepage)
        }
    }

    // Carga inicial de datos + motores/ajustes guardados.
    LaunchedEffect(Unit) {
        // M5 · motor Gecko desde el arranque: la primera pestaña no paga el
        // coste de crear el runtime y las extensiones se listan al abrir ☰.
        GeckoMotor.arrancar(context)
        val (list, savedEngine, savedHome) = withContext(Dispatchers.IO) {
            AntenaDatabase.seed(db)
            val l = db.engineDao().all()
            val s = db.settingDao().get(AntenaDatabase.KEY_SEARCH_ENGINE)
            val hp = db.settingDao().get(AntenaDatabase.KEY_HOMEPAGE)
            Triple(l, s, hp)
        }
        if (list.isNotEmpty()) engines = list
        if (savedEngine != null && list.any { it.id == savedEngine }) engineId = savedEngine
        // Los ajustes guardados con el nombre anterior (antena://) siguen valiendo.
        if (savedHome != null) homepage = savedHome.replace("antena://", "cocosurf://")

        // Botones de la barra de herramientas (por defecto visibles).
        val tb = withContext(Dispatchers.IO) {
            listOf("fav", "hist", "down", "float", "ext").map { k -> db.settingDao().get("tb_$k") }
        }
        tbFav = tb[0] != "0"
        tbHist = tb[1] != "0"
        tbDown = tb[2] != "0"
        tbFloat = tb[3] != "0"
        tbExt = tb[4] != "0"
        // Perfil: color de acento y extensiones ancladas a la barra.
        val (acento, ancladas) = withContext(Dispatchers.IO) {
            db.settingDao().get("acento") to db.settingDao().get("ext_ancladas")
        }
        if (acento != null) com.antena.tv.ui.TemaApp.elegir(acento)
        if (ancladas != null) extAncladas = ancladas.split(',').filter { it.isNotBlank() }.toSet()
        // Control remoto, modo de página y monitor de recursos.
        val otros = withContext(Dispatchers.IO) {
            listOf("cursor_vel", "modo_pagina", "mon_pos", "mon_graf", "mon_met", "mon_tecla", "mon_vis", "cursor_tecla_prec")
                .map { db.settingDao().get(it) }
        }
        otros[0]?.let { velocidadId = it; Puntero.factorVel = factorDeVelocidad(it) }
        otros[1]?.let { modoPaginaId = it; ModoPagina.preferencia = it }
        otros[2]?.let { MonitorConfig.posicion = it }
        otros[3]?.let { MonitorConfig.graficas = it == "1" }
        otros[4]?.let { MonitorConfig.metricas = it.split(',').filter { s -> s.isNotBlank() }.toSet() }
        otros[5]?.toIntOrNull()?.let { MonitorConfig.tecla = it }
        otros[6]?.let { MonitorConfig.visible = it == "1" }
        otros[7]?.toIntOrNull()?.let { Puntero.teclaPrecision = it }
        // Pestañas de la sesión anterior.
        val guardadas = withContext(Dispatchers.IO) {
            Triple(db.settingDao().get("restaurar_pestanas"), db.settingDao().get("pestanas_guardadas"), db.settingDao().get("pestana_activa"))
        }
        restaurarPestanas = guardadas.first != "0"
        precalentarMotor = withContext(Dispatchers.IO) { db.settingDao().get("precalentar") } != "0"
        if (restaurarPestanas && !guardadas.second.isNullOrBlank()) {
            runCatching {
                val arr = org.json.JSONArray(guardadas.second)
                val lista = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    PageTab(i, o.optString("t").ifBlank { hostOf(o.getString("u")) ?: "Página" }, o.getString("u"))
                }.filter { it.url.startsWith("http") }
                if (lista.isNotEmpty()) {
                    webTabs.keys.toList().forEach { destroy(it) }
                    tabs = lista
                    nextId = lista.size
                    activeId = (guardadas.third?.toIntOrNull() ?: 0).coerceIn(0, lista.size - 1)
                    // Se cargan al mirarlas: sólo la activa gasta memoria al abrir.
                    descartadas.addAll(lista.map { it.id })
                    Bitacora.add(LogKind.SISTEMA, "Pestañas restauradas: ${lista.size}")
                }
            }.onFailure { Bitacora.add(LogKind.ERROR, "No se pudieron restaurar las pestañas: ${it.message}") }
        }
        pestanasCargadas = true
        // Si la primera pantalla es una página web ya se carga sola; en Inicio se calienta el motor.
        if (precalentarMotor && tabs.firstOrNull { it.id == activeId }?.url?.startsWith("http") != true) {
            GeckoMotor.precalentar(context)
        }
        // Una vez al día se mira si hay versión nueva (sin molestar si no la hay).
        val ultimaBusqueda = withContext(Dispatchers.IO) { db.settingDao().get("act_ultima")?.toLongOrNull() ?: 0L }
        if (System.currentTimeMillis() - ultimaBusqueda > 24L * 3600_000L) {
            scope.launch {
                delay(20_000)
                val r = Actualizador.buscar(context)
                withContext(Dispatchers.IO) { db.settingDao().put(Setting("act_ultima", System.currentTimeMillis().toString())) }
                if (r is Actualizador.Estado.Nueva) toast("Hay una versión nueva (${r.version}): Menú → Acerca de Coco Surf")
            }
        }
        refreshData()
    }

    DisposableEffect(Unit) {
        onDispose {
            webTabs.keys.toList().forEach { destroy(it) }
        }
    }

    // El chip 🛡 de la barra muestra el contador de la pestaña en pantalla
    // (p. ej. los anuncios que uBO lleva bloqueados en esta página).
    LaunchedEffect(activeId) {
        GeckoMotor.activarSesion(webTabs[activeId]?.session)
        // La pestaña del panel de una extensión se mira con los ojos de la
        // pestaña que lo abrió: su popup pregunta por «la pestaña activa» para
        // saber en qué sitio está actuando. Volver al panel es un caso normal
        // (mirarlo, tocar algo, ir a la página y volver), no una salida.
        val origen = webTabs[popupOrigen]?.session
        if (activeId == popupId && origen != null) {
            GeckoMotor.fijarSesion(origen)
        } else if (activeId != popupId) {
            // Se dejó atrás la pestaña del panel: la marca vuelve a la normal.
            popupId = -1
            popupOrigen = -1
            GeckoMotor.fijarSesion(null)
        }
    }

    // Clic en el chip de una extensión → la extensión pide su panel: se abre en
    // una pestaña. Paso 7: además se atienden sus otras dos peticiones reales
    // —su página de opciones y una pestaña nueva— porque GeckoView las deja en
    // manos de la app (su `openOptionsPage` interno no es público).
    DisposableEffect(Unit) {
        GeckoMotor.alOpciones = { peticion ->
            val destino = urlSegura(peticion.url)
            val pedida = android.os.SystemClock.uptimeMillis() - Puntero.ultimaAccion < 3000
            if (!pedida) {
                Bitacora.add(LogKind.ERROR, "${peticion.nombre} quiso abrir sus opciones por su cuenta: bloqueado")
            } else if (destino == null) {
                // Sin página publicada no se inventa nada: se dice tal cual.
                Bitacora.add(LogKind.SISTEMA, "${peticion.nombre} pidió opciones y no publica ninguna")
                toast("${peticion.nombre} no publica página de opciones")
            } else {
                Bitacora.add(LogKind.SISTEMA, "Opciones de ${peticion.nombre} · $destino")
                openInNewTab(destino, background = false)
            }
        }
        GeckoMotor.alNuevaPestana = { url, alFrente ->
            // Sólo si el usuario acaba de pulsar algo (su botón, el panel del complemento…).
            // Los complementos que abren su «bienvenida» o su sitio por su cuenta al
            // cargar cada página (AdGuard, p. ej.) se bloquean: eso sacaba al usuario de
            // la página que estaba viendo.
            val pedida = android.os.SystemClock.uptimeMillis() - Puntero.ultimaAccion < 3000
            if (!pedida) {
                Bitacora.add(LogKind.ERROR, "Un complemento quiso abrir una pestaña por su cuenta: bloqueada · $url")
                null
            } else {
                val t = PageTab(nextId, hostOf(url) ?: url, url)
                nextId += 1
                tabs = tabs + t
                if (alFrente) {
                    activeId = t.id
                    progress = 1
                    revealChrome()
                }
                Bitacora.add(LogKind.PAGINA, "Pestaña pedida por una extensión · $url")
                val w = webTab(t.id)
                w.load(url)
                w.session
            }
        }
        GeckoMotor.alPopup = { nombre ->
            // La pestaña de origen (la que el usuario miraba) es la que las
            // extensiones seguirán contando mientras el panel esté abierto.
            val activeIdAntes = activeId
            val origen = GeckoMotor.sesionParaExtensiones()
            val t = PageTab(nextId, nombre, "about:blank")
            nextId += 1
            tabs = tabs + t
            activeId = t.id
            progress = 1
            overlay = null
            revealChrome()
            popupId = t.id
            popupOrigen = activeIdAntes
            if (origen != null) GeckoMotor.fijarSesion(origen)
            val w = webTab(t.id)
            w.session
        }
        onDispose {
            GeckoMotor.alPopup = null
            GeckoMotor.alOpciones = null
            GeckoMotor.alNuevaPestana = null
        }
    }

    // Fase 5 · mantener OK (mando) → menú contextual de la página.
    DisposableEffect(Unit) {
        val activity = context as? MainActivity
        activity?.onLongOk = {
            val current = tabs.firstOrNull { it.id == activeId }
            val tab = webTabs[activeId]
            val inPage = tab?.view?.hasFocus() == true
            if (
                tab != null && inPage && overlay == null && !editing &&
                current != null && current.url.startsWith("http")
            ) {
                // Si hay selección activa, menú de texto; si no, el elemento que
                // resolvió la extensión interna (tag «contexto»); sin elemento,
                // menú de página.
                val textoSel = webTabs[activeId]
                    ?.ultimoTextoSeleccionado?.takeIf { it.isNotBlank() }
                val (link, img) = GeckoMotor.ultimoContexto
                overlay = Overlay.ContextMenu(
                    if (textoSel != null) ContextTarget(texto = textoSel)
                    else ContextTarget(link = link, img = img),
                )
                true
            } else {
                false
            }
        }
        // ↑ al tope de la página → foco de vuelta a la barra superior.
        activity?.onUpInPage = {
            val w = webTabs[activeId]
            if (
                w != null && w.view.hasFocus() && w.scrollY <= 40 &&
                overlay == null && !editing
            ) {
                revealChrome()
                // La barra pasa de 0 a 30 dp: el foco se pide cuando ya existe.
                composeView.postDelayed({ barFr.requestFocus() }, 320)
                true
            } else {
                false
            }
        }
        onDispose {
            activity?.onLongOk = null
            activity?.onUpInPage = null
        }
    }

    // Al abrirse el menú el cursor salta a su interior: así se puede recorrer y
    // desplazar (contra el borde de abajo) sin tener que cruzar la pantalla.
    val densidadUi = androidx.compose.ui.platform.LocalDensity.current.density
    val anchoUi = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    LaunchedEffect(overlay is Overlay.Menu) {
        if (overlay is Overlay.Menu) {
            Puntero.irA((anchoUi - 200f) * densidadUi, (Puntero.origenY + 330f * densidadUi))
        }
    }

    // Pestaña a la vista = pestaña viva; las demás se duermen (y, si falta memoria, se sueltan).
    LaunchedEffect(activeId) {
        ultimoUso[activeId] = System.currentTimeMillis()
        delay(400)
        webTabs.forEach { (id, w) ->
            if (id != popupId && id !in popupIds) runCatching { w.session.setActive(id == activeId) }
        }
    }
    val enVideoActual by rememberUpdatedState(pantallaCompleta || webTabs[activeId]?.reproduciendo == true)
    // La corutina del muestreo se crea una sola vez: estos valores se leen siempre actuales.
    val vivasActual by rememberUpdatedState(webTabs.size)
    val urlActual by rememberUpdatedState(activeTab.url)
    // Guarda las pestañas abiertas (con un pequeño retraso) para traerlas la próxima vez.
    LaunchedEffect(tabs, activeId, restaurarPestanas, pestanasCargadas) {
        if (!pestanasCargadas) return@LaunchedEffect
        delay(2_000)
        withContext(Dispatchers.IO) {
            if (!restaurarPestanas) {
                db.settingDao().put(Setting("pestanas_guardadas", ""))
            } else {
                val web = tabs.filter { it.url.startsWith("http") }.take(12)
                val arr = org.json.JSONArray()
                web.forEach { arr.put(org.json.JSONObject().put("u", it.url).put("t", it.title)) }
                db.settingDao().put(Setting("pestanas_guardadas", arr.toString()))
                db.settingDao().put(Setting("pestana_activa", web.indexOfFirst { it.id == activeId }.coerceAtLeast(0).toString()))
            }
        }
    }

    // Vigilante: cada pocos segundos mide la memoria libre y actúa ANTES de que
    // el televisor se cuelgue.
    LaunchedEffect(Unit) {
        var anterior = Memoria.Presion.NORMAL
        var extremas = 0
        var criticas = 0
        var ultimaLimpieza = 0L
        while (true) {
            delay(2_000)
            val e = Memoria.leer(context)
            val tope = Memoria.maxVivas(context)
            if (e.presion != anterior) {
                Bitacora.add(
                    if (e.presion >= Memoria.Presion.CRITICA) LogKind.ERROR else LogKind.SISTEMA,
                    "Memoria ${Memoria.etiqueta(e.presion).lowercase()}: ${e.libreMb} MB libres de ${e.totalMb} MB " +
                        "(reserva ${e.reservaMb} MB) · pestañas vivas ${webTabs.size}/$tope · " + com.antena.tv.gecko.MemoriaSistema.resumen(),
                )
                anterior = e.presion
            }
            // Limpieza del motor: con presión de memoria (y más si hay un video corriendo) se le pide a
            // Gecko que suelte cachés y haga limpieza, como cuando Android se lo pide. Una vez por minuto.
            val ahoraMs = System.currentTimeMillis()
            if (e.presion >= Memoria.Presion.BAJA && enVideoActual && ahoraMs - ultimaLimpieza > 60_000) {
                ultimaLimpieza = ahoraMs
                Memoria.pedirLimpiezaMotor(context)
                Bitacora.add(
                    LogKind.SISTEMA,
                    "Limpieza del motor pedida (video en curso, ${e.libreMb} MB libres)",
                )
            } else if (e.presion >= Memoria.Presion.CRITICA && ahoraMs - ultimaLimpieza > 60_000) {
                ultimaLimpieza = ahoraMs
                Memoria.pedirLimpiezaMotor(context)
                Bitacora.add(LogKind.SISTEMA, "Limpieza del motor pedida (${e.libreMb} MB libres)")
            }
            var quitadas = 0
            when (e.presion) {
                Memoria.Presion.EXTREMA -> {
                    quitadas = descartarFondo(1, "memoria extrema")
                    extremas++
                    // Dos lecturas seguidas en el límite: la página activa se recicla (se recarga
                    // limpia). Si ya se reciclaba hace poco y vuelve a pasar, se pausa del todo.
                    if (extremas >= 2 && activeTab.url.startsWith("http") && activeId !in pausadas) {
                        if (System.currentTimeMillis() - ultimoReciclaje < 90_000) {
                            Bitacora.add(
                                LogKind.ERROR,
                                "Memoria extrema (${e.libreMb} MB libres): página pausada para proteger el aparato · ${activeTab.url}",
                            )
                            pausarActiva()
                        } else {
                            reciclarActiva("memoria extrema, ${e.libreMb} MB libres")
                        }
                        extremas = 0
                        criticas = 0
                    }
                }
                Memoria.Presion.CRITICA -> {
                    quitadas = descartarFondo(1, "memoria crítica")
                    extremas = 0
                    // Crítica sostenida con una página muy grande: se recicla antes de llegar al límite.
                    criticas++
                    val pesada = (muestras.lastOrNull()?.paginasMb ?: 0L) >= 380L
                    if (criticas >= 4 && pesada && activeTab.url.startsWith("http") &&
                        activeId !in pausadas && System.currentTimeMillis() - ultimoReciclaje > 90_000
                    ) {
                        reciclarActiva("memoria crítica sostenida, página de ${muestras.last().paginasMb} MB")
                        criticas = 0
                    }
                }
                Memoria.Presion.BAJA -> {
                    quitadas = descartarFondo(minOf(tope, 2), "memoria baja")
                    extremas = 0
                    criticas = 0
                }
                Memoria.Presion.NORMAL -> {
                    quitadas = descartarFondo(tope, "límite de pestañas")
                    extremas = 0
                    criticas = 0
                }
            }
            if (quitadas > 0 && e.presion != Memoria.Presion.NORMAL) {
                toast("Poca memoria: cerré pestañas en segundo plano")
            }
        }
    }

    // Muestreo de recursos: cada segundo con el monitor a la vista, cada 15 s si no.
    // Alimenta los gráficos y el registro en disco que explica un cierre de golpe.
    LaunchedEffect(MonitorConfig.visible) {
        if (MonitorConfig.visible) com.antena.tv.gecko.FpsInterfaz.encender() else com.antena.tv.gecko.FpsInterfaz.apagar()
    }
    LaunchedEffect(Unit) {
        var n = 0
        var libreAntes = -1L
        var guardadas = 0
        while (true) {
            val visible = MonitorConfig.visible
            val enVideo = enVideoActual
            val vivas = vivasActual
            val url = urlActual
            val m = withContext(Dispatchers.Default) {
                com.antena.tv.gecko.Recursos.muestrear(context, vivas, url)
            }
            muestras.add(m)
            while (muestras.size > 90) muestras.removeAt(0)
            n++
            if (!visible || n % 15 == 0) {
                withContext(Dispatchers.IO) { com.antena.tv.gecko.Recursos.guardar(context, m) }
                // Memoria que se va comiendo deprisa: queda anotado.
                if (libreAntes > 0 && libreAntes - m.libreMb > m.totalMb / 6) {
                    Bitacora.add(
                        LogKind.SISTEMA,
                        "La memoria libre bajó de $libreAntes a ${m.libreMb} MB en poco tiempo " +
                            "(páginas ${m.paginasMb} MB, complementos ${m.complementosMb} MB) · $url",
                    )
                }
                libreAntes = m.libreMb
                // Con un video en curso se anota cada medición: es lo que explica a dónde se va la memoria.
                if (enVideo) {
                    val s = m.sis
                    Bitacora.add(
                        LogKind.SISTEMA,
                        "VIDEO · libre ${m.libreMb} MB · páginas ${m.paginasMb} · gráficos ${m.graficosMb} · " +
                            "complementos ${m.complementosMb} · interfaz ${m.appMb} · otros ${m.otrosMb}" +
                            (s?.let { " · caché ${it.cacheMb} · apps ${it.appsMb} · kernel ${it.kernelMb} · controladores ${it.sinContabilizarMb}" } ?: "") +
                            (m.cpuTotal?.let { " · CPU ${it.toInt()} %" } ?: "") + " · ${hostOf(url) ?: url}",
                    )
                }
                // Cada ~5 minutos queda en la Bitácora el reparto de la RAM del aparato (tendencia).
                guardadas++
                if (guardadas % 20 == 1) Bitacora.add(LogKind.SISTEMA, com.antena.tv.gecko.MemoriaSistema.resumen())
            }
            delay(if (visible) 1_000L else if (enVideo) 40_000L else 15_000L)
        }
    }
    // Prioridad al vídeo: mientras suena (o va en pantalla completa) el motor de esa pestaña pasa a
    // prioridad alta y la app deja de hacer trabajo de fondo que no hace falta.
    val modoVideo = pantallaCompleta || webTabs[activeId]?.reproduciendo == true
    LaunchedEffect(modoVideo, activeId, recarga) {
        webTabs.forEach { (id, w) ->
            runCatching {
                w.session.setPriorityHint(
                    if (modoVideo && id == activeId) {
                        org.mozilla.geckoview.GeckoSession.PRIORITY_HIGH
                    } else {
                        org.mozilla.geckoview.GeckoSession.PRIORITY_DEFAULT
                    },
                )
            }
        }
    }
    // Avisos de Android («recorta»): lo mismo, sin esperar al vigilante.
    DisposableEffect(Unit) {
        val actividad = context as? MainActivity
        actividad?.alRecortarMemoria = { nivel ->
            descartarFondo(if (nivel >= 15) 1 else minOf(2, Memoria.maxVivas(context)), "aviso del sistema")
        }
        actividad?.alMonitorCambio = { guardarMonitor() }
        actividad?.alPrecisionCambio = { guardarTeclaPrecision() }
        Puntero.alModoFino = { fino -> toast(if (fino) "Cursor corto (preciso)" else "Cursor largo (rápido)") }
        onDispose {
            actividad?.alRecortarMemoria = null
            actividad?.alMonitorCambio = null
        }
    }

    val extsTabs by GeckoMotor.extensiones.collectAsState()
    val iconosTabs by GeckoMotor.iconos.collectAsState()

    // El cursor «ilumina» lo que tiene debajo: al subir o bajar barras y paneles se
    // corrige solo, en vez de dejar botones marcados.
    LaunchedEffect(chromeOpen, overlay, panelFijo) {
        delay(350)
        Puntero.sincronizarHover()
    }

    // Aviso flotante del motor: aparece unos segundos y se va.
    val avisoMotor by GeckoMotor.aviso.collectAsState()
    var avisoVisible by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(avisoMotor) {
        val a = avisoMotor
        if (a != null) {
            avisoVisible = a
            // Los de «en curso» se quedan hasta que llegue el resultado.
            val enCurso = a.startsWith("Comprobando") || a.startsWith("Descargando")
            delay(if (enCurso) 20_000 else 5_000)
            avisoVisible = null
        }
    }

    // Protección desde el primer arranque: uBlock Origin (de la tienda oficial de
    // Firefox) se instala solo UNA vez si el usuario aún no tiene un bloqueador.
    // Después manda el usuario: si lo quita, no vuelve a instalarse.
    LaunchedEffect(Unit) {
        delay(6_000)
        val hecho = withContext(Dispatchers.IO) { db.settingDao().get("auto_bloqueador") }
        if (hecho == null) {
            GeckoMotor.arrancar(context)
            delay(2_500)
            val ya = GeckoMotor.extensiones.value.any {
                val n = it.nombre.lowercase()
                "ublock" in n || "adguard" in n || "adblock" in n
            }
            if (!ya) {
                Bitacora.add(LogKind.SISTEMA, "Primer arranque: instalando el bloqueador uBlock Origin")
                GeckoMotor.instalar(TIENDA.first().xpi)
            }
            withContext(Dispatchers.IO) { db.settingDao().put(Setting("auto_bloqueador", "1")) }
        }
    }

    // Fase 6 · bitácora: la primera entrada explica con qué se arrancó.
    LaunchedEffect(Unit) {
        // ¿La sesión anterior terminó de golpe? Los últimos datos de recursos dicen por qué.
        if (Bitacora.cierreAnormal) {
            withContext(Dispatchers.IO) { com.antena.tv.gecko.Recursos.resumenCierre(context) }?.let {
                Bitacora.add(LogKind.ERROR, it)
            }
        }
        Memoria.leer(context).let {
            Bitacora.add(
                LogKind.SISTEMA,
                "Dispositivo: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · RAM ${it.totalMb} MB " +
                    "(${it.libreMb} libres) · " +
                    if (it.pocaRam) "modo ahorro, máx. ${Memoria.maxVivas(context)} pestañas vivas" else "memoria normal",
            )
        }
        Bitacora.add(
            LogKind.SISTEMA,
            "Coco Surf ${version ?: "?"} arrancado · motor GeckoView (Firefox) con extensiones",
        )
        // P0 · contraseñas sobre la plataforma: qué gestor y qué proveedor hay.
        Bitacora.add(
            LogKind.SISTEMA,
            "Autofill: ${Probe.autofillInfo(context)} · Passkeys: ${Probe.passkeysInfo(context)}",
        )
    }

    // Fase 4 · PDF terminado → visor dentro de la app.
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                // Fase 6 · bitácora: cómo terminó la descarga (éxito o motivo).
                if (id >= 0) logDownloadResult(context, id)
                val pending = pendingPdf.value ?: return
                if (pending.dmId == id) {
                    pendingPdf.value = null
                    overlay = Overlay.Pdf(pending)
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    LaunchedEffect(editing) {
        if (editing) {
            wasEditing = true
            delay(80)
            fieldFr.requestFocus()
            fieldHasFocus = true
            keyboard?.show()
        } else if (wasEditing) {
            wasEditing = false
            keyboard?.hide()
            fieldHasFocus = false
        }
    }

    // Sugerencias: del historial **ya cargado** en memoria (≤500 filas), no de
    // una consulta a disco por pulsación. Sin texto → lo más usado y reciente;
    // con texto → lo que casa en dominio, dirección, título o búsqueda anterior.
    LaunchedEffect(editing, addressValue.text, history, engines) {
        if (editing) {
            sugsOpen = true
            fieldHasFocus = true
            // Un respiro entre teclas: no se recalcula en cada repetición.
            delay(90)
            suggestions = sugerencias(history, engines, addressValue.text)
        } else {
            suggestions = emptyList()
        }
    }

    // El scroll de la página llega por WebTab.onScroll (ScrollDelegate de
    // Gecko): la barra se pliega al bajar y vuelve al tope al subir.

    fun closeOverlay() {
        val ctx = overlay as? Overlay.ContextMenu
        overlay = null
        refreshData()
        if (ctx != null) {
            val w = webTabs[activeId]
            // Paso 5 · el menú de una selección no vuelve a salir por el mismo
            // texto (así se pueden ajustar sus tiradores sin que el menú salga
            // solo); una selección nueva sí lo vuelve a abrir.
            if (ctx.target.texto != null) w?.textoSeleccionadoDescartado = ctx.target.texto
            // La página recupera el foco (se le quitó al abrir el menú).
            w?.view?.isFocusable = true
            w?.view?.requestFocus()
        } else {
            menuFr.requestFocus()
        }
    }

    /** «Guardar como PDF» (a Descargas) o «Imprimir…» (servicio de impresión del sistema). */
    fun pdfDePagina(imprimir: Boolean) {
        val w = webTabs[activeId] ?: return
        val nombre = nombrePdf(activeTab.title, activeTab.url)
        val carpeta = if (imprimir) {
            java.io.File(context.cacheDir, "impresion")
        } else {
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        }
        // Sin pisar un PDF anterior con el mismo nombre.
        var destino = java.io.File(carpeta, nombre)
        var n = 1
        while (destino.exists() && !imprimir) {
            destino = java.io.File(carpeta, nombre.removeSuffix(".pdf") + " ($n).pdf")
            n++
        }
        toast(if (imprimir) "Preparando la impresión…" else "Generando el PDF…")
        generarPdf(context, w, destino) { archivo ->
            when {
                archivo == null -> toast("No se pudo generar el PDF de esta página")
                imprimir -> {
                    val act = context as? android.app.Activity
                    if (act == null || !imprimirPdf(act, archivo, nombre)) {
                        toast("Este aparato no tiene servicio de impresión")
                    }
                }
                else -> {
                    val entrada = DownloadEntry(
                        id = UUID.randomUUID().toString(),
                        dmId = -1L,
                        url = "file://" + archivo.absolutePath,
                        title = archivo.name,
                        addedAt = System.currentTimeMillis(),
                    )
                    scope.launch(Dispatchers.IO) {
                        db.downloadDao().insert(entrada)
                        refreshData()
                    }
                    Bitacora.add(LogKind.SISTEMA, "PDF guardado: ${archivo.name}")
                    toast("PDF guardado en Descargas: ${archivo.name}")
                }
            }
        }
    }

    /** Guarda la bitácora completa en un .txt (Descargas) y ofrece compartirlo. */
    fun exportarBitacora() {
        runCatching {
            val carpeta = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val fecha = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
            val archivo = java.io.File(carpeta, "CocoSurf-bitacora-$fecha.txt")
            archivo.writeText(
                Bitacora.comoTexto(
                    "Coco Surf ${version ?: "?"} · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · " +
                        "Android ${android.os.Build.VERSION.RELEASE} · ${Memoria.leer(context).totalMb} MB de RAM",
                ),
            )
            scope.launch(Dispatchers.IO) {
                db.downloadDao().insert(
                    DownloadEntry(
                        id = UUID.randomUUID().toString(),
                        dmId = -1L,
                        url = "file://" + archivo.absolutePath,
                        title = archivo.name,
                        addedAt = System.currentTimeMillis(),
                    ),
                )
                refreshData()
            }
            toast("Bitácora guardada en Descargas: ${archivo.name}")
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                archivo,
            )
            val enviar = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching {
                context.startActivity(
                    Intent.createChooser(enviar, "Exportar bitácora").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }.onFailure { toast("No se pudo exportar la bitácora") }
    }

    /** ¿Esta extensión tiene su botón en la barra? Por defecto, sólo los bloqueadores. */
    fun esAnclada(ext: com.antena.tv.gecko.ExtInfo): Boolean {
        val elegidas = extAncladas
        if (elegidas != null) return ext.id in elegidas
        val n = ext.nombre.lowercase()
        return "ublock" in n || "adguard" in n
    }

    fun anclar(ext: com.antena.tv.gecko.ExtInfo) {
        val todas = GeckoMotor.extensiones.value.filter { it.id != com.antena.tv.gecko.EXT_INTERNA }
        val actual = todas.filter { esAnclada(it) }.map { it.id }.toMutableSet()
        if (!actual.add(ext.id)) actual.remove(ext.id)
        extAncladas = actual
        scope.launch(Dispatchers.IO) {
            db.settingDao().put(Setting("ext_ancladas", actual.joinToString(",")))
        }
    }

    fun elegirVelocidad(id: String) {
        velocidadId = id
        Puntero.factorVel = factorDeVelocidad(id)
        scope.launch(Dispatchers.IO) { db.settingDao().put(Setting("cursor_vel", id)) }
    }

    fun elegirPrecalentar(si: Boolean) {
        precalentarMotor = si
        if (si) GeckoMotor.precalentar(context) else GeckoMotor.liberarPrecalentamiento()
        scope.launch(Dispatchers.IO) { db.settingDao().put(Setting("precalentar", if (si) "1" else "0")) }
    }

    fun abrirDescarga(d: DownloadEntry) {
        val f = if (d.dmId < 0) java.io.File(d.url.removePrefix("file://"))
        else java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), d.title)
        if (!f.exists()) { openDownloadedFile(context, d); return }
        when (com.antena.tv.archivos.Almacenamiento.tipoDe(f)) {
            com.antena.tv.archivos.Tipo.VIDEO, com.antena.tv.archivos.Tipo.AUDIO -> { closeOverlay(); reproductorArchivo = f }
            com.antena.tv.archivos.Tipo.APK -> com.antena.tv.archivos.Almacenamiento.instalarApk(context, f)?.let { toast(it) }
            else -> openDownloadedFile(context, d)
        }
    }

    fun irACarpetaDescargas() {
        carpetaArchivos = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
        closeOverlay()
        abrirInterna(INTERNA_ARCHIVOS)
    }

    fun elegirRestaurar(si: Boolean) {
        restaurarPestanas = si
        scope.launch(Dispatchers.IO) { db.settingDao().put(Setting("restaurar_pestanas", if (si) "1" else "0")) }
    }

    fun elegirModoPagina(id: String) {
        modoPaginaId = id
        ModoPagina.preferencia = id
        scope.launch(Dispatchers.IO) { db.settingDao().put(Setting("modo_pagina", id)) }
        // Se aplica a la página que se está viendo ahora mismo.
        webTabs[activeId]?.reload()
    }

    fun elegirAcento(id: String) {
        com.antena.tv.ui.TemaApp.elegir(id)
        scope.launch(Dispatchers.IO) { db.settingDao().put(Setting("acento", id)) }
    }

    /** Chip de la barra: abre el panel rápido; si ya está abierto o fijado, lo cierra. */
    fun alternarPanel(tipo: PanelTipo) {
        when {
            panelFijo == tipo -> panelFijo = null
            (overlay as? Overlay.Panel)?.tipo == tipo -> closeOverlay()
            else -> {
                refreshData()
                overlay = Overlay.Panel(tipo)
            }
        }
    }

    /** «Borrar datos de navegación»: historial y descargas (Room) + cookies y caché (Gecko). */
    fun borrarDatos(sel: SeleccionBorrado) {
        val desde = if (sel.ventanaMs == 0L) 0L else System.currentTimeMillis() - sel.ventanaMs
        scope.launch(Dispatchers.IO) {
            if (sel.historial) db.historyDao().borrarDesde(desde)
            if (sel.descargas) db.downloadDao().borrarDesde(desde)
            refreshData()
        }
        if (sel.cookies || sel.cache) {
            val rt = GeckoMotor.runtime(context)
            var banderas = 0L
            if (sel.cookies) {
                banderas = banderas or StorageController.ClearFlags.COOKIES or
                    StorageController.ClearFlags.SITE_DATA
            }
            if (sel.cache) banderas = banderas or StorageController.ClearFlags.ALL_CACHES
            // GeckoView 141 sólo borra cookies y caché por completo (no por intervalo).
            rt.storageController.clearData(banderas)
        }
        Bitacora.add(
            LogKind.SISTEMA,
            "Datos de navegación borrados · historial=${sel.historial} descargas=${sel.descargas} " +
                "cookies=${sel.cookies} caché=${sel.cache}",
        )
        overlay = null
        toast("Datos de navegación borrados")
    }

    /** Contenido de un panel rápido, flotante (overlay) o fijado a la derecha. */
    @Composable
    fun contenidoPanel(tipo: PanelTipo, fijado: Boolean, modifier: Modifier) {
        val alFijar = {
            if (fijado) {
                panelFijo = null
                overlay = Overlay.Panel(tipo)
            } else {
                panelFijo = tipo
                overlay = null
            }
        }
        val alCerrar = { if (fijado) panelFijo = null else closeOverlay() }
        when (tipo) {
            PanelTipo.COMPLEMENTOS -> {
                val exts by GeckoMotor.extensiones.collectAsState()
                val bitmaps by GeckoMotor.iconos.collectAsState()
                PanelComplementos(
                    extensiones = exts,
                    iconos = bitmaps,
                    ancladas = ::esAnclada,
                    fijado = fijado,
                    onFijar = alFijar,
                    onCerrar = alCerrar,
                    onAlternar = { GeckoMotor.alternar(it) },
                    onAnclar = ::anclar,
                    onAbrirExt = { overlay = Overlay.Extension(it.id) },
                    onPagina = {
                        GeckoMotor.arrancar(context)
                        GeckoMotor.refrescar()
                        overlay = Overlay.Complementos
                    },
                    modifier = modifier,
                )
            }
            PanelTipo.FAVORITOS -> PanelFavoritos(
                favoritos = favorites,
                fijado = fijado,
                onFijar = alFijar,
                onCerrar = alCerrar,
                onAbrir = { openUrl(it) },
                onEliminar = { fav ->
                    scope.launch(Dispatchers.IO) {
                        db.favoriteDao().delete(fav.id)
                        refreshData()
                    }
                },
                onAnadir = { overlay = Overlay.Star(null) },
                onPagina = {
                    refreshData()
                    overlay = Overlay.Favorites
                },
                modifier = modifier,
            )
            PanelTipo.DESCARGAS -> PanelDescargas(
                entries = downloads,
                fijado = fijado,
                onFijar = alFijar,
                onCerrar = alCerrar,
                onAbrirArchivo = { abrirDescarga(it) },
                onEliminar = { d ->
                    deleteDownloadedFile(context, d)
                    scope.launch(Dispatchers.IO) {
                        db.downloadDao().delete(d.id)
                        refreshData()
                    }
                },
                onCarpeta = { irACarpetaDescargas() },
                onPagina = {
                    overlay = null
                    abrirInterna(INTERNA_DESCARGAS)
                },
                onLimpiar = {
                    scope.launch(Dispatchers.IO) {
                        db.downloadDao().borrarDesde(0L)
                        refreshData()
                    }
                },
                modifier = modifier,
            )
            PanelTipo.HISTORIAL -> PanelHistorial(
                entries = history,
                cerradas = cerradas,
                fijado = fijado,
                onFijar = alFijar,
                onCerrar = alCerrar,
                onAbrir = { openUrl(it) },
                onRestaurar = { restaurarTab(it) },
                onEliminar = { e ->
                    scope.launch(Dispatchers.IO) {
                        db.historyDao().delete(e.id)
                        refreshData()
                    }
                },
                onPagina = {
                    overlay = null
                    abrirInterna(INTERNA_HISTORIAL)
                },
                onBorrarDatos = { overlay = Overlay.BorrarDatos },
                onLimpiar = {
                    scope.launch(Dispatchers.IO) {
                        db.historyDao().clear()
                        refreshData()
                    }
                },
                modifier = modifier,
            )
        }
    }

    val internaActiva = tituloInterna(activeTab.url)
    val showDashboard = internaActiva == "Inicio"
    val webActivo = activeTab.url.startsWith("http")
    // Lo que realmente se ve: sólo una página web puede esconder las barras.
    val chromeVisible = (chromeOpen || !webActivo) && !pantallaCompleta

    // Sistema en modo inmersivo mientras el vídeo ocupa la pantalla.
    DisposableEffect(pantallaCompleta) {
        val ventana = (context as? android.app.Activity)?.window
        if (ventana != null) {
            val control = androidx.core.view.WindowCompat.getInsetsController(ventana, ventana.decorView)
            if (pantallaCompleta) {
                control.systemBarsBehavior =
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                control.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            } else {
                control.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
    }

    // Cursor del mando: activo mientras se ve una página web a pantalla completa
    // y no hay nada modal ni el teclado de la barra de direcciones.
    // También con las barras a la vista: así se llega con el cursor a las pestañas
    // (y a su ×) sin maniobras con las flechas. Menú alterna el cursor.
    val densidadPx = androidx.compose.ui.platform.LocalDensity.current.density
    val punteroActivo = !editing && cursorEnabled
    SideEffect {
        Puntero.activo = punteroActivo
        Puntero.esWeb = webActivo && overlay == null
        Puntero.anchoPanel = if (panelFijo != null) PanelAncho.value * densidadPx else 0f
        // Con un vídeo a pantalla completa el cursor desaparece al quedar quieto.
        Puntero.ocultarEnReposo = pantallaCompleta
    }
    DisposableEffect(Unit) {
        Puntero.desplazar = { dx, dy -> webTabs[activeId]?.desplazar(dx, dy) }
        Puntero.ruedaWeb = { x, y, dx, dy -> webTabs[activeId]?.rueda(x, y, dx, dy) ?: false }
        // ↑ contra el borde de arriba con la página ya arriba: baja la barra.
        // Mantener OK sobre una zona vacía de la página: menú de la página. Sobre un
        // enlace, imagen o texto, Gecko ya abrió el suyo a los ~0,5 s y no se toca.
        Puntero.alMantener = {
            val enWeb = tabs.firstOrNull { it.id == activeId }?.url?.startsWith("http") == true
            if (enWeb && overlay == null && !editing && !chromeOpen) {
                webTabs[activeId]?.let { w ->
                    w.view.isFocusable = false
                    w.view.clearFocus()
                }
                overlay = Overlay.ContextMenu(ContextTarget())
            }
        }
        Puntero.alModoScroll = { activo ->
            toast(
                if (activo) "Modo desplazar: las flechas mueven la lista bajo el cursor · Menú para volver"
                else "Cursor normal",
            )
        }
        Puntero.alMenu = {
            if (!editing) {
                cursorEnabled = !cursorEnabled
                toast(
                    if (cursorEnabled) "Cursor activado" else "Cursor apagado · flechas = foco",
                )
                true
            } else {
                false
            }
        }
        onDispose {
            Puntero.activo = false
            Puntero.desplazar = { _, _ -> }
            Puntero.ruedaWeb = { _, _, _, _ -> false }
            Puntero.alMenu = { false }
            Puntero.alModoScroll = {}
            Puntero.alMantener = {}
        }
    }

    // Atrás: overlay → edición → barras (mostrar/ocultar) → vista interna → historia.
    // En la página de inicio no se captura: el sistema cierra la aplicación.
    var confirmarSalida by remember { mutableStateOf(false) }
    SideEffect {
        // Con el diálogo abierto, las flechas son de sus botones y el imán se apaga.
        Puntero.pausado = confirmarSalida
    }
    BackHandler(enabled = true) {
        when {
            confirmarSalida -> confirmarSalida = false
            // Vídeo a pantalla completa: Atrás lo devuelve a su recuadro.
            pantallaCompleta -> {
                webTabs[activeId]?.salirPantallaCompleta()
                pantallaCompleta = false
            }
            overlay != null -> closeOverlay()
            editing -> {
                if (sugsOpen && suggestions.isNotEmpty()) {
                    // Primero se cierra el desplegable, después la edición.
                    sugsOpen = false
                    fieldHasFocus = true
                    fieldFr.requestFocus()
                } else {
                    editing = false
                    barFr.requestFocus()
                }
            }
            // Página a pantalla completa: Atrás baja la barra de direcciones y
            // las pestañas, con el foco en la barra.
            webActivo && !chromeOpen -> {
                chromeOpen = true
                composeView.postDelayed({ barFr.requestFocus() }, 350)
            }
            // Barras a la vista: Atrás retrocede en el historial de la página y,
            // si ya no hay a dónde volver, pregunta si se quiere salir.
            webActivo -> {
                val w = webTabs[activeId]
                if (w != null && w.canGoBack) w.goBack() else confirmarSalida = true
            }
            internaActiva != null && !showDashboard -> closeTab(activeId)
            // Nada más que retroceder: se pregunta antes de cerrar la aplicación.
            else -> confirmarSalida = true
        }
    }

    // Paso 2 · las pestañas internas refrescan sus datos al entrar y se recuerda
    // la última página web vista (para «Usar esta página» de Configuración).
    LaunchedEffect(activeId) {
        if (internaActiva != null) refreshData() else if (activeTab.url.startsWith("http")) ultimaWeb = activeTab.url
    }

    // La página de inicio siempre muestra los favoritos al día (también al
    // volver a ella desde una pestaña web o tras cerrar un panel).
    LaunchedEffect(showDashboard) { if (showDashboard) refreshData() }

    /**
     * Entra en la barra de direcciones **sin borrar** lo que hay: el texto se
     * conserva y el cursor queda al final, para poder ampliar, corregir o
     * borrar una parte concreta con ←/→ y borrar.
     *
     * Marcar todo el texto —cómodo para pegar una dirección nueva— es una
     * acción aparte (mantener OK sobre la barra), porque la selección completa
     * impedía editar un trozo: la primera tecla borraba la dirección entera.
     */
    fun editarBarra(seleccionarTodo: Boolean) {
        // La dirección se lee del estado actual (pestañas y pestaña activa), no
        // de valores capturados al construir el gesto: el toque sobre la barra
        // llegaba con las variables de la PRIMERA composición —las de la página
        // de inicio—, así que el campo nacía vacío y la dirección parecía
        // borrarse sola.
        val urlActual = tabs.firstOrNull { it.id == activeId }?.url
        val actual = urlActual?.takeIf { tituloInterna(it) == null }.orEmpty()
        if (!editing) {
            addressValue = TextFieldValue(
                text = actual,
                selection = if (seleccionarTodo) TextRange(0, actual.length) else TextRange(actual.length),
            )
            sugsOpen = true
            editing = true
        } else {
            if (seleccionarTodo) {
                addressValue = TextFieldValue(addressValue.text, TextRange(0, addressValue.text.length))
            }
            fieldHasFocus = true
            fieldFr.requestFocus()
        }
    }

    /**
     * Elegir una sugerencia: una **dirección** se abre tal cual; una **búsqueda**
     * devuelve su texto a la barra (sin cambiar el motor elegido, que es el que
     * se usará al confirmar). Nunca se inventa una navegación: la entrada de
     * historial la añade la carga real de la página.
     */
    fun usarSugerencia(s: Sugerencia) {
        val consulta = s.consulta
        if (s.tipo == TipoSugerencia.BUSQUEDA && consulta != null) {
            addressValue = TextFieldValue(consulta, TextRange(consulta.length))
            sugsOpen = true
            fieldHasFocus = true
            fieldFr.requestFocus()
            Bitacora.add(
                LogKind.SISTEMA,
                "Sugerencia: búsqueda anterior «$consulta» recuperada en la barra · motor ${engine.name}",
            )
        } else {
            sugsOpen = false
            Bitacora.add(LogKind.SISTEMA, "Sugerencia: dirección abierta · ${s.url}")
            openUrl(s.url)
        }
    }

    Box(Modifier.fillMaxSize().background(Noche)) {
        Column(Modifier.fillMaxSize()) {
            // Pestañas + barra de direcciones forman un solo bloque que sube y
            // baja como una persiana: mientras está arriba no ocupa sitio ni
            // admite foco, y la página aprovecha toda la pantalla.
            val chromeFrac by animateFloatAsState(
                targetValue = if (chromeVisible) 1f else 0f,
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                label = "chrome",
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .zIndex(10f)
                    .clipToBounds()
                    .layout { medida, restricciones ->
                        val p = medida.measure(restricciones)
                        val alto = (p.height * chromeFrac).roundToInt()
                        layout(p.width, alto) { p.place(0, alto - p.height) }
                    }
                    .onSizeChanged { Puntero.altoBarras = it.height.toFloat() }
                    .focusProperties { canFocus = chromeVisible },
            ) {
            // ── Tira de pestañas pegada al borde superior: planas, separadas por una línea ──
            Column(Modifier.fillMaxWidth().background(Casco)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 3.dp, bottom = 3.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LazyRow(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items(tabs, key = { it.id }) { tab ->
                            // Páginas de una extensión (opciones, panel de control): llevan
                            // el nombre y el icono de la extensión en vez de un UUID.
                            val deExt = if (tab.url.startsWith("moz-extension://")) {
                                extsTabs.firstOrNull {
                                    val o = it.opciones
                                    o != null && hostOf(o) == hostOf(tab.url)
                                }
                            } else {
                                null
                            }
                            TabChip(
                                label = deExt?.nombre ?: tabLabel(tab),
                                iconoExt = deExt?.let { iconosTabs[it.id] },
                                url = tab.url,
                                active = tab.id == activeId,
                                onClick = {
                                    activeId = tab.id
                                    progress = 0
                                    revealChrome()
                                },
                                onLongClick = { closeTab(tab.id) },
                                onClose = { closeTab(tab.id) },
                                compact = false,
                            )
                        }
                        // «Pestaña nueva» pegada a la última pestaña, no tirada a la orilla.
                        item(key = "nueva") {
                            RemoteChip(
                                label = "",
                                icon = ToolIcon.NewTab,
                                small = true,
                                onClick = ::newTab,
                                onLongClick = ::newTab,
                            )
                        }
                    }
                }
                // Único ornamento estructural de la tira: un filete de 1 px que
                // separa las pestañas de la barra de direcciones.
                Box(Modifier.fillMaxWidth().height(1.dp).background(Azul.copy(alpha = 0.22f)))
            }

            // ── Barra de direcciones: ←, ↻, ⌂, URL con ★ pegada al borde, ☰ y ⏻ al final ──
            val chromeH = BarraAlto
            Box(
                Modifier
                    .fillMaxWidth()
                    .zIndex(10f)
                    .onPreviewKeyEvent { ev ->
                        // ↓ dentro del campo abre el primer sugerido.
                        if (
                            editing && fieldHasFocus && sugsOpen && suggestions.isNotEmpty() &&
                            ev.key == Key.DirectionDown && ev.type == KeyEventType.KeyDown
                        ) {
                            fieldHasFocus = false
                            sugFr.requestFocus()
                            true
                        } else false
                    },
            ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(chromeH)
                    .background(Casco)
                    .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 6.dp)
                    .onPreviewKeyEvent { ev ->
                        // ↓ desde la barra → a la página (está justo debajo): las
                        // barras suben y el cursor vuelve a la página.
                        if (ev.key == Key.DirectionDown && ev.type == KeyEventType.KeyDown) {
                            val w = webTabs[activeId]
                            if (w != null && activeTab.url.startsWith("http")) {
                                chromeOpen = false
                                w.view.requestFocus()
                                true
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RemoteChip(
                    label = "",
                    icon = ToolIcon.Back,
                    small = true,
                    enabled = chromeVisible,
                    onClick = {
                        webTabs[activeId]?.takeIf { it.canGoBack }?.goBack()
                    },
                )
                Spacer(Modifier.width(4.dp))
                RemoteChip(
                    label = "",
                    icon = ToolIcon.Reload,
                    small = true,
                    enabled = chromeVisible,
                    onClick = {
                        when {
                            showDashboard -> goHome()
                            // Vista interna: recargar = volver a leer sus datos.
                            internaActiva != null -> refreshData()
                            else -> webTabs[activeId]?.reload()
                        }
                    },
                )
                Spacer(Modifier.width(4.dp))
                RemoteChip(label = "", icon = ToolIcon.Home, small = true, enabled = chromeVisible, onClick = ::goHome)
                Spacer(Modifier.width(8.dp))

                val starred = !showDashboard && favorites.any { it.url == activeTab.url }
                AddressBar(
                    modifier = Modifier.weight(1f),
                    url = activeTab.url,
                    editing = editing,
                    enabled = chromeVisible,
                    starred = starred,
                    value = addressValue,
                    onValueChange = { addressValue = it },
                    // Tocar la barra conserva lo que hay y pone el cursor al
                    // final; mantener OK marca todo el texto para sustituirlo.
                    onStartEdit = { editarBarra(seleccionarTodo = true) },
                    onSelectAll = { editarBarra(seleccionarTodo = true) },
                    onStar = {
                        if (!showDashboard && activeTab.url.startsWith("http")) {
                            val existing = favorites.find { it.url == activeTab.url }
                            overlay = Overlay.Star(existing)
                        }
                    },
                    onSubmit = { submit(it) },
                    focusRequester = barFr,
                    fieldRequester = fieldFr,
                )
                Spacer(Modifier.width(6.dp))

                // Paso 7 · un chip POR EXTENSIÓN (ya no un icono genérico que
                // las agrupe): el icono real que entrega el motor, o la inicial
                // de su nombre si no lo da, más su contador cuando lo informa.
                // Pulsar abre SU interfaz propia (la acción de barra de la
                // extensión); mantener abre su panel individual. Si no caben
                // todas, la última es «＋N», que lleva a Complementos: ninguna
                // extensión pierde el acceso ni sus controles.
                val accs by GeckoMotor.acciones.collectAsState()
                val exts by GeckoMotor.extensiones.collectAsState()
                val bitmaps by GeckoMotor.iconos.collectAsState()
                // Barra «infinita»: todos los accesos y TODAS las extensiones en una
                // tira que se desplaza (ya no hay «＋N»). El resto de la barra
                // (←, ↻, ⌂, dirección, ☰, ⏻) se queda fijo.
                Row(
                    Modifier
                        .widthIn(max = 520.dp)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                // Fase 9 · buscar pestañas.
                RemoteChip(
                    label = "",
                    icon = ToolIcon.Search,
                    small = true,
                    enabled = chromeVisible,
                    onClick = { overlay = Overlay.Buscar },
                )
                Spacer(Modifier.width(4.dp))
                // Un solo botón con todos los complementos; sólo los anclados
                // tienen además su propio botón en la barra.
                if (tbExt) {
                    RemoteChip(
                        label = "",
                        icon = ToolIcon.Puzzle,
                        small = true,
                        enabled = chromeVisible,
                        openState = (overlay as? Overlay.Panel)?.tipo == PanelTipo.COMPLEMENTOS,
                        onClick = { alternarPanel(PanelTipo.COMPLEMENTOS) },
                        onLongClick = {
                            GeckoMotor.arrancar(context)
                            GeckoMotor.refrescar()
                            overlay = Overlay.Complementos
                        },
                    )
                    Spacer(Modifier.width(4.dp))
                }
                exts.filter { it.id != com.antena.tv.gecko.EXT_INTERNA && esAnclada(it) }.forEach { ext ->
                    val objeto = bitmaps[ext.id]
                    val conBadge = accs.firstOrNull { it.extId == ext.id && it.badge != null }
                    // «Interfaz propia» = la extensión declara acción de barra Y
                    // hay pestaña con sesión a la que contarla. En el panel de
                    // inicio no la hay: entonces el chip abre el panel propio
                    // (información real) en vez de provocar un clic que Gecko
                    // no puede resolver.
                    val tieneInterfaz = GeckoMotor.puedeAbrirInterfaz(ext.id)
                    RemoteChip(
                        label = conBadge?.badge.orEmpty(),
                        imagen = objeto?.asImageBitmap(),
                        glifo = if (objeto == null) inicialExtension(ext.nombre) else null,
                        accent = conBadge != null,
                        tenue = !ext.activa,
                        small = true,
                        enabled = chromeVisible,
                        onClick = {
                            Puntero.ultimaAccion = android.os.SystemClock.uptimeMillis()
                            // Con interfaz disponible, el clic va a la extensión
                            // (su popup/panel). Si no lo está, se muestra el panel
                            // propio: información real, nunca un clic sin efecto.
                            if (!(tieneInterfaz && GeckoMotor.clicAccion(ext.id))) {
                                overlay = Overlay.Extension(ext.id)
                            }
                        },
                        onLongClick = { overlay = Overlay.Extension(ext.id) },
                    )
                    Spacer(Modifier.width(4.dp))
                }
                // Resto de accesos, dentro de la misma tira desplazable.
                if (tbFav) {
                    RemoteChip(
                        label = "",
                        icon = ToolIcon.StarOutline,
                        small = true,
                        enabled = chromeVisible,
                        onClick = { alternarPanel(PanelTipo.FAVORITOS) },
                        onLongClick = { refreshData(); overlay = Overlay.Favorites },
                    )
                    Spacer(Modifier.width(4.dp))
                }
                if (tbHist) {
                    RemoteChip(
                        label = "",
                        icon = ToolIcon.Clock,
                        small = true,
                        enabled = chromeVisible,
                        onClick = { alternarPanel(PanelTipo.HISTORIAL) },
                        onLongClick = { abrirInterna(INTERNA_HISTORIAL) },
                    )
                    Spacer(Modifier.width(4.dp))
                }
                if (tbDown) {
                    RemoteChip(
                        label = "",
                        icon = ToolIcon.Download,
                        small = true,
                        enabled = chromeVisible,
                        onClick = { alternarPanel(PanelTipo.DESCARGAS) },
                        onLongClick = { abrirInterna(INTERNA_DESCARGAS) },
                    )
                }
                }
                Spacer(Modifier.width(6.dp))
                // Menú desplegable (siempre al final de la fila de la barra): solo el icono.
                // Abierto → píldora blanca (sin ámbar) que indica que el menú está desplegado.
                Box(Modifier.focusRequester(menuFr)) {
                    RemoteChip(
                        label = "",
                        icon = ToolIcon.Menu,
                        openState = overlay is Overlay.Menu,
                        small = true,
                        enabled = chromeVisible,
                        onClick = { overlay = if (overlay is Overlay.Menu) null else Overlay.Menu },
                    )
                }
            }

                // Desplegable de sugerencias bajo el campo de dirección: la
                // barra y el campo siguen a la vista, la lista no tapa la
                // pantalla y se recorre con el mando (↑/↓ desplazan, OK abre).
                if (editing && sugsOpen && suggestions.isNotEmpty()) {
                    val sugScroll = remember(addressValue.text) { ScrollState(0) }
                    Column(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 176.dp, top = chromeH)
                            .width(700.dp)
                            .heightIn(max = 440.dp)
                            .verticalScroll(sugScroll)
                            .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                            .background(Noche),
                    ) {
                        suggestions.forEachIndexed { i, s ->
                            SuggestionRow(
                                sugerencia = s,
                                isFirst = i == 0,
                                focusFirst = if (i == 0) sugFr else null,
                                onUp = {
                                    fieldHasFocus = true
                                    fieldFr.requestFocus()
                                },
                                onClick = { usarSugerencia(s) },
                            )
                        }
                    }
                }
            }
            } // ← fin del bloque «persiana» (pestañas + barra)

            // ── Progreso ──
            if (progress in 1..99) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(Noche)) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress / 100f)
                            .height(3.dp)
                            .background(Ambar),
                    )
                }
            }

            // ── Área de la página (+ panel rápido fijado a la derecha) ──
            Row(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (internaActiva != null) {
                    // Paso 2 · vistas internas como pestañas: mismo mecanismo que la
                    // página de inicio (Compose dentro de la pestaña) en vez de un
                    // overlay encima del navegador. `SaveableStateProvider` guarda el
                    // estado propio de cada pestaña (filtro y scroll del historial…)
                    // para que siga igual al cambiar a otra pestaña y volver.
                    tabStateHolder.SaveableStateProvider(activeId) {
                        when (activeTab.url) {
                            INTERNA_HISTORIAL -> HistoryPage(
                                entries = history,
                                filter = historyFilter,
                                onFilter = { historyFilter = it },
                                // La página se abre en una pestaña web nueva: la del
                                // historial no se destruye ni pierde la posición.
                                onOpen = { abrirDesdeVistaInterna(it) },
                                onDelete = { e ->
                                    scope.launch(Dispatchers.IO) {
                                        db.historyDao().delete(e.id)
                                        refreshData()
                                    }
                                },
                                onClear = {
                                    scope.launch(Dispatchers.IO) {
                                        db.historyDao().clear()
                                        refreshData()
                                    }
                                },
                                onBack = { closeTab(activeId) },
                                backLabel = "✕ Cerrar",
                            )
                            INTERNA_DESCARGAS -> DownloadsPage(
                                entries = downloads,
                                onOpenFile = { abrirDescarga(it) },
                                onDelete = { d ->
                                    deleteDownloadedFile(context, d)
                                    scope.launch(Dispatchers.IO) {
                                        db.downloadDao().delete(d.id)
                                        refreshData()
                                    }
                                },
                                onOpenFolder = { irACarpetaDescargas() },
                                onBack = { closeTab(activeId) },
                                backLabel = "✕ Cerrar",
                            )
                            INTERNA_ACERCA -> AcercaPage(
                                version = version ?: "—",
                                onClose = { closeTab(activeId) },
                                onAbrir = { u -> abrirDesdeVistaInterna(u) },
                            )
                            INTERNA_CLAVES -> ClavesPage(onBack = { closeTab(activeId) })
                            INTERNA_ARCHIVOS -> ArchivosPage(
                                onClose = { closeTab(activeId) },
                                onReproducir = { reproductorArchivo = it },
                                carpetaInicial = carpetaArchivos,
                            )
                            INTERNA_CONFIG -> ConfiguracionPage(
                                engines = engines,
                                engineId = engineId,
                                onEngine = { id ->
                                    engineId = id
                                    scope.launch(Dispatchers.IO) {
                                        db.settingDao().put(Setting(AntenaDatabase.KEY_SEARCH_ENGINE, id))
                                    }
                                },
                                homepage = homepage,
                                paginaActual = ultimaWeb,
                                onHomepage = { url ->
                                    homepage = url
                                    scope.launch(Dispatchers.IO) {
                                        db.settingDao().put(Setting(AntenaDatabase.KEY_HOMEPAGE, url))
                                    }
                                },
                                showFavorites = tbFav,
                                showHistory = tbHist,
                                showDownloads = tbDown,
                                showFloating = tbFloat,
                                onToggle = ::setTb,
                                onClose = { closeTab(activeId) },
                                showComplementos = tbExt,
                                acentoId = com.antena.tv.ui.TemaApp.acentoId,
                                onAcento = ::elegirAcento,
                                velocidadId = velocidadId,
                                onVelocidad = ::elegirVelocidad,
                                modoPaginaId = modoPaginaId,
                                onModoPagina = ::elegirModoPagina,
                                onMonitorCambio = ::guardarMonitor,
                                onPrecisionCambio = ::guardarTeclaPrecision,
                                restaurarPestanas = restaurarPestanas,
                                onRestaurarPestanas = ::elegirRestaurar,
                                precalentar = precalentarMotor,
                                onPrecalentar = ::elegirPrecalentar,
                            )
                            else -> DashboardPage(
                                favorites = favorites,
                                onOpen = { url -> openUrl(url) },
                                onDelete = { fav ->
                                    scope.launch(Dispatchers.IO) {
                                        db.favoriteDao().delete(fav.id)
                                        refreshData()
                                    }
                                },
                                onAdd = { overlay = Overlay.Star(null) },
                                engineName = engine.name,
                                onSearch = { q -> submit(q) },
                            )
                        }
                    }
                } else {
                    key(activeId, recarga) {
                        if (activeId in pausadas) {
                            PaginaPausada(
                                url = activeTab.url,
                                onReintentar = {
                                    pausadas = pausadas - activeId
                                    descartadas += activeId
                                    recarga++
                                },
                                onCerrar = { closeTab(activeId) },
                            )
                        } else if (activeTab.url.isNotEmpty()) {
                            // Motor único: toda pestaña vive sobre GeckoView.
                            AndroidView(
                                factory = {
                                    val w = webTab(activeId)
                                    // Pestaña que se descartó por memoria: se vuelve a cargar.
                                    if (descartadas.remove(activeId)) {
                                        progress = 1
                                        w.load(activeTab.url)
                                    }
                                    // Tras caer el motor la vista vieja puede seguir pegada a su
                                    // padre: se despega antes de entregarla (era un cierre de la app).
                                    (w.view.parent as? android.view.ViewGroup)?.removeView(w.view)
                                    w.view
                                },
                                update = { gv ->
                                    // Panel modal abierto: la página no puede robar el foco
                                    // del D-pad (si no, el panel queda inalcanzable).
                                    val libre = overlay == null
                                    if (gv.isFocusable != libre) {
                                        gv.isFocusable = libre
                                        if (!libre) gv.clearFocus()
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    if (activeTab.url.isEmpty()) {
                        Column(
                            Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("Escribe una dirección arriba", style = AntenaType.panel, color = Fosforo)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "o pulsa ⌂ para ir al inicio",
                                style = AntenaType.meta,
                                color = Azul,
                            )
                        }
                    }
                }

                // Asa para volver a bajar las barras con el dedo, el ratón o el cursor
                // (en una tablet no hay «Atrás» a mano y la página quedaba sin salida).
                // Con cursor (TV) las asas sólo aparecen al acercarse al borde y se retiran
                // solas; sin cursor en uso (tablet, táctil) la de arriba queda tenue a la vista.
                val usoCursor = Puntero.activo && Puntero.movimiento != 0L
                val yRel = Puntero.pos.y - Puntero.origenY
                val altoPx = Puntero.altoVentana
                val cercaArriba = yRel < 72f * densidadPx
                val cercaAbajo = altoPx > 0f && yRel > altoPx - 96f * densidadPx
                val verlo = webActivo && !pantallaCompleta
                ManijaBarras(
                    visible = verlo && !chromeVisible && (!usoCursor || cercaArriba || Puntero.indicador == 1) && !Puntero.modoScroll,
                    crecida = Puntero.indicador == 1,
                    onClick = {
                        chromeOpen = true
                        composeView.postDelayed({ barFr.requestFocus() }, 350)
                    },
                    modifier = Modifier.align(Alignment.TopCenter).zIndex(11f),
                )
                // Abajo: una pastilla que sube al acercar el cursor; tocarla baja una pantalla.
                ManijaAbajo(
                    visible = verlo && usoCursor && (cercaAbajo || Puntero.indicador == 2) && overlay == null && !Puntero.modoScroll,
                    crecida = Puntero.indicador == 2,
                    onClick = { webTabs[activeId]?.desplazar(0f, altoPx * 0.85f) },
                    modifier = Modifier.align(Alignment.BottomCenter).zIndex(11f),
                )

                // Botón flotante "ir arriba" (último hermano + zIndex: por encima de la página).
                // Aparece con la barra plegada; se desactiva en Menú → Barra de herramientas.
                if (tbFloat && scrolled && !chromeVisible && webActivo && !pantallaCompleta) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .zIndex(10f)
                            .padding(end = 28.dp, bottom = 28.dp),
                    ) {
                        RemoteChip(
                            label = "",
                            icon = ToolIcon.ArrowUp,
                            small = true,
                            ghost = true,
                            onClick = { webTabs[activeId]?.scrollToTop() },
                        )
                    }
                }
            }
            // Panel fijado: columna propia, la página se estrecha en vez de taparse.
            val fijo = panelFijo
            if (fijo != null && !pantallaCompleta) {
                contenidoPanel(fijo, true, Modifier.width(PanelAncho).fillMaxHeight())
            }
            }
        }

        // ── Overlays ──
        when (val ov = overlay) {
            is Overlay.Panel -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures { closeOverlay() } },
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 104.dp, end = 16.dp)
                        .pointerInput(Unit) { detectTapGestures { } },
                ) {
                    val altoMax = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp - 124.dp
                    contenidoPanel(ov.tipo, false, Modifier.width(PanelAncho).heightIn(max = altoMax))
                }
            }
            is Overlay.BorrarDatos -> BorrarDatosDialogo(
                onBorrar = ::borrarDatos,
                onCancelar = ::closeOverlay,
            )
            is Overlay.Menu -> {
                // Sin oscurecer la página: solo un velo que cierra al tocar fuera.
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures { closeOverlay() } },
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 104.dp, end = 16.dp)
                        .pointerInput(Unit) { detectTapGestures { } },
                ) {
                    MenuDropdown(
                        engineName = engine.name,
                        onFavorites = { refreshData(); overlay = Overlay.Favorites },
                        // Paso 2 · historial, descargas y configuración son pestañas:
                        // se abren (o se traen al frente) sin tapar la página actual.
                        onHistory = { abrirInterna(INTERNA_HISTORIAL) },
                        onDownloads = { abrirInterna(INTERNA_DESCARGAS) },
                        onComplementos = {
                            GeckoMotor.arrancar(context)
                            GeckoMotor.refrescar()
                            overlay = Overlay.Complementos
                        },
                        onConfig = { abrirInterna(INTERNA_CONFIG) },
                        onAcerca = { abrirInterna(INTERNA_ACERCA) },
                        onClaves = { abrirInterna(INTERNA_CLAVES) },
                        onArchivos = { carpetaArchivos = null; abrirInterna(INTERNA_ARCHIVOS) },
                        onSystem = { overlay = Overlay.System },
                        onDiagnostics = { overlay = Overlay.Diagnostics },
                        onBitacora = {
                            logEntries = Bitacora.entries()
                            overlay = Overlay.Bitacora
                        },
                        onSalir = { confirmarSalida = true; closeOverlay() },
                        version = version ?: "—",
                    )
                }
            }
            is Overlay.Extension -> {
                // Panel individual: identidad y controles de ESA extensión. El
                // estado que muestra es el que informa el motor (`list()`), y el
                // aviso de error, si lo hay, se ve aquí sin esconderse.
                val exts by GeckoMotor.extensiones.collectAsState()
                val accs by GeckoMotor.acciones.collectAsState()
                val bitmaps by GeckoMotor.iconos.collectAsState()
                val av by GeckoMotor.aviso.collectAsState()
                val ext = exts.firstOrNull { it.id == ov.id }
                if (ext == null) {
                    // La extensión ya no está (desinstalada desde Complementos):
                    // el panel se cierra en vez de quedarse mostrando algo falso.
                    LaunchedEffect(ov.id) { closeOverlay() }
                } else {
                    ExtensionCard(
                        ext = ext,
                        imagen = bitmaps[ext.id],
                        badge = accs.firstOrNull { it.extId == ext.id }?.badge,
                        tieneInterfaz = GeckoMotor.puedeAbrirInterfaz(ext.id),
                        aviso = av,
                        onRetryFocus = { webTabs[activeId]?.view?.clearFocus() },
                        onAccion = { accion ->
                            when (accion) {
                                // Su interfaz propia (popup de la extensión).
                                AccionExtension.AbrirInterfaz -> {
                                    if (GeckoMotor.clicAccion(ext.id)) {
                                        closeOverlay()
                                    } else {
                                        toast("${ext.nombre} no puede abrir su panel ahora")
                                    }
                                }
                                // Página de opciones: GeckoView no la abre por su
                                // cuenta (su openOptionsPage no es público), así
                                // que se carga la URL que publica la extensión.
                                AccionExtension.Opciones -> {
                                    closeOverlay()
                                    urlSegura(ext.opciones)?.let { u ->
                                        Bitacora.add(LogKind.SISTEMA, "Opciones de ${ext.nombre} · $u")
                                        openInNewTab(u, background = false)
                                    }
                                }
                                AccionExtension.SitioAutor -> {
                                    closeOverlay()
                                    urlSegura(ext.web)?.let { openInNewTab(it, background = false) }
                                }
                                AccionExtension.FichaAmo -> {
                                    closeOverlay()
                                    urlSegura(ext.ficha)?.let { openInNewTab(it, background = false) }
                                }
                                // Activar/desactivar: la operación la confirma el
                                // motor y sólo entonces cambia el estado visible.
                                AccionExtension.Alternar -> {
                                    GeckoMotor.alternar(ext.id)
                                    toast(
                                        if (ext.activa) {
                                            "Desactivando ${ext.nombre}…"
                                        } else {
                                            "Activando ${ext.nombre}…"
                                        },
                                    )
                                }
                                AccionExtension.Administrar -> {
                                    GeckoMotor.arrancar(context)
                                    GeckoMotor.refrescar()
                                    overlay = Overlay.Complementos
                                }
                            }
                        },
                        onClose = ::closeOverlay,
                    )
                }
            }
            is Overlay.Complementos -> {
                PageScaffold(title = "", onClose = ::closeOverlay) {
                    val exts by GeckoMotor.extensiones.collectAsState()
                    val av by GeckoMotor.aviso.collectAsState()
                    val bitmaps by GeckoMotor.iconos.collectAsState()
                    ComplementosPage(
                        extensiones = exts,
                        iconos = bitmaps,
                        aviso = av,
                        onAlternar = { GeckoMotor.alternar(it) },
                        onDesinstalar = { GeckoMotor.desinstalar(it.id) },
                        onInstalar = { GeckoMotor.instalar(it) },
                        // La tienda se abre en una pestaña normal: el índice general
                        // o la ficha del addon que el usuario quiera revisar.
                        onAbrirTienda = { url ->
                            overlay = null
                            openUrl(url)
                        },
                        onPestañaGecko = {
                            overlay = null
                            nuevaPestañaGecko()
                        },
                        ancladas = ::esAnclada,
                        onAnclar = ::anclar,
                        onAbrirExt = { overlay = Overlay.Extension(it.id) },
                        onClose = ::closeOverlay,
                    )
                }
            }
            is Overlay.System -> {
                PageScaffold(title = "Información del sistema", onClose = ::closeOverlay) {
                    // Sistema · Diagnóstico · Bitácora en un solo lugar.
                    var seccion by remember { mutableStateOf(0) }
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier.padding(start = 48.dp, top = 8.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf("Sistema", "Diagnóstico", "Bitácora").forEachIndexed { i, txt ->
                                RemoteChip(
                                    label = txt,
                                    small = true,
                                    accent = i == seccion,
                                    onClick = {
                                        seccion = i
                                        if (i == 2) logEntries = Bitacora.entries()
                                    },
                                )
                            }
                        }
                        Box(
                            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 48.dp),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            Box(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                            when (seccion) {
                                0 -> ProbeScreen()
                                1 -> DiagnosticoPage(
                                    cifras = {
                                        CifrasPestanas(
                                            vivas = webTabs.size,
                                            descartadas = descartadas.size,
                                            total = tabs.size,
                                        )
                                    },
                                    version = version ?: "?",
                                    onLiberar = {
                                        val n = descartarFondo(1, "pedido del usuario")
                                        if (n > 0) toast("Memoria liberada: $n pestañas de fondo")
                                        n
                                    },
                                )
                                else -> BitacoraPage(
                                    entries = logEntries,
                                    onExportar = ::exportarBitacora,
                                    onBack = { seccion = 0 },
                                    onClear = {
                                        Bitacora.clear()
                                        logEntries = Bitacora.entries()
                                    },
                                    onCopy = { e ->
                                        copyTextToClipboard(context, e.completa())
                                        toast("Entrada copiada")
                                    },
                                )
                            }
                            }
                        }
                    }
                }
            }
            is Overlay.Diagnostics -> {
                PageScaffold(title = "Diagnóstico", onClose = ::closeOverlay) {
                    CursorScreen()
                }
            }
            is Overlay.Bitacora -> {
                PageScaffold(title = "", onClose = ::closeOverlay) {
                    BitacoraPage(
                        entries = logEntries,
                        onBack = ::closeOverlay,
                        onClear = {
                            Bitacora.clear()
                            logEntries = Bitacora.entries()
                        },
                        onCopy = { e ->
                            copyTextToClipboard(context, e.completa())
                            toast("Entrada copiada")
                        },
                    )
                }
            }
            is Overlay.Buscar -> {
                PageScaffold(title = "", onClose = ::closeOverlay) {
                    BuscarTabsPage(
                        abiertas = tabs,
                        activaId = activeId,
                        cerradas = cerradas,
                        onAbrir = { irATab(it) },
                        onRestaurar = { restaurarTab(it) },
                        onBack = ::closeOverlay,
                    )
                }
            }
            is Overlay.Favorites -> {
                PageScaffold(title = "", onClose = ::closeOverlay) {
                    FavoritesPage(
                        favorites = favorites,
                        folders = folders,
                        selectedFolder = favFolderSel,
                        onSelectFolder = { favFolderSel = it },
                        onOpen = { openUrl(it) },
                        onDelete = { fav ->
                            scope.launch(Dispatchers.IO) {
                                db.favoriteDao().delete(fav.id)
                                refreshData()
                            }
                        },
                        onNewFolder = {
                            scope.launch(Dispatchers.IO) {
                                db.folderDao().upsert(Folder(UUID.randomUUID().toString(), "Carpeta nueva"))
                                folderVersion++
                                refreshData()
                            }
                        },
                        onDeleteFolder = { f ->
                            scope.launch(Dispatchers.IO) {
                                db.favoriteDao().detachFolder(f.id)
                                db.folderDao().delete(f.id)
                                refreshData()
                            }
                            if (favFolderSel == f.id) favFolderSel = null
                        },
                        onBack = ::closeOverlay,
                    )
                }
            }
            is Overlay.Star -> {
                // Desde la página de inicio no hay página activa que precargar:
                // el usuario escribe nombre y dirección a mano.
                val desdeInicio = activeTab.url.startsWith("cocosurf://")
                StarModal(
                    initialName = if (desdeInicio) "" else activeTab.title,
                    initialUrl = if (desdeInicio) "" else activeTab.url,
                    folders = folders,
                    existing = ov.existing,
                    foldersVersion = folderVersion,
                    onSave = { name, url, folderId ->
                        closeOverlay()
                        // «example.com» guardado a mano → https://example.com.
                        val limpia = url.trim().let { u ->
                            if (u.contains("://") || u.contains(" ")) u else "https://$u"
                        }
                        // Refresco DESPUÉS del insert: el favorito aparece ya guardado.
                        scope.launch(Dispatchers.IO) {
                            db.favoriteDao().upsert(
                                Favorite(UUID.randomUUID().toString(), name.trim(), limpia, folderId),
                            )
                            refreshData()
                        }
                    },
                    onDelete = {
                        closeOverlay()
                        ov.existing?.let { fav ->
                            scope.launch(Dispatchers.IO) {
                                db.favoriteDao().delete(fav.id)
                                refreshData()
                            }
                        }
                    },
                    onCreateFolder = { name ->
                        scope.launch(Dispatchers.IO) {
                            db.folderDao().upsert(Folder(UUID.randomUUID().toString(), name))
                            folderVersion++
                        }
                    },
                    onClose = ::closeOverlay,
                )
            }
            is Overlay.Pdf -> {
                PageScaffold(title = ov.entry.title.take(48), onClose = ::closeOverlay) {
                    PdfViewerScreen(entry = ov.entry, context = context)
                }
            }
            is Overlay.Media -> {
                PageScaffold(title = "Reproducir", onClose = ::closeOverlay) {
                    MediaPlayerScreen(url = ov.url)
                }
            }
            is Overlay.ContextMenu -> {
                val target = ov.target
                // Al cerrar, el foco regresa a la página (se quitó al abrir el menú).
                val closeCtx: () -> Unit = { closeOverlay() }
                // Cierre SIN acción (BACK / toque fuera): la selección se retira,
                // como al tocar fuera en cualquier navegador. Así no quedan
                // tiradores huérfanos y una pulsación nueva sobre el mismo
                // texto vuelve a abrir el menú.
                val cancelCtx: () -> Unit = {
                    if (target.texto != null) {
                        runCatching { webTabs[activeId]?.seleccion?.unselect() }
                    }
                    closeOverlay()
                }
                ContextMenuCard(
                    target = target,
                    pageUrl = activeTab.url,
                    puedeAtras = webTabs[activeId]?.canGoBack == true,
                    puedeAdelante = webTabs[activeId]?.canGoForward == true,
                    onRetryFocus = { webTabs[activeId]?.view?.clearFocus() },
                    onAccion = { accion ->
                        val w = webTabs[activeId]
                        when (accion) {
                            // Enlace ───────────────────────────────────────
                            AccionContexto.Abrir -> {
                                closeCtx()
                                target.link?.let { u -> if (!handleLink(u)) openUrl(u) }
                            }
                            AccionContexto.AbrirEnPestana -> {
                                closeCtx()
                                target.link?.let { openInNewTab(it, background = false) }
                            }
                            AccionContexto.AbrirSegundoPlano -> {
                                closeCtx()
                                target.link?.let { openInNewTab(it, background = true) }
                            }
                            AccionContexto.CopiarVinculo -> {
                                target.link?.let { copyTextToClipboard(context, it) }
                                toast("Vínculo copiado")
                                closeCtx()
                            }
                            AccionContexto.CompartirVinculo -> {
                                closeCtx()
                                compartirTexto(context, target.link ?: "")
                            }
                            AccionContexto.GuardarVinculo -> {
                                target.link?.let { u ->
                                    startSystemDownload(context, u, null, null) { entry ->
                                        scope.launch(Dispatchers.IO) { db.downloadDao().insert(entry) }
                                    }
                                    toast("Guardando vínculo…")
                                }
                                closeCtx()
                            }
                            // Imagen ───────────────────────────────────────
                            AccionContexto.GuardarImagen -> {
                                target.img?.let { u ->
                                    val ext = u.substringAfterLast('.', "")
                                        .substringBefore('?').substringBefore('&').lowercase()
                                    val mime = android.webkit.MimeTypeMap.getSingleton()
                                        .getMimeTypeFromExtension(ext) ?: "image/*"
                                    startSystemDownload(context, u, mime, null) { entry ->
                                        scope.launch(Dispatchers.IO) { db.downloadDao().insert(entry) }
                                    }
                                    toast("Guardando imagen…")
                                }
                                closeCtx()
                            }
                            AccionContexto.UsarComoFondo -> {
                                val u = target.img
                                closeCtx()
                                if (u != null) {
                                    scope.launch {
                                        toast("Descargando imagen…")
                                        val ok = FondoInicio.usarUrl(context, u)
                                        toast(if (ok) "Listo: es el fondo de la página de inicio" else "No se pudo usar esa imagen")
                                    }
                                }
                            }
                            AccionContexto.CopiarImagen -> {
                                val u = target.img
                                closeCtx()
                                if (u != null) {
                                    scope.launch {
                                        val ok = copyImageToClipboard(context, u)
                                        toast(if (ok) "Imagen copiada" else "No se pudo copiar la imagen")
                                    }
                                }
                            }
                            AccionContexto.CopiarVinculoImagen -> {
                                target.img?.let { copyTextToClipboard(context, it) }
                                toast("Vínculo de la imagen copiado")
                                closeCtx()
                            }
                            AccionContexto.BusquedaVisual -> {
                                val u = target.img
                                closeCtx()
                                if (u != null) {
                                    openInNewTab(
                                        "https://lens.google.com/uploadbyurl?url=" + android.net.Uri.encode(u),
                                        background = false,
                                    )
                                }
                            }
                            // Texto seleccionado ──────────────────────────
                            AccionContexto.CopiarSeleccion -> {
                                w?.seleccion?.copy()
                                toast("Texto copiado")
                                closeCtx()
                            }
                            AccionContexto.BuscarSeleccion -> {
                                val texto = target.texto
                                closeCtx()
                                if (texto != null) {
                                    // El motor configurado, sin tocar la configuración.
                                    val url = urlDeBusqueda(texto, engine.template)
                                        ?: resolveInput(texto, engine.template)
                                    if (url.isNotEmpty()) openUrl(url)
                                }
                            }
                            AccionContexto.CompartirSeleccion -> {
                                closeCtx()
                                compartirTexto(context, target.texto ?: "")
                            }
                            AccionContexto.SeleccionarTexto -> closeCtx()
                            // Página ───────────────────────────────────────
                            AccionContexto.CopiarPagina -> {
                                copyTextToClipboard(context, activeTab.url)
                                toast("Dirección copiada")
                                closeCtx()
                            }
                            AccionContexto.Atras -> {
                                closeCtx()
                                w?.goBack()
                            }
                            AccionContexto.Adelante -> {
                                closeCtx()
                                w?.goForward()
                            }
                            AccionContexto.Actualizar -> {
                                closeCtx()
                                w?.reload()
                            }
                            // Traducción de la página con el traductor web de Google.
                            AccionContexto.TraducirPagina -> {
                                closeCtx()
                                openUrl(
                                    "https://translate.google.com/translate?sl=auto&tl=es&u=" +
                                        android.net.Uri.encode(activeTab.url),
                                )
                            }
                            AccionContexto.GuardarPdf -> {
                                closeCtx()
                                pdfDePagina(imprimir = false)
                            }
                            AccionContexto.Imprimir -> {
                                closeCtx()
                                pdfDePagina(imprimir = true)
                            }
                            AccionContexto.NuevaPestana -> {
                                closeCtx()
                                newTab()
                            }
                        }
                    },
                    onClose = cancelCtx,
                )
            }
            null -> Unit
        }

        if (confirmarSalida) {
            Box(Modifier.fillMaxSize().zIndex(40f)) {
                SalirDialogo(
                    onSalir = { salirDeLaApp(context) },
                    onCancelar = { confirmarSalida = false },
                )
            }
        }

        reproductorArchivo?.let { f ->
            Box(Modifier.fillMaxSize().zIndex(60f)) { ReproductorLocal(f) { reproductorArchivo = null } }
        }

        // Contraseñas: ofrecer guardar o elegir (tarjeta bajo la barra, arriba al centro).
        claveGuardar?.let { ClaveGuardarCard(it, Modifier.align(Alignment.TopCenter).padding(top = 76.dp).zIndex(25f)) }
        claveElegir?.let { ClaveElegirCard(it, Modifier.align(Alignment.TopCenter).padding(top = 76.dp).zIndex(25f)) }

        // Avisos del motor (instalar/quitar complementos): visibles desde cualquier pantalla.
        AvisoFlotante(
            texto = avisoVisible,
            modifier = Modifier.align(Alignment.BottomCenter).zIndex(30f),
        )

        // Monitor de recursos (semitransparente, en la esquina elegida).
        if (MonitorConfig.visible) {
            val esquina = when (MonitorConfig.posicion) {
                "sup_izq" -> Alignment.TopStart
                "inf_izq" -> Alignment.BottomStart
                "inf_der" -> Alignment.BottomEnd
                else -> Alignment.TopEnd
            }
            MonitorRecursos(
                muestras = muestras,
                modifier = Modifier.align(esquina).padding(14.dp).zIndex(90f),
            )
        }

        // Cursor del mando: por encima de TODO (también de diálogos y avisos), sin
        // interceptar toques.
        Box(Modifier.fillMaxSize().zIndex(100f)) { PunteroOverlay() }
    }
}

/**
 * Cartel inferior con el último aviso del motor de complementos: «Instalando…»,
 * «INSTALADA», «ERROR…». Antes esos avisos sólo se veían dentro de Complementos
 * y desde la tienda web parecía que no pasaba nada.
 */
@Composable
private fun AvisoFlotante(texto: String?, modifier: Modifier = Modifier) {
    if (texto == null) return
    val error = texto.startsWith("ERROR") || texto.startsWith("Instalación fallida")
    val bien = texto.startsWith("INSTALADA") || texto.startsWith("Extensión instalada")
    val acento = when {
        error -> com.antena.tv.ui.Alerta
        bien -> Escudo
        else -> Ambar
    }
    Row(
        modifier
            .padding(bottom = 36.dp, start = 24.dp, end = 24.dp)
            .widthIn(max = 760.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Casco.copy(alpha = 0.97f))
            .border(1.5.dp, acento, RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(acento))
        Spacer(Modifier.width(14.dp))
        Text(
            texto.lines().take(2).joinToString("\n"),
            style = AntenaType.body,
            color = Fosforo,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Cierra Coco Surf por completo (también la tarea en «recientes»). */
private fun salirDeLaApp(context: Context) {
    var c: Context? = context
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) {
            c.finishAndRemoveTask()
            return
        }
        c = c.baseContext
    }
}

/**
 * Asa discreta en el borde superior mientras las barras están escondidas: una
 * pastilla fina que se ilumina al pasar por encima y las baja al tocarla.
 */
@Composable
private fun ManijaBarras(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, crecida: Boolean = false) {
    val fuente = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hover by fuente.collectIsHoveredAsState()
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { -it },
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { -it },
        modifier = modifier,
    ) {
        Box(
            Modifier
                .hoverable(fuente)
                .size(width = 190.dp, height = 30.dp)
                .pointerInput(onClick) { detectTapGestures { onClick() } },
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                Modifier
                    .padding(top = 7.dp)
                    .size(
                        width = androidx.compose.animation.core.animateDpAsState(if (crecida) 140.dp else if (hover) 72.dp else 52.dp, label = "ancho").value,
                        height = androidx.compose.animation.core.animateDpAsState(if (crecida) 6.dp else 5.dp, label = "alto").value,
                    )
                    .clip(RoundedCornerShape(3.dp))
                    .background(Fosforo.copy(alpha = if (crecida) 0.85f else if (hover) 0.9f else 0.55f)),
            )
        }
    }
}

/** Pastilla de abajo: indica que hay más página; tocarla baja una pantalla. */
@Composable
private fun ManijaAbajo(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, crecida: Boolean = false) {
    val fuente = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hover by fuente.collectIsHoveredAsState()
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it },
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it },
        modifier = modifier,
    ) {
        Box(
            Modifier
                .hoverable(fuente)
                .size(width = 190.dp, height = 30.dp)
                .pointerInput(onClick) { detectTapGestures { onClick() } },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .padding(bottom = 8.dp)
                    .size(
                        width = androidx.compose.animation.core.animateDpAsState(if (crecida) 140.dp else if (hover) 84.dp else 60.dp, label = "ancho").value,
                        height = 6.dp,
                    )
                    .clip(RoundedCornerShape(3.dp))
                    .background(androidx.compose.ui.graphics.Color.White.copy(alpha = if (crecida) 0.85f else if (hover) 0.9f else 0.6f)),
            )
        }
    }
}

/** Multiplicador de la velocidad del cursor para cada opción de Configuración. */
internal fun factorDeVelocidad(id: String): Float = when (id) {
    "lenta" -> 1.2f
    "rapida" -> 3.2f
    "turbo" -> 5f
    else -> 2f
}

/** Aviso de una página pausada por memoria al límite (antes, el televisor se colgaba). */
@Composable
private fun PaginaPausada(url: String, onReintentar: () -> Unit, onCerrar: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Noche), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 560.dp).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ToolIconGlyph(ToolIcon.Pulse, Ambar, size = 44.dp)
            Spacer(Modifier.height(14.dp))
            Text("Esta página necesita más memoria de la disponible", style = AntenaType.panel, color = Fosforo)
            Spacer(Modifier.height(8.dp))
            Text(
                "Se pausó para que el televisor no se cuelgue. Cierra otras pestañas o complementos " +
                    "y vuelve a intentarlo; el motivo queda en la Bitácora.",
                style = AntenaType.body,
                color = FosforoTenue,
            )
            Spacer(Modifier.height(6.dp))
            Text(hostOf(url) ?: url, style = AntenaType.monoSmall, color = FosforoTenue)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RemoteChip(label = "Reintentar", accent = true, small = true, onClick = onReintentar)
                RemoteChip(label = "Cerrar pestaña", small = true, onClick = onCerrar)
            }
        }
    }
}

/** Ancho de los paneles rápidos (Historial / Descargas). */
private val PanelAncho = 440.dp

/** Alto de la barra de direcciones: pensado para leerse a 3 m de distancia. */
private val BarraAlto = 52.dp

/** Envoltorio de página completa con cabecera y botón de cierre. */
@Composable
private fun PageScaffold(
    title: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        first.requestFocus()
    }
    Box(Modifier.fillMaxSize().background(Noche)) {
        Column(Modifier.fillMaxSize()) {
            if (title.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp, end = 24.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, style = AntenaType.panel, color = Fosforo, modifier = Modifier.weight(1f))
                    Box(Modifier.focusRequester(first)) {
                        RemoteChip(label = "", icon = ToolIcon.Close, small = true, onClick = onClose)
                    }
                }
            } else {
                // Las páginas con cabecera propia enfocan su primer elemento.
                Box(Modifier.focusRequester(first)) { }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}

private fun tabLabel(tab: PageTab): String {
    // Paso 2 · las vistas internas llevan su propio nombre en la pestaña.
    tituloInterna(tab.url)?.let { return it }
    return when {
        tab.url.isEmpty() -> "Nueva pestaña"
        tab.title.isNotBlank() && tab.title != tab.url -> tab.title
        tab.url.startsWith("cocosurf://") -> "Inicio"
        else -> hostOf(tab.url) ?: tab.title
    }
}

/**
 * Chip de pestaña (rediseño del Paso 2).
 *
 * Antes: píldora con contorno, la activa en blanco macizo, 26 dp de alto, un punto
 * de estado y el título en 14 sp. Ahora: superficie plana sin contorno (sólo el
 * foco dibuja el anillo ámbar), franja ámbar bajo la pestaña activa, favicon del
 * sitio o icono de la vista interna como identificador, título a 18 sp y un botón
 * × con más área. Se mantiene compacto (34 dp, 20 dp al leer) para no comerse la
 * página en una pantalla de TV, pero con más aire y mejor objetivo táctil.
 */
@Composable
private fun TabChip(
    label: String,
    iconoExt: android.graphics.Bitmap? = null,
    url: String,
    active: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onClose: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val fuente = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hover by fuente.collectIsHoveredAsState()
    val center = rememberCenterHandler(onClick, onLongClick)
    val shape = RoundedCornerShape(9.dp)

    Box(
        Modifier
            .hoverable(fuente)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .height(if (compact) 20.dp else 38.dp)
            .clip(shape)
            .background(
                when {
                    focused -> Ambar.copy(alpha = 0.16f)
                    hover -> Ambar.copy(alpha = 0.10f)
                    active -> Fosforo.copy(alpha = 0.08f)
                    else -> androidx.compose.ui.graphics.Color.Transparent
                },
            )
            .border(
                width = if (focused) 1.5.dp else 0.dp,
                color = if (focused) Ambar else androidx.compose.ui.graphics.Color.Transparent,
                shape = shape,
            )
            .onKeyEvent(center)
            .pointerInput(onClick) { detectTapGestures { onClick() } },
    ) {
        Row(
            Modifier
                .fillMaxHeight()
                .padding(
                    start = if (compact) 5.dp else 9.dp,
                    end = if (compact) 3.dp else 6.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (iconoExt != null) {
                androidx.compose.foundation.Image(
                    bitmap = iconoExt.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 13.dp else 18.dp),
                )
            } else {
                TabIcono(url = url, active = active, compact = compact)
            }
            Spacer(Modifier.width(if (compact) 4.dp else 7.dp))
            Text(
                label,
                style = if (compact) AntenaType.meta.copy(fontSize = 11.sp) else AntenaType.body,
                color = when {
                    focused -> Ambar
                    active -> Fosforo
                    else -> FosforoTenue
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = if (compact) 76.dp else 148.dp),
            )
            Spacer(Modifier.width(if (compact) 4.dp else 8.dp))
            // Botón × de cerrar. Cierra en KeyDown (un KeyUp tardío no debe cerrar
            // la pestaña por accidente) y con el mando basta mantener OK.
            Box(
                Modifier
                    .size(if (compact) 22.dp else 34.dp)
                    .onKeyEvent { ev ->
                        if (ev.key == Key.DirectionCenter || ev.key == Key.Enter) {
                            if (ev.type == KeyEventType.KeyDown) onClose()
                            true
                        } else false
                    }
                    .pointerInput(url) { detectTapGestures { onClose() } },
                contentAlignment = Alignment.Center,
            ) {
                // Una «x» fina, sin círculo ni fondo.
                ToolIconGlyph(
                    ToolIcon.Close,
                    if (active) Fosforo.copy(alpha = 0.85f) else FosforoTenue,
                    size = if (compact) 9.dp else 13.dp,
                )
            }
        }
        // Franja ámbar: la marca de la pestaña activa (no hace falta contorno).
        if (active) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(if (compact) 2.dp else 3.dp)
                    .background(Ambar),
            )
        }
    }
}

/** Identificador de la pestaña: favicon del sitio o icono de la vista interna. */
@Composable
private fun TabIcono(url: String, active: Boolean, compact: Boolean) {
    val lado = if (compact) 13.dp else 18.dp
    if (url.startsWith("cocosurf://")) {
        val icono = when (url) {
            INTERNA_HISTORIAL -> ToolIcon.Clock
            INTERNA_DESCARGAS -> ToolIcon.Download
            INTERNA_CONFIG -> ToolIcon.Gear
            INTERNA_ACERCA -> ToolIcon.Info
            INTERNA_CLAVES -> ToolIcon.Lock
            INTERNA_ARCHIVOS -> ToolIcon.Folder
            else -> ToolIcon.Home
        }
        ToolIconGlyph(kind = icono, color = if (active) Ambar else Azul, size = lado)
        return
    }
    if (url.startsWith("http")) {
        FaviconBox(
            url = url,
            char = hostOf(url)?.firstOrNull()?.uppercase() ?: "?",
            size = lado,
            background = Fosforo.copy(alpha = if (active) 0.95f else 0.7f),
            textColor = Noche,
            iconPadding = if (compact) 1.dp else 2.dp,
            textStyle = AntenaType.meta.copy(fontSize = if (compact) 8.sp else 11.sp),
        )
        return
    }
    ToolIconGlyph(kind = ToolIcon.NewTab, color = if (active) Ambar else Azul, size = lado)
}

/**
 * Fila del desplegable de sugerencias, navegable con el mando.
 *
 * La marca distingue las dos clases de sugerencia: 🌐 una dirección del
 * historial (título de la página + URL), 🔍 una búsqueda anterior (su texto, el
 * motor con el que se hizo y cuántas veces se ha usado).
 */
@Composable
private fun SuggestionRow(
    sugerencia: Sugerencia,
    isFirst: Boolean,
    focusFirst: FocusRequester?,
    onUp: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val center = rememberCenterHandler(onClick, null)
    val esBusqueda = sugerencia.tipo == TipoSugerencia.BUSQUEDA
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (focusFirst != null) Modifier.focusRequester(focusFirst) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .background(if (focused) Ambar.copy(alpha = 0.16f) else Noche)
            .onPreviewKeyEvent { ev ->
                if (isFirst && ev.key == Key.DirectionUp && ev.type == KeyEventType.KeyDown) {
                    onUp()
                    true
                } else false
            }
            .onKeyEvent(center)
            .pointerInput(onClick) { detectTapGestures { onClick() } }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (esBusqueda) "🔍" else "🌐",
            style = AntenaType.meta,
            color = if (focused) Ambar else Azul,
            modifier = Modifier.width(34.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                if (esBusqueda) "Buscar «${sugerencia.titulo}»" else sugerencia.titulo,
                style = AntenaType.meta,
                color = if (focused) Ambar else Fosforo,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (esBusqueda) {
                    "Búsqueda anterior · ${sugerencia.motor ?: "motor"} · ${sugerencia.visitas}×"
                } else {
                    sugerencia.url
                },
                style = AntenaType.mono.copy(fontSize = 12.sp),
                color = FosforoTenue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AddressBar(
    modifier: Modifier = Modifier,
    url: String,
    editing: Boolean,
    enabled: Boolean = true,
    starred: Boolean = false,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onStartEdit: () -> Unit,
    // Mantener OK: marca toda la dirección para sustituirla de una vez.
    onSelectAll: () -> Unit,
    onSubmit: (String) -> Unit,
    onStar: () -> Unit,
    focusRequester: FocusRequester,
    fieldRequester: FocusRequester,
) {
    var focused by remember { mutableStateOf(false) }
    val starFr = remember { FocusRequester() }
    val center = rememberCenterHandler(onStartEdit, onSelectAll)
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .height(38.dp)
            .clip(shape)
            .background(if (focused && !editing) Ambar.copy(alpha = 0.16f) else Noche)
            .border(
                if (focused || editing) 2.dp else 1.dp,
                when {
                    focused && !editing -> Ambar
                    editing -> Ambar.copy(alpha = 0.7f)
                    else -> Fosforo.copy(alpha = 0.08f)
                },
                shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .focusRequester(focusRequester)
            .then(if (enabled) Modifier.focusable() else Modifier)
            .then(
                if (enabled) {
                    Modifier.onPreviewKeyEvent { ev ->
                        // → desde el campo salta a la estrella viva dentro de la barra.
                        if (
                            !editing && focused && ev.key == Key.DirectionRight &&
                            ev.type == KeyEventType.KeyDown &&
                            url.startsWith("http") // la estrella sólo existe con URL http(s)
                        ) {
                            starFr.requestFocus()
                            true
                        } else {
                            false
                        }
                    }
                } else Modifier,
            )
            .then(if (enabled) Modifier.onKeyEvent(center) else Modifier)            // La clave del gesto es el propio callback: con `Unit` el bloque no
            // se reiniciaba nunca y el toque seguía usando el lambda de la
            // primera composición.
            .then(if (enabled) Modifier.pointerInput(onStartEdit) { detectTapGestures { onStartEdit() } }
                else Modifier,)
            // Sin margen a la derecha: la estrella queda pegada al borde del campo.
            .padding(start = 14.dp, end = if (url.startsWith("http")) 0.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // https → candado verde: la conexión es segura.
        if (url.startsWith("https://") && !editing) {
            ToolIconGlyph(ToolIcon.Lock, Escudo, size = 18.dp)
            Spacer(Modifier.width(8.dp))
        }
        if (editing) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = AntenaType.body.copy(color = Fosforo),
                cursorBrush = SolidColor(Ambar),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onSubmit(value.text) }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(fieldRequester)
                    .onPreviewKeyEvent { ev ->
                        val k = ev.key
                        if (
                            (k == Key.Enter || k == Key.NumPadEnter || k == Key.DirectionCenter) &&
                            ev.type == KeyEventType.KeyUp
                        ) {
                            onSubmit(value.text)
                            true
                        } else {
                            false
                        }
                    },
            )
        } else {
            Text(
                text = url.ifEmpty { "Busca o escribe una dirección" },
                style = AntenaType.body,
                color = if (url.isEmpty()) FosforoTenue else Fosforo,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        // Mientras se escribe: copiar, pegar y borrar todo (el teclado de un TV no los trae).
        if (editing) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            Spacer(Modifier.width(4.dp))
            RemoteChip(
                label = "",
                icon = ToolIcon.Copy,
                small = true,
                ghost = true,
                onClick = {
                    val sel = value.selection
                    val texto = if (sel.collapsed) value.text else value.text.substring(sel.min, sel.max)
                    copyTextToClipboard(ctx, texto)
                },
            )
            RemoteChip(
                label = "Pegar",
                small = true,
                ghost = true,
                onClick = {
                    pegarPortapapeles(ctx)?.let { clip ->
                        val sel = value.selection
                        val nuevo = value.text.replaceRange(sel.min, sel.max, clip)
                        onValueChange(TextFieldValue(nuevo, TextRange(sel.min + clip.length)))
                    }
                },
            )
            RemoteChip(
                label = "",
                icon = ToolIcon.Close,
                small = true,
                ghost = true,
                onClick = { onValueChange(TextFieldValue("")) },
            )
            Spacer(Modifier.width(2.dp))
        }

        // Estrella de favoritos: pegada al borde derecho del campo, a toda su
        // altura y separada por un filete (sin marco ni margen propio).
        if (url.startsWith("http")) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.fillMaxHeight().width(1.dp).background(Azul.copy(alpha = 0.35f)))
            EstrellaBorde(starred = starred, enabled = enabled, requester = starFr, onClick = onStar)
        }
    }
}

/**
 * Botón de favorito pegado al borde derecho de la barra de direcciones: ocupa
 * toda la altura del campo (lo recorta su forma redondeada), vacía = sin
 * guardar, rellena en ámbar = ya es favorito, bloque ámbar al enfocarla.
 */
@Composable
private fun EstrellaBorde(
    starred: Boolean,
    enabled: Boolean,
    requester: FocusRequester,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val fuente = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hover by fuente.collectIsHoveredAsState()
    val center = rememberCenterHandler(onClick, null)
    Box(
        Modifier
            .fillMaxHeight()
            .width(54.dp)
            .hoverable(fuente)
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (enabled) {
                    Modifier
                        .focusable()
                        .onKeyEvent(center)
                        .pointerInput(onClick) { detectTapGestures { onClick() } }
                } else {
                    Modifier
                },
            )
            .background(
                when {
                    focused -> Ambar
                    hover -> Ambar.copy(alpha = 0.22f)
                    starred -> Ambar.copy(alpha = 0.16f)
                    else -> androidx.compose.ui.graphics.Color.Transparent
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        ToolIconGlyph(
            kind = if (starred || focused) ToolIcon.Star else ToolIcon.StarOutline,
            color = when {
                focused -> Noche
                starred -> Ambar
                else -> FosforoTenue
            },
            size = 24.dp,
        )
    }
}
