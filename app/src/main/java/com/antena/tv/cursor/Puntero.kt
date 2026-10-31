package com.antena.tv.cursor

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.composed
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import com.antena.tv.ui.Ambar
import com.antena.tv.ui.Noche
import kotlinx.coroutines.delay

/**
 * Cursor del mando a distancia: un puntero que se mueve con ↑ ↓ ← → y «hace clic»
 * con OK, como el ratón de un navegador de TV.
 *
 * No finge nada: el OK se convierte en un toque real (ACTION_DOWN/UP) sobre la
 * ventana, en la posición del puntero, y lo recibe quien esté debajo (la página
 * de GeckoView o un control de Compose). Mantener OK es, por tanto, una
 * pulsación larga de verdad (menú contextual de la página) y mover con OK
 * pulsado arrastra (seleccionar texto, deslizar controles).
 *
 * Contra los bordes de la pantalla el puntero deja de moverse y desplaza la
 * página en esa dirección, de modo que se llega a todo el contenido sin tocar
 * otra tecla.
 */
object Puntero {
    /** Lo decide la pantalla: hay una página web a la vista y nada modal encima. */
    /** Un diálogo (p. ej. «¿Salir?») pide las flechas y OK para sí: el cursor no las consume. */
    var pausado by mutableStateOf(false)

    /** Lo mismo para pantallas a pantalla completa (reproductor): sus teclas no son del cursor. */
    @Volatile
    var pausadoExtra = false

    /** Cursor «corto»: va despacio y con pasos finos, para llegar a iconos pequeños. */
    var modoFino by mutableStateOf(false)

    /** Tecla del mando que alterna entre cursor largo y corto (0 = sin asignar). */
    var teclaPrecision by androidx.compose.runtime.mutableIntStateOf(0)

    /** Esperando que el usuario pulse la tecla que se asignará al modo preciso. */
    var capturandoPrecision by mutableStateOf(false)

    /** Aviso al cambiar de modo (la pantalla lo muestra como mensaje corto). */
    var alModoFino: (Boolean) -> Unit = {}

    fun alternarModoFino() {
        modoFino = !modoFino
        movimiento = SystemClock.uptimeMillis()
        alModoFino(modoFino)
    }

    /** Velocidad efectiva: en modo fino el cursor va a un tercio. */
    private val factorEf: Float get() = if (modoFino) factorVel * 0.3f else factorVel

    var activo by mutableStateOf(false)

    /** Posición en píxeles de la ventana. */
    var pos by mutableStateOf(Offset.Zero)
        private set

    /** OK mantenido: se dibuja un halo. */
    var pulsado by mutableStateOf(false)
        private set

    /** Se actualiza en cada movimiento: el puntero se atenúa si queda quieto. */
    var movimiento by mutableStateOf(0L)
        private set

    /** Desplazar la página (px) cuando el puntero empuja contra un borde. */
    var desplazar: (dx: Float, dy: Float) -> Unit = { _, _ -> }

    /** ¿Lo de debajo es una página web (scroll de Gecko) o una vista propia? */
    var esWeb = false

    /** Alto (px) de las barras de arriba cuando están a la vista; 0 si están ocultas. */
    var altoBarras = 0f

    /** Origen vertical (px) del contenido en la ventana (la barra de estado, si la hay). */
    var origenY = 0f

    /** Alto (px) del contenido: para saber cuándo el cursor está cerca del borde de abajo. */
    var altoVentana by mutableStateOf(0f)

    /** Con un vídeo a pantalla completa el cursor desaparece al quedar quieto (en vez de atenuarse). */
    var ocultarEnReposo by mutableStateOf(false)

    /** Multiplicador de velocidad del cursor (Configuración → Control remoto). */
    var factorVel = 1f

    /** Una lista desplazable de la interfaz (panel, menú): el cursor la mueve en sus bordes. */
    class Zona(val rect: androidx.compose.ui.geometry.Rect, val estado: androidx.compose.foundation.ScrollState)

    private val zonas = LinkedHashMap<Any, Zona>()
    private var zonaScroll: Zona? = null

