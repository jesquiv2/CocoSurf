package com.antena.tv.cursor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.antena.tv.ui.Ambar
import com.antena.tv.ui.AntenaType
import com.antena.tv.ui.Azul
import com.antena.tv.ui.Casco
import com.antena.tv.ui.Escudo
import com.antena.tv.ui.Fosforo
import com.antena.tv.ui.FosforoTenue
import kotlinx.coroutines.delay

private val LINKS = listOf(
    "youtube.com", "wikipedia.org",
    "correo.com", "noticias.com",
    "tienda.com", "foro.net",
)

@Composable
fun CursorScreen() {
    var cursorOn by remember { mutableStateOf(false) }
    var posRoot by remember { mutableStateOf(Offset.Zero) }
    var inited by remember { mutableStateOf(false) }
    var boxSize by remember { mutableStateOf(Size.Zero) }
    var boxOrigin by remember { mutableStateOf(Offset.Zero) }
    var status by remember { mutableStateOf("Sin clics todavía") }
    var flashId by remember { mutableStateOf(-1) }
    var focusedId by remember { mutableStateOf(-1) }
    var streak by remember { mutableStateOf(0) }
    var lastDir by remember { mutableStateOf<Key?>(null) }
    var lastMoveAt by remember { mutableStateOf(0L) }
    var downAt by remember { mutableStateOf(0L) }
    val bounds = remember { mutableStateMapOf<Int, Rect>() }
    val density = LocalDensity.current
    val firstCard = remember { FocusRequester() }

    LaunchedEffect(boxSize, boxOrigin) {
        if (!inited && boxSize != Size.Zero) {
            posRoot = Offset(
                boxOrigin.x + boxSize.width / 2f,
                boxOrigin.y + boxSize.height / 2f,
            )
            inited = true
        }
    }
    LaunchedEffect(cursorOn) {
        if (cursorOn) firstCard.requestFocus()
    }
    LaunchedEffect(flashId) {
        if (flashId != -1) {
            delay(350)
            flashId = -1
        }
    }

    fun activate(id: Int) {
        if (id in LINKS.indices) {
            status = "Clic en «${LINKS[id]}»"
            flashId = id
        }
    }

    fun tapAt(p: Offset) {
        val hit = bounds.entries.firstOrNull { it.value.contains(p) }
        if (hit != null) {
            activate(hit.key)
        } else {
            status = "El retículo no está sobre ningún enlace"
        }
    }

    fun handleKey(event: KeyEvent): Boolean {
        val k = event.key

        if (k == Key.Menu) {
            if (event.type == KeyEventType.KeyUp) {
                cursorOn = !cursorOn
            }
            return true
        }
        if (k == Key.Back && cursorOn) {
            if (event.type == KeyEventType.KeyUp) {
                cursorOn = false
            }
            return true
        }

        val isDir = k == Key.DirectionUp || k == Key.DirectionDown ||
            k == Key.DirectionLeft || k == Key.DirectionRight
        val isOk = k == Key.DirectionCenter || k == Key.Enter || k == Key.NumPadEnter

        if (cursorOn && isDir) {
            if (event.type == KeyEventType.KeyDown) {
                val now = System.currentTimeMillis()
                streak = if (k == lastDir && now - lastMoveAt < 300) {
                    (streak + 1).coerceAtMost(6)
                } else {
                    0
                }
                lastDir = k
                lastMoveAt = now
                val step = with(density) { (36f + streak * 30f).dp.toPx() }
                var dx = 0f
                var dy = 0f
                if (k == Key.DirectionLeft) dx = -step
                if (k == Key.DirectionRight) dx = step
                if (k == Key.DirectionUp) dy = -step
                if (k == Key.DirectionDown) dy = step
                val minX = boxOrigin.x
                val minY = boxOrigin.y
                posRoot = Offset(
                    (posRoot.x + dx).coerceIn(minX, minX + boxSize.width),
                    (posRoot.y + dy).coerceIn(minY, minY + boxSize.height),
                )
            }
            return true
        }
        if (cursorOn && isOk) {
            if (event.type == KeyEventType.KeyUp) {
                tapAt(posRoot)
            }
            return true
        }
        if (!cursorOn && isOk) {
            if (event.type == KeyEventType.KeyDown) {
                downAt = System.currentTimeMillis()
            }
            if (event.type == KeyEventType.KeyUp) {
                val held = System.currentTimeMillis() - downAt
                if (held > 500) {
                    cursorOn = true
                } else {
                    activate(focusedId)
                }
            }
            return true
        }
        return false
    }

    val hint = if (cursorOn) {
        "Cursor activo · flechas: mover · OK: clic · Menú o Atrás: salir"
    } else {
        "Flechas: mover el foco · OK: abrir · mantener OK o Menú: cursor"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .onGloballyPositioned {
                val r = it.boundsInRoot()
                boxOrigin = Offset(r.left, r.top)
            }
            .onPreviewKeyEvent { handleKey(it) },
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Casco)
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    androidx.compose.material3.Text(
                        "https://ejemplo.com",
                        style = AntenaType.mono,
                        color = Fosforo,
                    )
                }
                Spacer(Modifier.width(14.dp))
                androidx.compose.material3.Text(
                    "página de prueba",
                    style = AntenaType.meta,
                    color = Azul,
                )
            }

            Spacer(Modifier.height(24.dp))

            LINKS.chunked(2).forEachIndexed { rowIdx, pair ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 16.dp),
                ) {
                    pair.forEachIndexed { colIdx, label ->
                        val id = rowIdx * 2 + colIdx
                        LinkCard(
                            id = id,
                            label = label,
                            bounds = bounds,
                            boxOrigin = boxOrigin,
                            flashId = flashId,
                            onFocused = { focusedId = id },
                            onClick = { activate(id) },
                            requester = if (id == 0) firstCard else null,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                    if (pair.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            androidx.compose.material3.Text(
                status,
                style = AntenaType.body,
                color = if (flashId != -1) Escudo else FosforoTenue,
            )
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.Text(
                hint,
                style = AntenaType.monoSmall,
                color = Azul,
            )
        }

        if (cursorOn) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(posRoot.x - boxOrigin.x, posRoot.y - boxOrigin.y)
                val r = 26.dp.toPx()
                drawCircle(
                    color = Ambar.copy(alpha = 0.18f),
                    radius = r,
                    center = c,
                )
                drawCircle(
                    color = Ambar,
                    radius = r,
                    center = c,
                    style = Stroke(width = 3.dp.toPx()),
                )
                drawCircle(
                    color = Ambar,
                    radius = 4.dp.toPx(),
                    center = c,
                )
                val tick = 10.dp.toPx()
                val w = 2.5.dp.toPx()
                drawLine(Ambar, Offset(c.x, c.y - r - tick), Offset(c.x, c.y - r), w, StrokeCap.Round)
                drawLine(Ambar, Offset(c.x, c.y + r), Offset(c.x, c.y + r + tick), w, StrokeCap.Round)
                drawLine(Ambar, Offset(c.x - r - tick, c.y), Offset(c.x - r, c.y), w, StrokeCap.Round)
                drawLine(Ambar, Offset(c.x + r, c.y), Offset(c.x + r + tick, c.y), w, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun LinkCard(
    id: Int,
    label: String,
    bounds: androidx.compose.runtime.snapshots.SnapshotStateMap<Int, Rect>,
    boxOrigin: Offset,
    flashId: Int,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    requester: FocusRequester?,
    modifier: Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    var m = modifier
        .onGloballyPositioned { coords ->
            val r = coords.boundsInRoot()
            bounds[id] = Rect(
                r.left - boxOrigin.x,
                r.top - boxOrigin.y,
                r.right - boxOrigin.x,
                r.bottom - boxOrigin.y,
            )
        }
        .onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) onFocused()
        }
    if (requester != null) {
        m = m.focusRequester(requester)
    }
    m = m
        .clip(shape)
        .background(if (flashId == id) Ambar.copy(alpha = 0.30f) else Casco)
        .border(
            width = if (focused) 3.dp else 1.dp,
            color = if (focused) Ambar else Azul.copy(alpha = 0.35f),
            shape = shape,
        )
        .clickable(onClick = onClick)
        .padding(horizontal = 22.dp, vertical = 18.dp)

    Column(m, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        androidx.compose.material3.Text(label, style = AntenaType.body, color = Fosforo)
        Spacer(Modifier.height(4.dp))
        androidx.compose.material3.Text(
            "enlace de prueba",
            style = AntenaType.meta,
            color = Azul,
        )
    }
}
