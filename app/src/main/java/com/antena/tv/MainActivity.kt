package com.antena.tv

import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import com.antena.tv.browser.BrowserScreen
import com.antena.tv.browser.MonitorConfig
import com.antena.tv.cursor.Puntero
import com.antena.tv.log.Bitacora
import com.antena.tv.log.LogKind
import com.antena.tv.ui.AntenaTheme
import com.antena.tv.ui.Noche
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    /** Fase 5 · «mantener OK»: la pantalla decide si ese gesto lo consume. */
    var onLongOk: (() -> Boolean)? = null
    /** Fase 5 · ↑ desde la página (al tope): la pantalla decide si sale a la barra. */
    var onUpInPage: (() -> Boolean)? = null
    /** Android avisa de poca memoria: la pantalla suelta lo que no se ve. */
    var alRecortarMemoria: ((Int) -> Unit)? = null
    private var okDownAt = 0L
    private var longOkFired = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fase 6 · bitácora: recupera el cierre anterior y captura errores internos.
        Bitacora.init(this)
        setContent {
            AntenaTheme {
                Shell()
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_MODERATE) {
            val e = com.antena.tv.gecko.Memoria.leer(this)
            Bitacora.add(
                if (level >= TRIM_MEMORY_RUNNING_CRITICAL) LogKind.ERROR else LogKind.SISTEMA,
                "Android pidió recortar memoria (nivel $level) · ${e.libreMb} MB libres de ${e.totalMb} MB",
            )
        }
        if (level >= TRIM_MEMORY_RUNNING_LOW) alRecortarMemoria?.invoke(level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Bitacora.add(LogKind.ERROR, "Android avisó: memoria muy baja (onLowMemory)")
        alRecortarMemoria?.invoke(TRIM_MEMORY_RUNNING_CRITICAL)
    }

    override fun onDestroy() {
        // Cierre a propósito (el usuario salió); si el sistema mata el proceso no llega aquí.
        if (isFinishing) Bitacora.cierreLimpio()
        super.onDestroy()
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        // Un toque real: sirve para saber si una ventana emergente la pidió el usuario.
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
            Puntero.registrarAccion(ev.y)
        }
        return super.dispatchTouchEvent(ev)
    }

    /** El monitor cambió (se abrió/cerró o se asignó otra tecla): la pantalla lo guarda. */
    var alMonitorCambio: (() -> Unit)? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Tecla rápida del monitor de recursos: asignarla (modo captura) o usarla.
        if (MonitorConfig.capturando) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    MonitorConfig.capturando = false
                } else if (!MonitorConfig.reservada(event.keyCode)) {
                    MonitorConfig.tecla = event.keyCode
                    MonitorConfig.capturando = false
                    alMonitorCambio?.invoke()
                }
            }
            return true
        }
        if (event.keyCode == MonitorConfig.tecla && !MonitorConfig.reservada(event.keyCode)) {
            if (event.action == KeyEvent.ACTION_UP) {
                MonitorConfig.visible = !MonitorConfig.visible
                alMonitorCambio?.invoke()
            }
            return true
        }
        // El cursor del mando va primero: con él activo, las flechas y OK son suyos.
        if (Puntero.alTecla(this, event)) return true
        if (
            event.keyCode == KeyEvent.KEYCODE_DPAD_UP &&
            event.action == KeyEvent.ACTION_DOWN &&
            onUpInPage?.invoke() == true
        ) {
            return true
        }
        val ok = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            event.keyCode == KeyEvent.KEYCODE_ENTER ||
            event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
        if (ok) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    val now = SystemClock.uptimeMillis()
                    if (event.repeatCount == 0) {
                        okDownAt = now
                        longOkFired = false
                    } else if (
                        !longOkFired && okDownAt != 0L && now - okDownAt >= 400 &&
                        onLongOk?.invoke() == true
                    ) {
                        longOkFired = true
                        return true
                    }
                }

                KeyEvent.ACTION_UP -> {
                    val started = okDownAt
                    okDownAt = 0L
                    if (longOkFired) {
                        longOkFired = false
                        return true
                    }
                    val held = started != 0L && SystemClock.uptimeMillis() - started >= 400
                    if (held && onLongOk?.invoke() == true) return true
                }

                else -> Unit
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

/**
 * Carcasa sin rail: el navegador ocupa toda la ventana. El acceso a las demás
 * pantallas (favoritos, historial, descargas, sonda) vive en el menú ☰.
 */
@Composable
fun Shell() {
    val windowInfo = LocalWindowInfo.current
    val focusManager = LocalFocusManager.current
    var bootstrapped by remember { mutableStateOf(false) }

    LaunchedEffect(windowInfo.isWindowFocused, bootstrapped) {
        if (windowInfo.isWindowFocused && !bootstrapped) {
            repeat(6) {
                delay(300)
                val moved = runCatching { focusManager.moveFocus(FocusDirection.Next) }.getOrNull()
                android.util.Log.d("ANTENA", "bootstrap moveFocus -> $moved")
                if (moved == true) {
                    bootstrapped = true
                    return@LaunchedEffect
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Noche)) {
        BrowserScreen()
    }
}