    fun registrarZona(id: Any, zona: Zona) {
        zonas[id] = zona
    }

    fun quitarZona(id: Any) {
        zonas.remove(id)
    }

    /** Tras abrir/cerrar barras o paneles: que lo «iluminado» por el cursor se corrija solo. */
    fun sincronizarHover() {
        val d = decorRef ?: return
        if (hoverActivo) {
            enviarHover(d, MotionEvent.ACTION_HOVER_EXIT)
            hoverActivo = false
        }
        if (activo && !pulsado) hover(d)
    }

    /** Ancho (px) del panel fijado a la derecha; 0 si no hay. Es interfaz propia, no página. */
    var anchoPanel = 0f

    private fun enPanel(ancho: Float) = anchoPanel > 0f && pos.x > ancho - anchoPanel

    private val h = Handler(Looper.getMainLooper())
    private var scrollDx = 0f
    private var scrollDy = 0f
    private var scrollOn = false
    private var scrollDesde = 0L
    private var scrollVisto = 0L
    private var decorRef: android.view.View? = null
    private var densidad = 1f
    private var hoverActivo = false

    /** Última acción real del usuario (toque o OK): distingue un clic de un popunder. */
    @Volatile
    var ultimaAccion = 0L

    /**
     * Anota un toque/OK del usuario como «gesto» para las ventanas emergentes, pero
     * sólo si cayó sobre la página: un clic en las barras (cerrar una pestaña, p. ej.)
     * no autoriza a la página que queda debajo a abrir otra ventana.
     */
    fun registrarAccion(yVentana: Float) {
        if (yVentana - origenY >= altoBarras) ultimaAccion = SystemClock.uptimeMillis()
    }

    /** OK mantenido ~0,65 s sobre una zona vacía: abre el menú de la página. */
    var alMantener: () -> Unit = {}

    private val mantener = Runnable { if (pulsado) alMantener() }

    /** Tecla Menú: alterna el cursor. Devuelve si la consumió. */
    var alMenu: () -> Boolean = { false }

    private var inicializado = false
    private var downTime = 0L

    /** Lleva el puntero a un punto (px de la ventana): p. ej. sobre un menú recién abierto. */
    fun irA(x: Float, y: Float) {
        inicializado = true
        pos = Offset(x, y)
        movimiento = SystemClock.uptimeMillis()
    }

    /** La primera vez que se dibuja, el puntero nace en el centro de la ventana. */
    fun centrarSiHaceFalta(centro: Offset) {
        if (!inicializado) {
            pos = centro
            inicializado = true
        }
    }

