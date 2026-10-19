// Spike M5 · motor GeckoView con extensiones WebExtension reales.
// Verifica: (1) carga de página, (2) D-pad → scroll informado por la extensión,
// (3) extensión empaquetada con ensureBuiltIn, (4) instalar .xpi por URL y
// (5) MENÚ DE COMPLEMENTOS: listar, activar/desactivar, desinstalar e
// instalar desde la tienda AMO (links directos + URL libre).
package com.antena.tv.spike

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var gecko: GeckoView
    private lateinit var session: GeckoSession
    private lateinit var runtime: GeckoRuntime

    // Panel de complementos (menú de herramientas).
    private lateinit var panel: LinearLayout
    private lateinit var panelLista: LinearLayout
    private lateinit var panelLog: TextView
    private var panelVisible = false

    private var extensionOk = false
    private var extInstalada: WebExtension? = null
    private var ultimoMensaje = "(sin mensajes aún)"
    private var titulo = "(sin título)"
    // Id de la extensión cuya fila tenía el foco (para no perderlo al refrescar).
    private var focoExtId: String? = null

    // Links directos a la tienda AMO (.xpi firmados) para montar en un toque.
    private val tienda = listOf(
        "uBlock Origin" to "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi",
        "Dark Reader" to "https://addons.mozilla.org/firefox/downloads/latest/darkreader/latest.xpi",
        "SponsorBlock" to "https://addons.mozilla.org/firefox/downloads/latest/sponsorblock-for-youtube/latest.xpi",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Barra de herramientas ──────────────────────────────────────────
        val barra = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0F131A"))
            setPadding(16, 10, 16, 10)
            addView(
                boton("🧩 Complementos") { abrirPanel() },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(boton("↻ Recargar") { session.reload() })
        }

        status = TextView(this).apply {
            setTextColor(Color.parseColor("#ECE6D8"))
            setBackgroundColor(Color.parseColor("#161B24"))
            setPadding(24, 16, 24, 16)
            textSize = 13f
            text = "Iniciando GeckoView…"
        }
        gecko = GeckoView(this)

        val columna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(barra, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(status, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(gecko, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }

        val root = FrameLayout(this).apply {
            addView(columna, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        construirPanel()
        root.addView(panel, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)

        runtime = GeckoRuntime.create(this)

        // Sin PromptDelegate, GeckoView no tiene "listener" para
        // GeckoView:WebExtension:InstallPrompt y TODO install() falla con
        // "No listener for ...InstallPrompt". El clic en el menú YA es el
        // consentimiento del usuario → se conceden permisos automáticamente.
        runtime.webExtensionController.setPromptDelegate(promptDelegate)

        // (3) Extensión empaquetada en assets → instalada al arrancar.
        runtime.webExtensionController
            .ensureBuiltIn("resource://android/assets/prueba-ext/", "prueba@antena.tv")
            .accept(
                { ext ->
                    extensionOk = true
                    extInstalada = ext
                    ext?.setMessageDelegate(delegate, "antena")
                    if (::session.isInitialized && ext != null) {
                        session.webExtensionController.setMessageDelegate(ext, delegate, "antena")
                    }
                    log("extensión instalada: ${ext?.id}")
                    refrescarLista()
                },
                { e -> log("extensión ERROR: ${e?.message}") },
            )

        // (4) uBO REAL desde AMO por URL — el objetivo de M5.
        runtime.webExtensionController
            .install("https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi")
            .accept(
                { ext ->
                    log("uBO INSTALADA: ${ext?.id} · ${ext?.metaData?.name}")
                    refrescarLista()
                },
                { e -> log("uBO ERROR: ${e?.javaClass?.simpleName} ${e?.message}") },
            )

        session = GeckoSession()
        session.open(runtime)

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                permissions: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                log("ubicación: $url")
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                titulo = title ?: "(vacío)"
                pintar()
            }
        }
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                log("página cargada = $success · ext = $extensionOk")
            }
        }

        gecko.setSession(session)
        extInstalada?.let {
            session.webExtensionController.setMessageDelegate(it, delegate, "antena")
        }
        // (1) Página alta para probar scroll con el mando.
        session.loadUri("https://es.wikipedia.org/wiki/Android")
        gecko.requestFocus()
    }

    // ── Barra del menú de complementos ─────────────────────────────────────

    private fun boton(texto: String, alPulsar: () -> Unit): TextView =
        TextView(this).apply {
            text = texto
            textSize = 16f
            setTextColor(Color.parseColor("#ECE6D8"))
            setBackgroundColor(Color.parseColor("#232A38"))
            setPadding(28, 14, 28, 14)
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            setOnClickListener { alPulsar() }
            setOnFocusChangeListener { v, foco ->
                v.setBackgroundColor(if (foco) Color.parseColor("#2E5FA3") else Color.parseColor("#232A38"))
            }
        }

    private fun construirPanel() {
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F20D1118"))
            setPadding(48, 40, 48, 40)
            visibility = View.GONE
        }
        panel.addView(TextView(this@MainActivity).apply {
            text = "COMPLEMENTOS"
            textSize = 26f
            setTextColor(Color.parseColor("#7FB08A"))
        })
        panel.addView(TextView(this@MainActivity).apply {
            text = "ENTER = activar / desactivar · instala cualquier .xpi de la tienda"
            textSize = 14f
            setTextColor(Color.parseColor("#9AA4B2"))
            setPadding(0, 6, 0, 18)
        })

        val contenido = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panelLista = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        contenido.addView(panelLista)

        contenido.addView(separador("TIENDA AMO · addons.mozilla.org"))
        contenido.addView(filaSimple("🛒 Explorar la tienda en el navegador") { irTienda() })
        tienda.forEach { (nombre, url) ->
            contenido.addView(filaSimple("⬇ $nombre — instalar desde AMO") { instalar(url) })
        }
        contenido.addView(filaSimple("＋ Instalar desde otra URL de .xpi…") { dialogoUrl() })

        val scroll = ScrollView(this).apply {
            isFocusable = false
            addView(contenido, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        panel.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        panelLog = TextView(this).apply {
            text = "Listo."
            textSize = 14f
            setTextColor(Color.parseColor("#ECE6D8"))
            setBackgroundColor(Color.parseColor("#161B24"))
            setPadding(20, 14, 20, 14)
        }
        panel.addView(panelLog, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun separador(texto: String): TextView =
        TextView(this).apply {
            text = "── $texto ──"
            textSize = 14f
            setTextColor(Color.parseColor("#9AA4B2"))
            setPadding(0, 24, 0, 10)
        }

    /** Fila de acción simple (tienda, instalar por URL). */
    private fun filaSimple(texto: String, alPulsar: () -> Unit): TextView =
        TextView(this).apply {
            text = texto
            textSize = 18f
            setTextColor(Color.parseColor("#ECE6D8"))
            setBackgroundColor(Color.parseColor("#1E2531"))
            setPadding(20, 18, 20, 18)
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            setOnClickListener { alPulsar() }
            setOnFocusChangeListener { v, foco ->
                v.setBackgroundColor(if (foco) Color.parseColor("#2E5FA3") else Color.parseColor("#1E2531"))
            }
        }

    /** Fila por extensión instalada: info + toggle + desinstalar. */
    private fun filaExtension(ext: WebExtension): View {
        val activa = ext.metaData.enabled
        val fila = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1E2531"))
            setPadding(20, 12, 20, 12)
        }
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            setOnClickListener { focoExtId = ext.id; alternar(ext) }
            setOnFocusChangeListener { v, foco ->
                if (foco) focoExtId = ext.id
                v.setBackgroundColor(if (foco) Color.parseColor("#2E5FA3") else Color.TRANSPARENT)
            }
            addView(TextView(this@MainActivity).apply {
                text = "${if (activa) "●" else "○"} ${ext.metaData.name ?: ext.id}  v${ext.metaData.version}"
                textSize = 18f
                setTextColor(if (activa) Color.parseColor("#7FB08A") else Color.parseColor("#D08770"))
            })
            addView(TextView(this@MainActivity).apply {
                text = ext.id
                textSize = 11f
                setTextColor(Color.parseColor("#9AA4B2"))
            })
            addView(TextView(this@MainActivity).apply {
                text = if (activa) "ACTIVA — ENTER para desactivar" else "DESACTIVA — ENTER para activar"
                textSize = 13f
                setTextColor(Color.parseColor("#ECE6D8"))
            })
        }
        val quitar = TextView(this).apply {
            text = "✕ Desinstalar"
            textSize = 15f
            setTextColor(Color.parseColor("#E8A0A0"))
            setPadding(24, 16, 12, 16)
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            setOnClickListener { confirmarDesinstalar(ext) }
            setOnFocusChangeListener { v, foco ->
                setBackgroundColor(if (foco) Color.parseColor("#2E5FA3") else Color.TRANSPARENT)
            }
        }
        fila.addView(info)
        fila.addView(quitar)
        return fila
    }

    private fun refrescarLista() {
        runtime.webExtensionController.list().accept(
            { exts ->
                runOnUiThread {
                    panelLista.removeAllViews()
                    val orden = exts.orEmpty()
                        .filter { it.id != "toolkit@mozilla.org" }
                        .sortedBy { (it.metaData.name ?: it.id).lowercase() }
                    val resumen = orden.joinToString { "${it.id}[${if (it.metaData.enabled) "on" else "off"}]" }
                    Log.i("Spike", "list() → $resumen")
                    orden.forEach { panelLista.addView(filaExtension(it)) }
                    if (orden.isEmpty()) {
                        panelLista.addView(TextView(this).apply {
                            text = "(sin extensiones instaladas)"
                            textSize = 16f
                            setTextColor(Color.parseColor("#9AA4B2"))
                            setPadding(0, 8, 0, 8)
                        })
                    }
                    // El foco sólo puede pedirse tras el layout (una vista de
                    // tamaño 0 rechaza requestFocus); al abrir el panel list()
                    // tarda ~2 s, así que se pide en el siguiente frame.
                    if (panelVisible) panelLista.post { enfocarFila() }
                }
            },
            { e -> runOnUiThread { panelLog.text = "ERROR al listar: ${e?.message}" } },
        )
    }

    private fun alternar(ext: WebExtension) {
        val ctrl = runtime.webExtensionController
        val destino = if (ext.metaData.enabled) "DESACTIVADA" else "ACTIVADA"
        val futuro =
            if (ext.metaData.enabled) ctrl.disable(ext, WebExtensionController.EnableSource.USER)
            else ctrl.enable(ext, WebExtensionController.EnableSource.USER)
        futuro.accept(
            { nuevo ->
                Log.i("Spike", "toggle ${ext.id} → $destino")
                runOnUiThread {
                    panelLog.text = "${nuevo?.metaData?.name ?: ext.id}: $destino"
                    refrescarLista()
                }
            },
            { e ->
                Log.e("Spike", "toggle ERROR: ${e?.message}", e)
                runOnUiThread { panelLog.text = "ERROR toggle: ${e?.javaClass?.simpleName} ${e?.message}"; refrescarLista() }
            },
        )
    }

    private fun confirmarDesinstalar(ext: WebExtension) {
        AlertDialog.Builder(this)
            .setTitle("Desinstalar")
            .setMessage("¿Quitar «${ext.metaData.name ?: ext.id}» del sistema?")
            .setPositiveButton("Desinstalar") { _, _ ->
                runtime.webExtensionController.uninstall(ext).accept(
                    { _ ->
                        runOnUiThread {
                            panelLog.text = "DESINSTALADA: ${ext.metaData.name ?: ext.id}"
                            refrescarLista()
                        }
                    },
                    { e -> runOnUiThread { panelLog.text = "ERROR al desinstalar: ${e?.message}"; refrescarLista() } },
                )
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun instalar(url: String) {
        panelLog.text = "Descargando e instalando…\n$url"
        runtime.webExtensionController.install(url).accept(
            { ext ->
                Log.i("Spike", "install OK: ${ext?.id} v${ext?.metaData?.version}")
                runOnUiThread {
                    panelLog.text = "INSTALADA: ${ext?.metaData?.name ?: ext?.id} v${ext?.metaData?.version}"
                    refrescarLista()
                }
            },
            { e ->
                Log.e("Spike", "install ERROR: ${e?.javaClass?.simpleName} ${e?.message}", e)
                runOnUiThread {
                    panelLog.text = "ERROR al instalar: ${e?.javaClass?.simpleName} ${e?.message}"
                }
            },
        )
    }

    // Concede los permisos que pida la extensión al instalarse/actualizarse.
    private val promptDelegate = object : WebExtensionController.PromptDelegate {
        override fun onInstallPromptRequest(
            extension: WebExtension,
            permissions: Array<String>,
            origins: Array<String>,
        ): GeckoResult<WebExtension.PermissionPromptResponse> {
            Log.i("Spike", "prompt instalar ${extension.id}: permisos=${permissions.size} orígenes=${origins.size}")
            return GeckoResult.fromValue(WebExtension.PermissionPromptResponse(true, true, true))
        }

        override fun onInstallPromptRequest(
            extension: WebExtension,
            permissions: Array<String>,
            origins: Array<String>,
            optional: Array<String>,
        ): GeckoResult<WebExtension.PermissionPromptResponse> {
            Log.i("Spike", "prompt instalar ${extension.id} (con opcionales)")
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

    private fun dialogoUrl() {
        val input = EditText(this).apply {
            setText("https://addons.mozilla.org/firefox/downloads/latest/darkreader/latest.xpi")
            hint = "https://…/archivo.xpi"
        }
        AlertDialog.Builder(this)
            .setTitle("Instalar .xpi desde URL")
            .setView(input)
            .setPositiveButton("Instalar") { _, _ -> instalar(input.text.toString().trim()) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Link a la tienda: se navega dentro del propio spike. */
    private fun irTienda() {
        cerrarPanel()
        session.loadUri("https://addons.mozilla.org/es/firefox/extensions/")
        gecko.requestFocus()
    }

    private fun abrirPanel() {
        panelVisible = true
        panel.visibility = View.VISIBLE
        refrescarLista() // el foco lo pide el callback de list() (enfocarFila)
    }

    /**
     * Pone el foco en la fila de la extensión recordada (focoExtId) o, si no,
     * en la primera. El foco va al hijo "info" (focusable) de la fila, no a la
     * fila contenedora: si no, ENTER se lo lleva la WebView.
     */
    private fun enfocarFila() {
        val destino = (0 until panelLista.childCount)
            .mapNotNull { i ->
                val fila = panelLista.getChildAt(i) as? LinearLayout ?: return@mapNotNull null
                val info = fila.getChildAt(0) ?: fila
                (info as? View)?.takeIf { it.isFocusable }?.let { fila to it }
            }
            .firstOrNull { (fila, _) ->
                val id = (fila as? LinearLayout)?.let { filaFocoId(it) }
                id != null && id == focoExtId
            }
            ?.second
            ?: (panelLista.getChildAt(0) as? LinearLayout)?.getChildAt(0)
        val ok = destino?.requestFocus() ?: false
        Log.i("Spike", "enfocarFila: destino=${destino?.javaClass?.simpleName} hijos=${panelLista.childCount} requestFocus=$ok")
    }

    /** Id de la extensión que representa una fila (línea 2 del bloque de info). */
    private fun filaFocoId(fila: LinearLayout): String? =
        ((fila.getChildAt(0) as? LinearLayout)?.getChildAt(1) as? TextView)?.text?.toString()

    private fun cerrarPanel() {
        panelVisible = false
        panel.visibility = View.GONE
    }

    // ── Ciclo de vida / teclado ────────────────────────────────────────────

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                if (panelVisible) cerrarPanel() else abrirPanel()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (panelVisible) {
                    cerrarPanel()
                    gecko.requestFocus()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // La extensión informa por native messaging: título, scroll y banner.
    private val delegate = object : WebExtension.MessageDelegate {
        override fun onMessage(
            nativeApp: String,
            message: Any,
            sender: WebExtension.MessageSender,
        ): GeckoResult<Any>? {
            ultimoMensaje = message.toString()
            runOnUiThread { pintar() }
            return null
        }
    }

    // (2) El D-pad llega como teclado.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                -> if (!panelVisible) log("tecla D-pad ${event.keyCode} → la página debería scrollear")
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun log(mensaje: String) {
        runOnUiThread {
            status.text =
                "spike · ext=$extensionOk · título=$titulo\n" +
                    "último mensaje: $ultimoMensaje\n$mensaje"
        }
    }

    private fun pintar() {
        status.text =
            "spike · ext=$extensionOk · título=$titulo\n" +
                "último mensaje: $ultimoMensaje"
    }
}