    /** Devuelve `true` si el cursor consumió la tecla. */
    fun alTecla(act: Activity, ev: KeyEvent): Boolean {
        val k = ev.keyCode
        if (k == KeyEvent.KEYCODE_MENU) {
            // Pulsación corta: modo «desplazar» (las flechas mueven la lista que hay bajo
            // el cursor, p. ej. los vídeos laterales de YouTube). Mantener ≥ 0,7 s:
            // enciende o apaga el cursor.
            when (ev.action) {
                KeyEvent.ACTION_DOWN -> if (ev.repeatCount == 0) menuDesde = SystemClock.uptimeMillis()
                KeyEvent.ACTION_UP -> {
                    val largo = menuDesde != 0L && SystemClock.uptimeMillis() - menuDesde >= 700
                    menuDesde = 0L
                    if (largo) {
                        alMenu()
                    } else if (activo) {
                        alternarModoScroll()
                    } else {
                        alMenu()
                    }
                }
            }
            return true
        }
        val dir = k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN ||
            k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT
        // Soltar una flecha la quita; sin flechas, el movimiento y el desplazamiento paran.
        if (dir && ev.action == KeyEvent.ACTION_UP) {
            teclas.remove(k)
            if (teclas.isEmpty()) {
                h.removeCallbacks(movTick)
                // En modo desplazar, soltar las flechas levanta el dedo virtual (con inercia).
                if (modoScroll) decorRef?.let { terminarToque(it) }
                parar()
            }
        }
        if (!activo && !pulsado) return false
        // Con un diálogo abierto las flechas y OK mueven el foco de sus botones, no el cursor.
        if ((pausado || pausadoExtra) && (dir || k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER)) return false

        val decor = act.window.decorView
        decorRef = decor
        if (decor.width > 0) centrarSiHaceFalta(Offset(decor.width / 2f, decor.height / 2f))

        val ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER ||
            k == KeyEvent.KEYCODE_NUMPAD_ENTER

        if (dir) {
            if (activo && ev.action == KeyEvent.ACTION_DOWN) {
                val ahora = SystemClock.uptimeMillis()
                ultimoEvento = ahora
                if (modoScroll) {
                    // Las flechas desplazan lo que hay bajo el cursor.
                    if (k !in teclas) {
                        val primera = teclas.isEmpty()
                        teclas.add(k)
                        densidad = act.resources.displayMetrics.density
                        if (primera) {
                            inicioMov = ahora
                            h.removeCallbacks(modoTick)
                            h.post(modoTick)
                        }
                    }
                    movimiento = ahora
                    return true
                }
                if (k !in teclas) {
                    val primera = teclas.isEmpty()
                    teclas.add(k)
                    densidad = act.resources.displayMetrics.density
                    // Como en TV Bro: el cursor arranca desde cero y acelera mientras la flecha esté
                    // pulsada. Un toque suelto apenas lo mueve (unos pocos px), así se llega a un icono
                    // pequeño; mantenida, la velocidad sube sola hasta el máximo.
                    val d = 2.5f * densidad * (factorEf / 2f)
                    aplicar(
                        decor,
                        when (k) { KeyEvent.KEYCODE_DPAD_LEFT -> -d; KeyEvent.KEYCODE_DPAD_RIGHT -> d; else -> 0f },
                        when (k) { KeyEvent.KEYCODE_DPAD_UP -> -d; KeyEvent.KEYCODE_DPAD_DOWN -> d; else -> 0f },
                    )
                    if (primera) {
                        inicioMov = ahora
                        h.removeCallbacks(movTick)
                        h.post(movTick)
                    }
                }
            }
            return activo
        }
        if (ok) {
            when (ev.action) {
                KeyEvent.ACTION_DOWN ->
                    if (ev.repeatCount == 0 && activo) {
                        registrarAccion(pos.y)
                        toque(decor, MotionEvent.ACTION_DOWN)
                        pulsado = true
                        h.removeCallbacks(mantener)
                        h.postDelayed(mantener, 650)
                    }
                KeyEvent.ACTION_UP -> {
                    h.removeCallbacks(mantener)
                    if (pulsado) {
                        toque(decor, MotionEvent.ACTION_UP)
                        pulsado = false
                    }
                }
            }
            return true
        }
        return false
    }

    // ── Modo «desplazar» ───────────────────────────────────────────────────
    // Las flechas dejan de mover el cursor y desplazan lo que hay debajo de él: una
    // lista interna de la página (YouTube, redes sociales…) no se mueve con el
    // scroll general. Se hace con un dedo virtual que arrastra en el punto del cursor.

    /** ¿Las flechas desplazan lo que hay bajo el cursor en vez de mover el cursor? */
    var modoScroll by mutableStateOf(false)
        private set

    /** Aviso al cambiar de modo (la pantalla lo muestra como un mensaje corto). */
    var alModoScroll: (Boolean) -> Unit = {}

    private var menuDesde = 0L
    private var tocando = false
    private var tDown = 0L
    private var tOrigen = Offset.Zero
    private var tPos = Offset.Zero

    fun alternarModoScroll() {
        modoScroll = !modoScroll
        if (!modoScroll) {
            decorRef?.let { terminarToque(it) }
            teclas.clear()
            h.removeCallbacks(modoTick)
        }
        movimiento = SystemClock.uptimeMillis()
        alModoScroll(modoScroll)
    }

    private fun iniciarToque(decor: android.view.View) {
        tOrigen = pos
        tPos = pos
        tDown = SystemClock.uptimeMillis()
        enviarToque(decor, MotionEvent.ACTION_DOWN, tPos)
        tocando = true
    }

    private fun terminarToque(decor: android.view.View) {
        if (tocando) enviarToque(decor, MotionEvent.ACTION_UP, tPos)
        tocando = false
    }

    private fun enviarToque(decor: android.view.View, accion: Int, p: Offset) {
        val ahora = SystemClock.uptimeMillis()
        val e = tactil(tDown, ahora, accion, p.x, p.y)
        decor.dispatchTouchEvent(e)
        e.recycle()
    }

    /** Un fotograma del dedo virtual: arrastra en sentido contrario al que se quiere desplazar. */
    private val modoTick = object : Runnable {
        override fun run() {
            val ahora = SystemClock.uptimeMillis()
            val decor = decorRef
            if (teclas.isEmpty() || !activo || !modoScroll || decor == null || ahora - ultimoEvento > 1400) {
                if (decor != null) terminarToque(decor)
                teclas.clear()
                return
            }
            val t = ((ahora - inicioMov) / 1200f).coerceIn(0f, 1f)
            val paso = (260f + 1000f * t * t) * 0.016f * densidad     // dp/s → px por fotograma
            var dx = 0f
            var dy = 0f
            if (KeyEvent.KEYCODE_DPAD_UP in teclas) dy -= 1f
            if (KeyEvent.KEYCODE_DPAD_DOWN in teclas) dy += 1f
            if (KeyEvent.KEYCODE_DPAD_LEFT in teclas) dx -= 1f
            if (KeyEvent.KEYCODE_DPAD_RIGHT in teclas) dx += 1f
            if (!tocando) iniciarToque(decor)
            // Para desplazar hacia abajo el dedo sube, y al revés.
            tPos = Offset(
                (tPos.x - dx * paso).coerceIn(2f, decor.width - 2f),
                (tPos.y - dy * paso).coerceIn(2f, decor.height - 2f),
            )
            // Si el dedo recorrió mucho (o se acercó a un borde), se levanta y se vuelve a
            // apoyar donde empezó: así se puede seguir desplazando sin límite.
            val recorrido = Math.hypot((tPos.x - tOrigen.x).toDouble(), (tPos.y - tOrigen.y).toDouble())
            if (recorrido > 200f * densidad || tPos.x <= 4f || tPos.y <= 4f ||
                tPos.x >= decor.width - 4f || tPos.y >= decor.height - 4f
            ) {
                enviarToque(decor, MotionEvent.ACTION_UP, tPos)
                tocando = false
            } else {
                enviarToque(decor, MotionEvent.ACTION_MOVE, tPos)
            }
            h.postDelayed(this, 16)
        }
    }

    // Flechas mantenidas y su movimiento continuo.
    private val teclas = mutableSetOf<Int>()
    private var inicioMov = 0L
    private var ultimoEvento = 0L

    /** Movimiento mientras se mantiene una flecha: arranca lento y acelera (≈150 → 1100 dp/s). */
    private val movTick = object : Runnable {
        override fun run() {
            val ahora = SystemClock.uptimeMillis()
            val decor = decorRef
            // Sin eventos de tecla durante un rato = se soltó (red de seguridad).
            if (teclas.isEmpty() || !activo || decor == null || ahora - ultimoEvento > 1400) {
                teclas.clear()
                parar()
                return
            }
            val t = ((ahora - inicioMov) / 1100f).coerceIn(0f, 1f)
            val velocidad = (25f + 1075f * t * t) * factorEf
            val paso = velocidad * 0.016f * densidad
            var dx = 0f
            var dy = 0f
            if (KeyEvent.KEYCODE_DPAD_UP in teclas) dy -= 1f
            if (KeyEvent.KEYCODE_DPAD_DOWN in teclas) dy += 1f
            if (KeyEvent.KEYCODE_DPAD_LEFT in teclas) dx -= 1f
            if (KeyEvent.KEYCODE_DPAD_RIGHT in teclas) dx += 1f
            if (dx != 0f || dy != 0f) aplicar(decor, dx * paso, dy * paso)
            h.postDelayed(this, 16)
        }
    }

    /** Mueve el puntero (dx, dy en px): scroll contra los bordes de la pantalla o de una lista. */
    private fun aplicar(decor: android.view.View, dx: Float, dy: Float) {
        val dp = densidad
        val ancho = decor.width.toFloat()
        val alto = decor.height.toFloat()
        val margen = 3f * dp
        // Banda de 18 dp junto al borde de la pantalla: empujar ahí desplaza la página.
        val banda = 18f * dp
        val p = pos
        // Cursor dentro de una lista desplazable (panel, menú): al empujar contra su
        // borde —si aún hay contenido ese lado— la lista se desplaza, igual que la
        // página contra el borde de la pantalla.
        if (dy != 0f) {
            val z = zonas.values.lastOrNull { it.rect.contains(p) }
            if (z != null) {
                val abajo = dy > 0
                val puede = if (abajo) z.estado.canScrollForward else z.estado.canScrollBackward
                val enBorde = if (abajo) p.y + dy >= z.rect.bottom - margen else p.y + dy <= z.rect.top + margen
                if (puede && enBorde) {
                    movimiento = SystemClock.uptimeMillis()
                    scrollVisto = movimiento
                    pos = Offset(p.x, if (abajo) z.rect.bottom - margen - 1f else z.rect.top + margen + 1f)
                    val dir = if (abajo) 1f else -1f
                    if (!scrollOn || zonaScroll !== z || scrollDy != dir) {
                        zonaScroll = z
                        scrollDx = 0f
                        scrollDy = dir
                        scrollDesde = movimiento
                        indicador = if (abajo) 2 else 1
                        decorRef = decor
                        if (!scrollOn) {
                            scrollOn = true
                            h.post(tick)
                        }
                    }
                    return
                }
            }
        }
        val contraBorde = (dy < 0 && p.y + dy <= banda) || (dy > 0 && p.y + dy >= alto - banda) ||
            (dx < 0 && p.x + dx <= banda) || (dx > 0 && p.x + dx >= ancho - banda)
        movimiento = SystemClock.uptimeMillis()
        if (contraBorde) {
            // Contra el borde: la página se desliza mientras se mantenga la flecha
            // (no «a saltos»), cada vez más rápido. El puntero se queda en la banda.
            scrollVisto = movimiento
            pos = Offset(
                (p.x + dx).coerceIn(if (dx < 0) banda else 0f, if (dx > 0) ancho - banda else ancho),
                (p.y + dy).coerceIn(if (dy < 0) banda else 0f, if (dy > 0) alto - banda else alto),
            )
            val dirx = Math.signum(dx)
            val diry = Math.signum(dy)
            if (!scrollOn || zonaScroll != null || dirx != scrollDx || diry != scrollDy) {
                zonaScroll = null
                scrollDx = dirx
                scrollDy = diry
                scrollDesde = movimiento
                indicador = when {
                    diry < 0 -> 1
                    diry > 0 -> 2
                    dirx < 0 -> 3
                    else -> 4
                }
                decorRef = decor
                if (!scrollOn) {
                    scrollOn = true
                    h.post(tick)
                }
            }
            return
        }
        parar()
        pos = Offset((p.x + dx).coerceIn(0f, ancho), (p.y + dy).coerceIn(0f, alto))
        if (pulsado) {
            toque(decor, MotionEvent.ACTION_MOVE)
        } else {
            hover(decor)
        }
    }

    /** Borde por el que se está desplazando: 0 ninguno, 1 arriba, 2 abajo, 3 izquierda, 4 derecha. */
    var indicador by mutableStateOf(0)
        private set

    private fun parar() {
        scrollOn = false
        indicador = 0
        zonaScroll = null
        h.removeCallbacks(tick)
    }

    /** Un «fotograma» de desplazamiento continuo (≈60 por segundo en páginas web). */
    private val tick = object : Runnable {
        override fun run() {
            val ahora = SystemClock.uptimeMillis()
            val decor = decorRef
            // Sin eventos de tecla durante un rato = se soltó la flecha (red de seguridad).
            if (!scrollOn || !activo || decor == null || ahora - scrollVisto > 1100) {
                scrollOn = false
                return
            }
            val t = ((ahora - scrollDesde) / 900f).coerceIn(0f, 1f)
            val dpf = (7f + 15f * t) * densidad          // 7 → 22 dp por fotograma
            val z = zonaScroll
            if (z != null) {
                // Lista de la interfaz: se mueve directamente su estado de scroll.
                z.estado.dispatchRawDelta(scrollDy * dpf)
                h.postDelayed(this, 16)
            } else if (esWeb && !enPanel(decor.width.toFloat())) {
                // Rueda del ratón en el punto del cursor: Gecko mueve el elemento que haya
                // debajo (la lista de vídeos de YouTube, un panel interno…) y, si ya no puede
                // más, la página entera. Si la rueda no llega, se usa el desplazamiento general.
                if (!rueda(decor, scrollDx * dpf, scrollDy * dpf)) desplazar(scrollDx * dpf, scrollDy * dpf)
                h.postDelayed(this, 16)
            } else {
                arrastrar(decor, scrollDx * dpf * 3f, scrollDy * dpf * 3f)
                h.postDelayed(this, 80)
            }
        }
    }

    /**
     * La pantalla enlaza esto con la página activa: recibe la posición del cursor en la
     * ventana y cuánto se quiere desplazar, y entrega la rueda directo al motor (sin pasar por
     * las asas ni otras piezas de la interfaz que están encima y se la tragarían).
     */
    var ruedaWeb: (x: Float, y: Float, dx: Float, dy: Float) -> Boolean = { _, _, _, _ -> false }

    /** Una vuelta de rueda en el cursor (px de pantalla que se quieren desplazar). */
    private fun rueda(@Suppress("UNUSED_PARAMETER") decor: android.view.View, dx: Float, dy: Float): Boolean =
        runCatching { ruedaWeb(pos.x, pos.y, dx, dy) }.getOrDefault(false)

    /**
     * «Pasar el ratón» por encima de la interfaz propia (barras, menús, paneles)
     * para que los botones se iluminen. Sobre la página web NO se envía: GeckoView
     * procesa el hover en el hilo principal y llegó a bloquear la aplicación.
     */
    private fun hover(decor: android.view.View) {
        val propia = !esWeb || (pos.y - origenY) < altoBarras || enPanel(decor.width.toFloat())
        if (propia) {
            enviarHover(decor, MotionEvent.ACTION_HOVER_MOVE)
            hoverActivo = true
        } else if (hoverActivo) {
            enviarHover(decor, MotionEvent.ACTION_HOVER_EXIT)
            hoverActivo = false
        }
    }

    /**
     * Un toque «de dedo» sintético. Lleva herramienta FINGER y su propio dispositivo: antes salía con
     * herramienta desconocida y compartía el id 0 con el «ratón» del hover, y Compose se confundía
     * (una pulsación sí y otra no llegaba a los botones).
     */
    private fun tactil(down: Long, t: Long, accion: Int, x: Float, y: Float): MotionEvent {
        val pp = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER })
        val pc = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = 1f })
        return MotionEvent.obtain(down, t, accion, 1, pp, pc, 0, 0, 1f, 1f, DISPOSITIVO_DEDO, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
    }

    private fun raton(t: Long, accion: Int, x: Float, y: Float): MotionEvent {
        val pp = arrayOf(MotionEvent.PointerProperties().apply { id = 1; toolType = MotionEvent.TOOL_TYPE_MOUSE })
        val pc = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y })
        return MotionEvent.obtain(t, t, accion, 1, pp, pc, 0, 0, 1f, 1f, DISPOSITIVO_RATON, 0, InputDevice.SOURCE_MOUSE, 0)
    }

    private const val DISPOSITIVO_DEDO = 9101
    private const val DISPOSITIVO_RATON = 9102

    private fun enviarHover(decor: android.view.View, accion: Int) {
        val ahora = SystemClock.uptimeMillis()
        val e = raton(ahora, accion, pos.x, pos.y)
        decor.dispatchGenericMotionEvent(e)
        e.recycle()
    }

    /**
     * Empujar contra el borde sobre una vista propia (historial, favoritos…):
     * un deslizamiento corto que la lista de Compose entiende como scroll.
     */
    private fun arrastrar(decor: android.view.View, dx: Float, dy: Float) {
        // El puntero está pegado al borde de la pantalla (fuera de la lista que se
        // quiere mover): el deslizamiento se hace por dentro, en la misma columna.
        val p0 = Offset(
            if (dx != 0f) decor.width / 2f else pos.x.coerceIn(2f, decor.width - 2f),
            if (dy != 0f) decor.height / 2f else pos.y.coerceIn(2f, decor.height - 2f),
        )
        val ini = SystemClock.uptimeMillis()
        val pasos = 4
        var t = ini
        var e = tactil(ini, t, MotionEvent.ACTION_DOWN, p0.x, p0.y)
        decor.dispatchTouchEvent(e); e.recycle()
        for (i in 1..pasos) {
            t += 16
            // El contenido sube cuando el dedo sube: se invierte el empuje.
            e = tactil(ini, t, MotionEvent.ACTION_MOVE, p0.x - dx * i / pasos, p0.y - dy * i / pasos)
            decor.dispatchTouchEvent(e); e.recycle()
        }
        e = tactil(ini, t + 16, MotionEvent.ACTION_UP, p0.x - dx, p0.y - dy)
        decor.dispatchTouchEvent(e); e.recycle()
    }

    private fun toque(decor: android.view.View, accion: Int) {
        val ahora = SystemClock.uptimeMillis()
        if (accion == MotionEvent.ACTION_DOWN) {
            downTime = ahora
            // El «ratón» del hover se retira antes de pulsar: un dedo y un ratón a la vez no existen.
            if (hoverActivo) {
                enviarHover(decor, MotionEvent.ACTION_HOVER_EXIT)
                hoverActivo = false
            }
        }
        val e = tactil(downTime, ahora, accion, pos.x, pos.y)
        decor.dispatchTouchEvent(e)
        e.recycle()
        // Al soltar, el botón sigue «iluminado» por el cursor.
        if (accion == MotionEvent.ACTION_UP || accion == MotionEvent.ACTION_CANCEL) hover(decor)
    }
}

/**
 * Marca una lista desplazable de la interfaz para que el cursor del mando pueda
 * moverla empujando contra su borde superior o inferior.
 */
fun Modifier.zonaScroll(estado: androidx.compose.foundation.ScrollState): Modifier = composed {
    val id = remember { Any() }
    DisposableEffect(id) { onDispose { Puntero.quitarZona(id) } }
    onGloballyPositioned { Puntero.registrarZona(id, Puntero.Zona(it.boundsInWindow(), estado)) }
}

/** Dibuja el puntero encima de todo (no intercepta toques). */
@Composable
fun PunteroOverlay() {
    if (!Puntero.activo) return
    var origen by remember { mutableStateOf(Offset.Zero) }
    var tenue by remember { mutableStateOf(false) }
    LaunchedEffect(Puntero.movimiento, Puntero.pulsado) {
        tenue = false
        delay(3500)
        tenue = true
    }
    // Aviso de «se está desplazando»: pastilla blanca redondeada en el borde por el
    // que se mueve la página, sin importar si el cursor está atenuado.
    Canvas(Modifier.fillMaxSize()) {
        val ind = Puntero.indicador
        // En las páginas web, arriba y abajo el aviso es el asa que crece (ver BrowserScreen).
        if (ind != 0 && !(Puntero.esWeb && (ind == 1 || ind == 2))) {
            val largo = 140.dp.toPx()
            val grueso = 6.dp.toPx()
            val m = 8.dp.toPx()
            val color = Color.White.copy(alpha = 0.7f)
            val radio = androidx.compose.ui.geometry.CornerRadius(grueso / 2)
            when (ind) {
                1 -> drawRoundRect(color, Offset((size.width - largo) / 2, m), androidx.compose.ui.geometry.Size(largo, grueso), radio)
                2 -> drawRoundRect(color, Offset((size.width - largo) / 2, size.height - m - grueso), androidx.compose.ui.geometry.Size(largo, grueso), radio)
                3 -> drawRoundRect(color, Offset(m, (size.height - largo) / 2), androidx.compose.ui.geometry.Size(grueso, largo), radio)
                else -> drawRoundRect(color, Offset(size.width - m - grueso, (size.height - largo) / 2), androidx.compose.ui.geometry.Size(grueso, largo), radio)
            }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                origen = it.positionInWindow()
                Puntero.origenY = origen.y
                Puntero.altoVentana = it.size.height.toFloat()
                Puntero.centrarSiHaceFalta(it.boundsInWindow().center)
            }
            .alpha(
                when {
                    Puntero.pulsado || !tenue -> 1f
                    Puntero.ocultarEnReposo -> 0f
                    else -> 0.4f
                },
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val p = Puntero.pos - origen
            // Mitad del tamaño anterior (~17 dp de alto), flecha blanca de contorno
            // oscuro al estilo de los punteros de escritorio de Apple.
            val s = 0.62f * density
            if (Puntero.pulsado) {
                drawCircle(Ambar.copy(alpha = 0.28f), radius = 15.dp.toPx(), center = p)
                drawCircle(Ambar, radius = 15.dp.toPx(), center = p, style = Stroke(2.dp.toPx()))
            }
            // Modo corto: un aro alrededor del punto caliente avisa de que va fino.
            if (Puntero.modoFino) {
                drawCircle(Color.White.copy(alpha = 0.9f), radius = 11.dp.toPx(), center = p, style = Stroke(1.5.dp.toPx()))
                drawCircle(Ambar, radius = 2.dp.toPx(), center = p)
            }
            // Punta en (0, 0): el «punto caliente» del clic.
            val flecha = Path().apply {
                moveTo(p.x, p.y)
                lineTo(p.x, p.y + 25f * s)
                lineTo(p.x + 6.2f * s, p.y + 19.6f * s)
                lineTo(p.x + 10.4f * s, p.y + 29f * s)
                lineTo(p.x + 14.2f * s, p.y + 27.3f * s)
                lineTo(p.x + 10f * s, p.y + 18.2f * s)
                lineTo(p.x + 17.6f * s, p.y + 18f * s)
                close()
            }
            val desp = Offset(1.2.dp.toPx(), 1.6.dp.toPx())
            // Sombra suave debajo, contorno oscuro fino y relleno blanco.
            drawPath(Path().apply { addPath(flecha, desp) },Color.Black.copy(alpha = 0.28f), style = Stroke(4.dp.toPx(), join = StrokeJoin.Round))
            drawPath(flecha, Noche, style = Stroke(3.dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round))
            drawPath(flecha, Color.White)
            // Modo desplazar: dos puntitas (↕) junto a la flecha avisan de que las flechas
            // del mando mueven la lista que hay debajo, no el cursor.
            if (Puntero.modoScroll) {
                val cx = p.x + 26f * s
                val cy = p.y + 14f * s
                val a = 6.dp.toPx()
                val arriba = Path().apply {
                    moveTo(cx, cy - 2.dp.toPx() - a); lineTo(cx - a, cy - 2.dp.toPx()); lineTo(cx + a, cy - 2.dp.toPx()); close()
                }
                val abajo = Path().apply {
                    moveTo(cx, cy + 2.dp.toPx() + a); lineTo(cx - a, cy + 2.dp.toPx()); lineTo(cx + a, cy + 2.dp.toPx()); close()
                }
                drawCircle(Color.Black.copy(alpha = 0.55f), radius = a * 1.7f, center = Offset(cx, cy))
                drawPath(arriba, Ambar)
                drawPath(abajo, Ambar)
            }
        }
    }
}
