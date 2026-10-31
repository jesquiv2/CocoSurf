package com.antena.tv.browser

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import com.antena.tv.gecko.ExtInfo
import com.antena.tv.gecko.GeckoMotor
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.antena.tv.db.DownloadEntry
import com.antena.tv.db.Favorite
import com.antena.tv.db.Folder
import com.antena.tv.db.HistoryEntry
import com.antena.tv.db.SearchEngine
import com.antena.tv.log.Bitacora
import com.antena.tv.log.LogEntry
import com.antena.tv.log.LogKind
import com.antena.tv.ui.ACENTOS
import com.antena.tv.ui.Alerta
import com.antena.tv.ui.EsquinaBoton
import com.antena.tv.ui.Interruptor
import com.antena.tv.ui.MuestraColor
import com.antena.tv.ui.Separador
import com.antena.tv.ui.EsquinaTarjeta
import com.antena.tv.ui.EsquinaVentana
import com.antena.tv.ui.cristal
import com.antena.tv.ui.Ambar
import com.antena.tv.ui.AntenaType
import com.antena.tv.ui.Azul
import com.antena.tv.ui.Casco
import com.antena.tv.ui.Escudo
import com.antena.tv.ui.Fosforo
import com.antena.tv.ui.FosforoTenue
import com.antena.tv.ui.Noche
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/* ── Handler de OK compartido (corto = acción, mantenido >0.5 s = larga) ── */
@Composable
fun rememberCenterHandler(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
): (KeyEvent) -> Boolean {
    val downAt = remember { mutableStateOf(0L) }
    return { ev ->
        val k = ev.key
        if (k == Key.DirectionCenter || k == Key.Enter || k == Key.NumPadEnter) {
            when (ev.type) {
                KeyEventType.KeyDown -> {
                    val now = System.currentTimeMillis()
                    // El sistema reenvía la tecla mientras se mantiene (key repeat):
                    // esos reenvíos NO reinician el cronómetro — sólo cuenta el primer
                    // KeyDOWN de la pulsación, igual que MainActivity (repeatCount 0).
                    // Un down huérfano de más de 5 s se descarta por obsoleto.
                    if (downAt.value == 0L || now - downAt.value > 5_000L) downAt.value = now
                }
                KeyEventType.KeyUp -> {
                    val started = downAt.value
                    downAt.value = 0L
                    when {
                        // KeyUP sin KeyDOWN previo en este nodo (el foco saltó, p. ej. al
                        // cerrarse el campo de dirección): se ignora para no disparar nada.
                        started == 0L -> Unit
                        onLongClick != null &&
                            System.currentTimeMillis() - started > 500 -> onLongClick()
                        else -> onClick()
                    }
                }
                else -> Unit
            }
            true
        } else {
            false
        }
    }
}

/* ── Iconos de la barra de herramientas (trazados propios, sin librería) ── */
enum class ToolIcon {
    Back, Reload, Home, Folder, Clock, Download, ArrowUp, Link, Image, Search, Star, NewTab,
    Gear, Text, Copy, Share, Power,
    // Coco Surf · rediseño: menú, candado, estrella vacía, complementos, info, pulso, bitácora.
    Menu, Lock, StarOutline, Puzzle, Info, Pulse, Doc, Chip,
    // Paneles rápidos de historial y descargas.
    Trash, Close, Pin, PinFilled, More, Folder2,
    // Menú de página: adelante, traducir, imprimir.
    Forward, Globe, Print,
}

/**
 * Dibuja un icono del catálogo al tamaño que se pida (los chips de pestaña usan
 * 18 dp y 13 dp; el resto de la interfaz mantiene los 18 dp de siempre).
 */
@Composable
fun ToolIconGlyph(kind: ToolIcon, color: Color, size: Dp = 18.dp) {
    ToolIconCanvas(kind, color, size)
}

@Composable
private fun ToolIconCanvas(kind: ToolIcon, color: Color, lado: Dp = 18.dp) {
    // Ojo: el parámetro NO se llama `size` porque dentro del Canvas `size` es el
    // tamaño del lienzo (DrawScope.size) y lo sombrearía.
    Canvas(Modifier.size(lado)) {
        val w = size.width
        val h = size.height
        val sw = 2.2.dp.toPx()
        when (kind) {
            ToolIcon.Back -> {
                drawLine(color, Offset(0.18f * w, 0.5f * h), Offset(0.86f * w, 0.5f * h), sw, cap = StrokeCap.Round)
                drawLine(color, Offset(0.46f * w, 0.2f * h), Offset(0.18f * w, 0.5f * h), sw, cap = StrokeCap.Round)
                drawLine(color, Offset(0.46f * w, 0.8f * h), Offset(0.18f * w, 0.5f * h), sw, cap = StrokeCap.Round)
            }
            ToolIcon.Reload -> {
                val cx = 0.5f * w
                val cy = 0.52f * h
                val r = 0.32f * w
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 300f,
                    useCenter = false,
                    topLeft = Offset(cx - r, cy - r),
                    size = Size(2 * r, 2 * r),
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
                // Punta en el ángulo final (210°) apuntando en la dirección del barrido.
                val rad = Math.toRadians(210.0)
                val ex = (cx + r * kotlin.math.cos(rad)).toFloat()
                val ey = (cy + r * kotlin.math.sin(rad)).toFloat()
                val tx = -kotlin.math.sin(rad).toFloat()
                val ty = kotlin.math.cos(rad).toFloat()
                val len = 0.13f * w
                val half = 0.08f * w
                val tip = Offset(ex + tx * len, ey + ty * len)
                val b1 = Offset(ex - ty * half, ey + tx * half)
                val b2 = Offset(ex + ty * half, ey - tx * half)
                val tri = Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo(b1.x, b1.y)
                    lineTo(b2.x, b2.y)
                    close()
                }
                drawPath(tri, color)
            }
            ToolIcon.Home -> {
                val path = Path().apply {
                    moveTo(0.14f * w, 0.52f * h)
                    lineTo(0.5f * w, 0.2f * h)
                    lineTo(0.86f * w, 0.52f * h)
                    lineTo(0.86f * w, 0.84f * h)
                    lineTo(0.14f * w, 0.84f * h)
                    close()
                }
                drawPath(path, color, style = Stroke(sw, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                drawLine(color, Offset(0.42f * w, 0.84f * h), Offset(0.42f * w, 0.62f * h), sw, cap = StrokeCap.Round)
                drawLine(color, Offset(0.58f * w, 0.84f * h), Offset(0.58f * w, 0.62f * h), sw, cap = StrokeCap.Round)
                drawLine(color, Offset(0.42f * w, 0.62f * h), Offset(0.58f * w, 0.62f * h), sw, cap = StrokeCap.Round)
            }
            ToolIcon.Folder -> {
                val path = Path().apply {
                    moveTo(0.14f * w, 0.30f * h)
                    lineTo(0.40f * w, 0.30f * h)
                    lineTo(0.47f * w, 0.38f * h)
                    lineTo(0.86f * w, 0.38f * h)
                    lineTo(0.86f * w, 0.80f * h)
                    lineTo(0.14f * w, 0.80f * h)
                    close()
                }
                drawPath(path, color, style = Stroke(sw, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
            ToolIcon.Clock -> {
                val r = 0.32f * w
                drawCircle(color, radius = r, style = Stroke(sw))
                drawLine(color, Offset(0.5f * w, 0.5f * h), Offset(0.5f * w, 0.28f * h), sw, cap = StrokeCap.Round)
                drawLine(color, Offset(0.5f * w, 0.5f * h), Offset(0.65f * w, 0.58f * h), sw, cap = StrokeCap.Round)
            }
            ToolIcon.Download -> {
                drawLine(color, Offset(0.5f * w, 0.16f * h), Offset(0.5f * w, 0.58f * h), sw, cap = StrokeCap.Round)
                drawLine(color, Offset(0.32f * w, 0.42f * h), Offset(0.5f * w, 0.58f * h), sw, cap = StrokeCap.Round)
                drawLine(color, Offset(0.68f * w, 0.42f * h), Offset(0.5f * w, 0.58f * h), sw, cap = StrokeCap.Round)
                drawLine(color, Offset(0.20f * w, 0.78f * h), Offset(0.80f * w, 0.78f * h), sw, cap = StrokeCap.Round)
            }
            ToolIcon.ArrowUp -> {
                drawLine(color, Offset(0.5f * w, 0.84f * h), Offset(0.5f * w, 0.18f * h), sw, cap = StrokeCap.Round)
                drawLine(color, Offset(0.24f * w, 0.44f * h), Offset(0.5f * w, 0.18f * h), sw, cap = StrokeCap.Round)
                drawLine(color, Offset(0.76f * w, 0.44f * h), Offset(0.5f * w, 0.18f * h), sw, cap = StrokeCap.Round)
            }
            // Fase 5 · menú contextual.
            ToolIcon.Link -> {
                val stroke = Stroke(sw, cap = StrokeCap.Round)
                rotate(-45f) {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(0.06f * w, 0.36f * h),
                        size = Size(0.42f * w, 0.28f * h),
                        cornerRadius = CornerRadius(0.14f * h),
                        style = stroke,
                    )
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(0.52f * w, 0.36f * h),
                        size = Size(0.42f * w, 0.28f * h),
                        cornerRadius = CornerRadius(0.14f * h),
                        style = stroke,
                    )
                }
            }
            ToolIcon.Image -> {
                val stroke = Stroke(sw, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0.10f * w, 0.18f * h),
                    size = Size(0.80f * w, 0.64f * h),
                    cornerRadius = CornerRadius(0.10f * w),
                    style = stroke,
                )
                drawCircle(color, radius = 0.08f * w, center = Offset(0.34f * w, 0.40f * h))
                drawPath(
                    Path().apply {
                        moveTo(0.22f * w, 0.74f * h)
                        lineTo(0.44f * w, 0.50f * h)
                        lineTo(0.58f * w, 0.64f * h)
                        lineTo(0.72f * w, 0.52f * h)
                        lineTo(0.86f * w, 0.74f * h)
                    },
                    color,
                    style = stroke,
                )
            }
            // Fase 9 · buscar pestañas.
            ToolIcon.Search -> {
                val cx = 0.42f * w
                val cy = 0.42f * h
                val r = 0.24f * w
                drawCircle(
                    color = color,
                    radius = r,
                    center = Offset(cx, cy),
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
                drawLine(
                    color,
                    Offset(cx + r * 0.74f, cy + r * 0.74f),
                    Offset(0.86f * w, 0.86f * h),
                    sw,
                    cap = StrokeCap.Round,
                )
            }
            // Estrella de favoritos: trazado propio de 5 puntas, rellena.
            ToolIcon.Star -> {
                val cx = 0.5f * w
                val cy = 0.53f * h
                val rout = 0.42f * w
                val rin = 0.18f * w
                val path = Path()
                for (i in 0 until 10) {
                    val ang = Math.toRadians(-90.0 + i * 36.0)
                    val r = if (i % 2 == 0) rout else rin
                    val x = (cx + r * kotlin.math.cos(ang)).toFloat()
                    val y = (cy + r * kotlin.math.sin(ang)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, color)
            }
            // Configuración (Paso 2): engranaje de anillo grueso y 8 dientes.
            ToolIcon.Gear -> {
                val cx = 0.5f * w
                val cy = 0.5f * h
                drawCircle(
                    color = color,
                    radius = 0.22f * w,
                    center = Offset(cx, cy),
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
                repeat(8) { i ->
                    val a = Math.toRadians(i * 45.0)
                    val dx = kotlin.math.cos(a).toFloat()
                    val dy = kotlin.math.sin(a).toFloat()
                    drawLine(
                        color,
                        Offset(cx + dx * 0.30f * w, cy + dy * 0.30f * h),
                        Offset(cx + dx * 0.44f * w, cy + dy * 0.44f * h),
                        sw,
                        cap = StrokeCap.Round,
                    )
                }
            }
            // Pestaña nueva: hoja redondeada con un "+" dentro.
            ToolIcon.NewTab -> {
                val stroke = Stroke(sw, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0.10f * w, 0.18f * h),
                    size = Size(0.80f * w, 0.64f * h),
                    cornerRadius = CornerRadius(0.12f * w),
                    style = stroke,
                )
                drawLine(color, Offset(0.50f * w, 0.34f * h), Offset(0.50f * w, 0.66f * h), sw, cap = StrokeCap.Round)
                drawLine(color, Offset(0.34f * w, 0.50f * h), Offset(0.66f * w, 0.50f * h), sw, cap = StrokeCap.Round)
            }
            // Paso 5 · menú contextual: texto ("T" clásica).
            ToolIcon.Text -> {
                drawLine(color, Offset(0.50f * w, 0.16f * h), Offset(0.50f * w, 0.84f * h), sw, cap = StrokeCap.Round)
                drawLine(color, Offset(0.16f * w, 0.16f * h), Offset(0.84f * w, 0.16f * h), sw, cap = StrokeCap.Round)
                drawLine(color, Offset(0.34f * w, 0.84f * h), Offset(0.66f * w, 0.84f * h), sw, cap = StrokeCap.Round)
            }
            // Paso 5 · menú contextual: copiar (dos hojas, la delantera rellena).
            ToolIcon.Copy -> {
                val stroke = Stroke(sw, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0.14f * w, 0.14f * h),
                    size = Size(0.50f * w, 0.50f * h),
                    cornerRadius = CornerRadius(0.08f * w),
                    style = stroke,
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0.36f * w, 0.36f * h),
                    size = Size(0.50f * w, 0.50f * h),
                    cornerRadius = CornerRadius(0.08f * w),
                )
            }
            // Paso 7 · activar/desactivar una extensión (glifo de encendido).
            ToolIcon.Power -> {
                drawArc(
                    color = color,
                    startAngle = -60f,
                    sweepAngle = 300f,
                    useCenter = false,
                    topLeft = Offset(0.14f * w, 0.18f * h),
                    size = Size(0.72f * w, 0.72f * h),
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
                drawLine(color, Offset(0.5f * w, 0.10f * h), Offset(0.5f * w, 0.46f * h), sw, cap = StrokeCap.Round)
            }
            ToolIcon.Menu -> {
                drawLine(color, Offset(0.18f * w, 0.28f * h), Offset(0.82f * w, 0.28f * h), sw, cap = StrokeCap.Round)
                drawLine(color, Offset(0.18f * w, 0.50f * h), Offset(0.82f * w, 0.50f * h), sw, cap = StrokeCap.Round)
                drawLine(color, Offset(0.18f * w, 0.72f * h), Offset(0.82f * w, 0.72f * h), sw, cap = StrokeCap.Round)
            }
            // Candado (https): cuerpo redondeado y arco.
            ToolIcon.Lock -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0.22f * w, 0.46f * h),
                    size = Size(0.56f * w, 0.38f * h),
                    cornerRadius = CornerRadius(0.08f * w),
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
                drawArc(
                    color = color,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(0.33f * w, 0.14f * h),
                    size = Size(0.34f * w, 0.40f * h),
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
                drawLine(color, Offset(0.5f * w, 0.60f * h), Offset(0.5f * w, 0.70f * h), sw, cap = StrokeCap.Round)
            }
            ToolIcon.StarOutline -> {
                val cx = 0.5f * w
                val cy = 0.53f * h
                val path = Path()
                for (i in 0 until 10) {
                    val ang = Math.toRadians(-90.0 + i * 36.0)
                    val r = if (i % 2 == 0) 0.40f * w else 0.18f * w
                    val x = (cx + r * kotlin.math.cos(ang)).toFloat()
                    val y = (cy + r * kotlin.math.sin(ang)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, color, style = Stroke(sw, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
            // Complementos: pieza de rompecabezas (cuerpo + dos botones).
            ToolIcon.Puzzle -> {
                val stroke = Stroke(sw, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0.16f * w, 0.32f * h),
                    size = Size(0.56f * w, 0.52f * h),
                    cornerRadius = CornerRadius(0.08f * w),
                    style = stroke,
                )
                drawCircle(color, radius = 0.10f * w, center = Offset(0.44f * w, 0.24f * h), style = stroke)
                drawCircle(color, radius = 0.10f * w, center = Offset(0.80f * w, 0.58f * h), style = stroke)
            }
            ToolIcon.Info -> {
                drawCircle(color, radius = 0.36f * w, style = Stroke(sw))
                drawCircle(color, radius = 0.05f * w, center = Offset(0.5f * w, 0.32f * h))
                drawLine(color, Offset(0.5f * w, 0.46f * h), Offset(0.5f * w, 0.70f * h), sw, cap = StrokeCap.Round)
            }
            // Diagnóstico: latido.
            ToolIcon.Pulse -> {
                val p = Path().apply {
                    moveTo(0.08f * w, 0.54f * h)
                    lineTo(0.30f * w, 0.54f * h)
                    lineTo(0.40f * w, 0.28f * h)
                    lineTo(0.56f * w, 0.78f * h)
                    lineTo(0.66f * w, 0.46f * h)
                    lineTo(0.92f * w, 0.46f * h)
                }
                drawPath(p, color, style = Stroke(sw, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
            ToolIcon.Forward -> {
                drawLine(color, Offset(0.82f * w, 0.5f * h), Offset(0.14f * w, 0.5f * h), sw, cap = StrokeCap.Round)
                drawLine(color, Offset(0.54f * w, 0.2f * h), Offset(0.82f * w, 0.5f * h), sw, cap = StrokeCap.Round)
                drawLine(color, Offset(0.54f * w, 0.8f * h), Offset(0.82f * w, 0.5f * h), sw, cap = StrokeCap.Round)
            }
            // Globo: traducir la página.
            ToolIcon.Globe -> {
                val c = Offset(0.5f * w, 0.5f * h)
                drawCircle(color, radius = 0.36f * w, center = c, style = Stroke(sw))
                drawOval(
                    color = color,
                    topLeft = Offset(0.34f * w, 0.14f * h),
                    size = Size(0.32f * w, 0.72f * h),
                    style = Stroke(sw),
                )
                drawLine(color, Offset(0.14f * w, 0.5f * h), Offset(0.86f * w, 0.5f * h), sw, cap = StrokeCap.Round)
            }
            ToolIcon.Print -> {
                val j = androidx.compose.ui.graphics.StrokeJoin.Round
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0.12f * w, 0.36f * h),
                    size = Size(0.76f * w, 0.34f * h),
                    cornerRadius = CornerRadius(0.07f * w),
                    style = Stroke(sw, cap = StrokeCap.Round, join = j),
                )
                drawPath(
                    Path().apply {
                        moveTo(0.28f * w, 0.36f * h); lineTo(0.28f * w, 0.14f * h)
                        lineTo(0.72f * w, 0.14f * h); lineTo(0.72f * w, 0.36f * h)
                    },
                    color, style = Stroke(sw, cap = StrokeCap.Round, join = j),
                )
                drawRect(color, topLeft = Offset(0.28f * w, 0.60f * h), size = Size(0.44f * w, 0.26f * h), style = Stroke(sw))
            }
            ToolIcon.Trash -> {
                val j = androidx.compose.ui.graphics.StrokeJoin.Round
                drawLine(color, Offset(0.18f * w, 0.28f * h), Offset(0.82f * w, 0.28f * h), sw, cap = StrokeCap.Round)
                drawPath(
                    Path().apply {
                        moveTo(0.38f * w, 0.28f * h); lineTo(0.38f * w, 0.16f * h)
                        lineTo(0.62f * w, 0.16f * h); lineTo(0.62f * w, 0.28f * h)
                    },
                    color, style = Stroke(sw, cap = StrokeCap.Round, join = j),
                )
                drawPath(
                    Path().apply {
                        moveTo(0.27f * w, 0.28f * h); lineTo(0.32f * w, 0.84f * h)
                        lineTo(0.68f * w, 0.84f * h); lineTo(0.73f * w, 0.28f * h)
                    },
                    color, style = Stroke(sw, cap = StrokeCap.Round, join = j),
                )
                drawLine(color, Offset(0.43f * w, 0.42f * h), Offset(0.43f * w, 0.70f * h), sw, cap = StrokeCap.Round)
                drawLine(color, Offset(0.57f * w, 0.42f * h), Offset(0.57f * w, 0.70f * h), sw, cap = StrokeCap.Round)
            }
            ToolIcon.Close -> {
                drawLine(color, Offset(0.24f * w, 0.24f * h), Offset(0.76f * w, 0.76f * h), sw, cap = StrokeCap.Round)
                drawLine(color, Offset(0.76f * w, 0.24f * h), Offset(0.24f * w, 0.76f * h), sw, cap = StrokeCap.Round)
            }
            // Chincheta: vacía = panel flotante, rellena = fijado al costado.
            ToolIcon.Pin, ToolIcon.PinFilled -> {
                val j = androidx.compose.ui.graphics.StrokeJoin.Round
                val cuerpo = Path().apply {
                    moveTo(0.36f * w, 0.14f * h); lineTo(0.64f * w, 0.14f * h)
                    lineTo(0.62f * w, 0.50f * h); lineTo(0.38f * w, 0.50f * h)
                    close()
                }
                if (kind == ToolIcon.PinFilled) drawPath(cuerpo, color)
                else drawPath(cuerpo, color, style = Stroke(sw, cap = StrokeCap.Round, join = j))
                drawLine(color, Offset(0.26f * w, 0.14f * h), Offset(0.74f * w, 0.14f * h), sw, cap = StrokeCap.Round)
                drawLine(color, Offset(0.24f * w, 0.50f * h), Offset(0.76f * w, 0.50f * h), sw, cap = StrokeCap.Round)
                drawLine(color, Offset(0.5f * w, 0.50f * h), Offset(0.5f * w, 0.90f * h), sw, cap = StrokeCap.Round)
            }
            ToolIcon.More -> {
                listOf(0.22f, 0.5f, 0.78f).forEach { x ->
                    drawCircle(color, radius = 0.075f * w, center = Offset(x * w, 0.5f * h))
                }
            }
            // Carpeta abierta (abrir ubicación del archivo).
            ToolIcon.Folder2 -> {
                val j = androidx.compose.ui.graphics.StrokeJoin.Round
                drawPath(
                    Path().apply {
                        moveTo(0.12f * w, 0.78f * h); lineTo(0.12f * w, 0.28f * h)
                        lineTo(0.38f * w, 0.28f * h); lineTo(0.46f * w, 0.36f * h)
                        lineTo(0.82f * w, 0.36f * h); lineTo(0.82f * w, 0.50f * h)
                    },
                    color, style = Stroke(sw, cap = StrokeCap.Round, join = j),
                )
                drawPath(
                    Path().apply {
                        moveTo(0.12f * w, 0.78f * h); lineTo(0.24f * w, 0.50f * h)
                        lineTo(0.92f * w, 0.50f * h); lineTo(0.80f * w, 0.78f * h)
                        close()
                    },
                    color, style = Stroke(sw, cap = StrokeCap.Round, join = j),
                )
            }
            // Información del sistema: chip con patitas.
            ToolIcon.Chip -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0.26f * w, 0.26f * h),
                    size = Size(0.48f * w, 0.48f * h),
                    cornerRadius = CornerRadius(0.07f * w),
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
                listOf(0.38f, 0.62f).forEach { t ->
                    drawLine(color, Offset(t * w, 0.10f * h), Offset(t * w, 0.26f * h), sw, cap = StrokeCap.Round)
                    drawLine(color, Offset(t * w, 0.74f * h), Offset(t * w, 0.90f * h), sw, cap = StrokeCap.Round)
                    drawLine(color, Offset(0.10f * w, t * h), Offset(0.26f * w, t * h), sw, cap = StrokeCap.Round)
                    drawLine(color, Offset(0.74f * w, t * h), Offset(0.90f * w, t * h), sw, cap = StrokeCap.Round)
                }
            }
            // Bitácora: hoja con renglones.
            ToolIcon.Doc -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0.24f * w, 0.14f * h),
                    size = Size(0.52f * w, 0.72f * h),
                    cornerRadius = CornerRadius(0.08f * w),
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
                drawLine(color, Offset(0.36f * w, 0.38f * h), Offset(0.64f * w, 0.38f * h), sw, cap = StrokeCap.Round)
                drawLine(color, Offset(0.36f * w, 0.54f * h), Offset(0.64f * w, 0.54f * h), sw, cap = StrokeCap.Round)
                drawLine(color, Offset(0.36f * w, 0.70f * h), Offset(0.54f * w, 0.70f * h), sw, cap = StrokeCap.Round)
            }
            // Paso 5 · menú contextual: compartir (glifo de Android: 3 nodos unidos).
            ToolIcon.Share -> {
                val p1 = Offset(0.72f * w, 0.26f * h)
                val p2 = Offset(0.72f * w, 0.74f * h)
                val p3 = Offset(0.28f * w, 0.50f * h)
                drawLine(color, p3, p1, sw, cap = StrokeCap.Round)
                drawLine(color, p3, p2, sw, cap = StrokeCap.Round)
                drawCircle(color, 0.085f * w, p1)
                drawCircle(color, 0.085f * w, p2)
                drawCircle(color, 0.085f * w, p3)
            }
        }
    }
}

/* ── Chip genérico remoto: fondo hundido + filete sutil; el foco se marca en ámbar ── */
@Composable
fun RemoteChip(
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    accent: Boolean = false,
    small: Boolean = false,
    icon: ToolIcon? = null,
    enabled: Boolean = true,
    compact: Boolean = false,
    ghost: Boolean = false,
    /** Estado «abierto» (menú): píldora blanca con glifo oscuro, sin ámbar. */
    openState: Boolean = false,
    /** Paso 7 · icono REAL de la extensión (se dibuja en lugar del texto). */
    imagen: androidx.compose.ui.graphics.ImageBitmap? = null,
    /** Paso 7 · iniciales cuando la extensión no trae icono del motor. */
    glifo: String? = null,
    /** Paso 7 · extensión desactivada: misma forma, tono apagado. */
    tenue: Boolean = false,
    /** Salir / acción destructiva: tono rojo en reposo y al enfocar. */
    peligro: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    // Hover: el cursor del mando «pasa por encima» y el botón responde.
    val fuente = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hover by fuente.collectIsHoveredAsState()
    val center = rememberCenterHandler(onClick, onLongClick)
    val shape = RoundedCornerShape(12.dp)
    // Botones de solo icono (barras, paneles): sin fondo ni borde; sólo el foco o
    // el cursor encima los marcan. Los botones con texto conservan un fondo tenue.
    val soloIcono = icon != null || imagen != null || glifo != null
    val fg = when {
        openState -> Ambar
        focused && peligro -> Noche
        peligro -> Alerta
        focused && accent -> Noche
        focused -> Ambar
        hover && enabled -> Ambar
        accent -> Ambar
        tenue -> FosforoTenue
        else -> Fosforo
    }
    val bg = when {
        openState -> Ambar.copy(alpha = 0.16f)
        focused && peligro -> Alerta
        focused && accent -> Ambar
        focused -> Ambar.copy(alpha = 0.18f)
        hover && enabled && peligro -> Alerta.copy(alpha = 0.22f)
        hover && enabled -> Ambar.copy(alpha = 0.13f)
        accent -> Ambar.copy(alpha = 0.14f)
        peligro -> Alerta.copy(alpha = 0.10f)
        ghost -> androidx.compose.ui.graphics.Color.Transparent
        soloIcono -> androidx.compose.ui.graphics.Color.Transparent
        // Minimalista: una tinta apenas visible, sin marco; el foco pone el color.
        else -> Fosforo.copy(alpha = 0.07f)
    }
    val border = when {
        openState -> androidx.compose.ui.graphics.Color.Transparent
        focused && peligro -> Alerta
        focused -> Ambar
        hover && enabled -> (if (peligro) Alerta else Ambar).copy(alpha = 0.55f)
        peligro -> Alerta.copy(alpha = 0.45f)
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    Row(
        Modifier
            .hoverable(fuente)
            .onFocusChanged { focused = it.isFocused }
            .then(if (enabled) Modifier.focusable() else Modifier)
            .then(
                when {
                    compact -> Modifier.height(18.dp)
                    // 38 dp: legible a 3 m y un blanco cómodo para el cursor.
                    small -> Modifier.height(38.dp)
                    else -> Modifier
                },
            )
            .clip(shape)
            .background(bg)
            .border(if (focused) 1.5.dp else 1.dp, border, shape)
            .then(if (enabled) Modifier.onKeyEvent(center) else Modifier)
            .then(
                if (enabled) Modifier.pointerInput(onClick) { detectTapGestures { onClick() } }
                else Modifier,
            )
            .padding(
                horizontal = when {
                    compact -> 8.dp
                    small -> 11.dp
                    else -> 18.dp
                },
                vertical = if (small || compact) 0.dp else 11.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Paso 7 · una extensión se representa con su icono (real o las
        // iniciales de su nombre) y, si informa contador, con él al lado.
        val conIcono = icon != null || imagen != null || glifo != null
        val estilo = when {
            compact -> AntenaType.meta.copy(fontSize = 10.sp)
            small -> AntenaType.meta
            else -> AntenaType.body
        }
        if (conIcono) {
            when {
                icon != null -> ToolIconCanvas(icon, fg, if (small && !compact) 22.dp else 18.dp)
                imagen != null -> Image(
                    bitmap = imagen,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .alpha(if (tenue) 0.45f else 1f),
                )
                else -> Text(
                    glifo ?: "",
                    style = estilo.copy(fontSize = 12.sp),
                    color = fg,
                    modifier = Modifier.alpha(if (tenue) 0.6f else 1f),
                )
            }
            if (label.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Text(label, style = estilo, color = fg)
            }
        } else {
            Text(label, style = estilo, color = fg)
        }
    }
}

/**
 * Paso 7 · identidad visual de una extensión donde haga falta (chip de la
 * barra, panel individual y lista de Complementos): el **icono real** que
 * entrega el motor (`MetaData.getIcon()`) y, si no lo hay, la inicial estable de
 * su nombre — nunca un hueco en blanco ni un icono genérico compartido.
 */
@Composable
fun ExtIcono(
    nombre: String,
    imagen: androidx.compose.ui.graphics.ImageBitmap?,
    activa: Boolean,
    lado: Dp = 18.dp,
) {
    val tenue = if (activa) 1f else 0.45f
    if (imagen != null) {
        Image(
            bitmap = imagen,
            contentDescription = null,
            modifier = Modifier.size(lado).alpha(tenue),
        )
    } else {
        Box(
            Modifier
                .size(lado)
                .clip(RoundedCornerShape(lado / 4))
                .background(if (activa) Ambar.copy(alpha = 0.22f) else Azul.copy(alpha = 0.18f))
                .alpha(tenue),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                inicialExtension(nombre),
                style = AntenaType.meta.copy(fontSize = (lado.value * 0.55f).sp),
                color = if (activa) Ambar else FosforoTenue,
            )
        }
    }
}

/** Atajo con el bitmap que entrega el motor (`GeckoMotor.iconos`). */
@Composable
fun ExtIcono(ext: ExtInfo, imagen: Bitmap?, lado: Dp = 18.dp) =
    ExtIcono(nombre = ext.nombre, imagen = imagen?.asImageBitmap(), activa = ext.activa, lado = lado)

/* ── Fila de menú desplegable ── */
@Composable
fun MenuRow(
    label: String,
    value: String? = null,
    /** Paso 5 · icono opcional a la izquierda (menú contextual). */
    icon: ToolIcon? = null,
    /** Salir: tono rojo. */
    peligro: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val fuente = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hover by fuente.collectIsHoveredAsState()
    val center = rememberCenterHandler(onClick, onLongClick)
    val shape = RoundedCornerShape(12.dp)
    val acento = if (peligro) Alerta else Ambar
    Row(
        Modifier
            .fillMaxWidth()
            .hoverable(fuente)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(shape)
            .background(
                when {
                    focused -> acento.copy(alpha = 0.16f)
                    hover -> acento.copy(alpha = 0.09f)
                    else -> Color.Transparent
                },
            )
            .border(if (focused) 1.5.dp else 0.dp, if (focused) acento else Color.Transparent, shape)
            .onKeyEvent(center)
            .pointerInput(onClick) { detectTapGestures { onClick() } }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            ToolIconGlyph(
                icon,
                if (focused) acento else if (peligro) Alerta else FosforoTenue,
                size = 24.dp,
            )
            Spacer(Modifier.width(14.dp))
        }
        Text(
            label,
            style = AntenaType.body,
            color = if (focused) acento else if (peligro) Alerta else Fosforo,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(value, style = AntenaType.monoSmall, color = FosforoTenue, maxLines = 1)
        }
    }
}

/* ── Panel desplegable del menú (arriba a la derecha) ── */
@Composable
fun MenuDropdown(
    engineName: String,
    onFavorites: () -> Unit,
    onHistory: () -> Unit,
    onDownloads: () -> Unit,
    onComplementos: () -> Unit,
    /** Paso 2 · una sola entrada: los ajustes (motor, inicio, barra) viven en su pestaña. */
    onConfig: () -> Unit,
    onSystem: () -> Unit,
    onDiagnostics: () -> Unit,
    onBitacora: () -> Unit,
    onAcerca: () -> Unit,
    onClaves: () -> Unit = {},
    onArchivos: () -> Unit = {},
    /** Cierra la aplicación. */
    onSalir: () -> Unit,
    version: String,
    /** Versión nueva disponible (o ya descargada): se avisa arriba del menú. */
    actualizacion: String? = null,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    // Nunca más alto que la pantalla (menos las barras): si no cabe, se desplaza.
    val altoMax = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp - 120.dp
    Column(
        Modifier
            .width(360.dp)
            .heightIn(max = altoMax)
            .cristal(RoundedCornerShape(EsquinaTarjeta))
            .listaDesplazable()
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        // Cabecera: la mascota, el nombre y la versión.
        Row(
            Modifier.padding(start = 6.dp, end = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = androidx.compose.ui.res.painterResource(com.antena.tv.R.drawable.ic_launcher_fg),
                contentDescription = null,
                modifier = Modifier.size(52.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Coco Surf", style = AntenaType.panel, color = Fosforo)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Escudo))
                    Spacer(Modifier.width(7.dp))
                    Text("v$version", style = AntenaType.monoSmall, color = FosforoTenue)
                }
            }
        }
        MenuDivisor()
        if (actualizacion != null) {
            MenuRow("Actualización $actualizacion", icon = ToolIcon.Download, value = "Instalar", onClick = onAcerca)
        }
        Box(Modifier.focusRequester(first)) {
            MenuRow("Favoritos", icon = ToolIcon.Star, onClick = onFavorites)
        }
        MenuRow("Historial", icon = ToolIcon.Clock, onClick = onHistory)
        MenuRow("Descargas", icon = ToolIcon.Download, onClick = onDownloads)
        MenuRow("Archivos", icon = ToolIcon.Folder, onClick = onArchivos)
        MenuDivisor()
        MenuRow("Complementos", icon = ToolIcon.Puzzle, onClick = onComplementos)
        MenuRow("Contraseñas", icon = ToolIcon.Lock, onClick = onClaves)
        MenuRow("Configuración", icon = ToolIcon.Gear, value = engineName, onClick = onConfig)
        MenuDivisor()
        MenuRow("Información del sistema", icon = ToolIcon.Chip, onClick = onSystem)
        MenuRow("Acerca de Coco Surf", icon = ToolIcon.Info, onClick = onAcerca)
        MenuDivisor()
        MenuRow("Salir de Coco Surf", icon = ToolIcon.Power, peligro = true, onClick = onSalir)
    }
}

/** Filete fino entre los grupos del menú. */
@Composable
private fun MenuDivisor() {
    Box(
        Modifier
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Azul.copy(alpha = 0.28f)),
    )
}

/* ── Fila de selección (motor / inicio) ── */
@Composable
fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fuente = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hover by fuente.collectIsHoveredAsState()
    val center = rememberCenterHandler(onClick)
    val shape = RoundedCornerShape(EsquinaBoton)
    Row(
        Modifier
            .fillMaxWidth()
            .hoverable(fuente)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(shape)
            .background(
                when {
                    focused -> Ambar.copy(alpha = 0.12f)
                    hover -> Fosforo.copy(alpha = 0.06f)
                    else -> Color.Transparent
                },
            )
            .border(if (focused) 1.5.dp else 0.dp, if (focused) Ambar else Color.Transparent, shape)
            .onKeyEvent(center)
            .pointerInput(onClick) { detectTapGestures { onClick() } }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(1.5.dp, if (selected) Ambar else Fosforo.copy(alpha = 0.35f), CircleShape)
                .padding(4.dp)
                .clip(CircleShape)
                .background(if (selected) Ambar else Color.Transparent),
        )
        Spacer(Modifier.width(14.dp))
        Text(label, style = AntenaType.body, color = if (selected || focused) Fosforo else Fosforo.copy(alpha = 0.8f))
    }
}

/** Una fila de ajuste: título (y explicación opcional) a la izquierda, control a la derecha. */
@Composable
fun FilaAjuste(
    titulo: String,
    descripcion: String? = null,
    onClick: () -> Unit,
    icono: (@Composable () -> Unit)? = null,
    control: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val fuente = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hover by fuente.collectIsHoveredAsState()
    val center = rememberCenterHandler(onClick)
    val shape = RoundedCornerShape(EsquinaBoton)
    Row(
        Modifier
            .fillMaxWidth()
            .hoverable(fuente)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(shape)
            .background(
                when {
                    focused -> Ambar.copy(alpha = 0.10f)
                    hover -> Fosforo.copy(alpha = 0.05f)
                    else -> Color.Transparent
                },
            )
            .border(if (focused) 1.5.dp else 0.dp, if (focused) Ambar else Color.Transparent, shape)
            .onKeyEvent(center)
            .pointerInput(onClick) { detectTapGestures { onClick() } }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icono != null) {
            icono()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(titulo, style = AntenaType.body, color = Fosforo)
            if (descripcion != null) {
                Text(descripcion, style = AntenaType.meta, color = FosforoTenue)
            }
        }
        Spacer(Modifier.width(14.dp))
        control()
    }
}

/** Sección de ajustes: título en versalitas y una tarjeta de cristal con sus filas. */
@Composable
fun SeccionAjustes(titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
        Text(
            titulo.uppercase(),
            style = AntenaType.meta.copy(fontSize = 12.sp, letterSpacing = 1.2.sp),
            color = FosforoTenue,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .cristal(RoundedCornerShape(EsquinaTarjeta), elevacion = 10.dp, opacidad = 0.7f)
                .padding(8.dp),
            content = contenido,
        )
    }
}

/* ── Modal de favorito (estrella): nombre, URL, carpeta ── */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StarModal(
    initialName: String,
    initialUrl: String,
    folders: List<Folder>,
    existing: Favorite?,
    foldersVersion: Int,
    onSave: (name: String, url: String, folderId: String?) -> Unit,
    onDelete: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onClose: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var url by remember { mutableStateOf(initialUrl) }
    var folderId by remember { mutableStateOf(existing?.folderId) }
    var newFolderMode by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    val nameFr = remember { FocusRequester() }
    val urlFr = remember { FocusRequester() }
    val saveFr = remember { FocusRequester() }

    LaunchedEffect(Unit) { nameFr.requestFocus() }
    // Si se creó una carpeta mientras el modal estaba abierto, refrescar la lista.
    LaunchedEffect(foldersVersion) { if (newFolderMode) newFolderMode = false }

    Box(
        Modifier
            // Sin oscurecer la página: solo un velo táctil que cierra al tocar fuera.
            .fillMaxSize()
            .pointerInput(onClose) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.TopEnd,
    ) {
        Column(
            Modifier
                // Anclado junto a la estrella (extremo derecho bajo la barra).
                .padding(top = 66.dp, end = 16.dp)
                .width(310.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Casco)
                .border(1.dp, Ambar.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(18.dp)
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Text(
                if (existing != null) "Editar favorito" else "Nuevo favorito",
                style = AntenaType.panel,
                color = Fosforo,
            )
            Spacer(Modifier.height(14.dp))

            Text("Nombre", style = AntenaType.meta, color = FosforoTenue)
            Spacer(Modifier.height(6.dp))
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                textStyle = AntenaType.body.copy(color = Fosforo),
                cursorBrush = SolidColor(Ambar),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(nameFr)
                    .onFocusChanged { if (it.isFocused) name = name }
                    .onKeyEvent { ev ->
                        if (ev.key == Key.DirectionDown && ev.type == KeyEventType.KeyUp) {
                            urlFr.requestFocus(); true
                        } else false
                    }
                    .clip(RoundedCornerShape(8.dp))
                    .background(Noche)
                    .border(
                        1.dp,
                        Azul.copy(alpha = 0.5f),
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )

            Spacer(Modifier.height(12.dp))
            Text("Dirección", style = AntenaType.meta, color = FosforoTenue)
            Spacer(Modifier.height(6.dp))
            BasicTextField(
                value = url,
                onValueChange = { url = it },
                textStyle = AntenaType.mono.copy(color = Fosforo),
                cursorBrush = SolidColor(Ambar),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(urlFr)
                    .onKeyEvent { ev ->
                        if (ev.key == Key.DirectionDown && ev.type == KeyEventType.KeyUp) {
                            saveFr.requestFocus(); true
                        } else if (ev.key == Key.DirectionUp && ev.type == KeyEventType.KeyUp) {
                            nameFr.requestFocus(); true
                        } else false
                    }
                    .clip(RoundedCornerShape(8.dp))
                    .background(Noche)
                    .border(1.dp, Azul.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )

            Spacer(Modifier.height(12.dp))
            Text("Carpeta", style = AntenaType.meta, color = FosforoTenue)
            Spacer(Modifier.height(8.dp))
            if (newFolderMode) {
                BasicTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    textStyle = AntenaType.body.copy(color = Fosforo),
                    cursorBrush = SolidColor(Ambar),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onKeyEvent { ev ->
                            if (ev.key == Key.Enter || ev.key == Key.DirectionCenter) {
                                if (ev.type == KeyEventType.KeyUp && newFolderName.isNotBlank()) {
                                    onCreateFolder(newFolderName.trim())
                                }
                                true
                            } else false
                        }
                        .clip(RoundedCornerShape(8.dp))
                        .background(Noche)
                        .border(2.dp, Ambar, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RemoteChip(
                        label = "Sin carpeta",
                        small = true,
                        accent = folderId == null,
                        onClick = { folderId = null },
                    )
                    folders.forEach { f ->
                        RemoteChip(
                            label = f.name,
                            small = true,
                            accent = folderId == f.id,
                            onClick = { folderId = f.id },
                        )
                    }
                    RemoteChip(
                        label = "+ Nueva",
                        small = true,
                        onClick = { newFolderMode = true; newFolderName = "" },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            FlowRow(
                Modifier.focusRequester(saveFr),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RemoteChip(
                    label = "Guardar",
                    accent = true,
                    onClick = {
                        if (name.isNotBlank() && url.isNotBlank()) {
                            onSave(name.trim(), url.trim(), folderId)
                        }
                    },
                )
                if (existing != null) {
                    RemoteChip(label = "Eliminar", onClick = onDelete)
                }
                RemoteChip(label = "Cancelar", onClick = onClose)
            }
        }
    }
}

/* ── Ajustes: qué botones viven en la barra de herramientas ── */
@Composable
fun ToolbarSettingsPage(
    showFavorites: Boolean,
    showHistory: Boolean,
    showDownloads: Boolean,
    showFloating: Boolean,
    onToggle: (key: String, value: Boolean) -> Unit,
    /** Paso 2 · dentro de la pestaña Configuración la sangría la pone el padre. */
    paddingStart: Dp = 48.dp,
) {
    Column(
        Modifier.fillMaxSize().padding(start = paddingStart),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Elige qué botones aparecen en la barra de direcciones. " +
                "Atrás, Recargar e Inicio siempre están.",
            style = AntenaType.meta,
            color = FosforoTenue,
        )
        Spacer(Modifier.height(8.dp))
        OptionRow(
            label = "Favoritos en la barra",
            selected = showFavorites,
            onClick = { onToggle("fav", !showFavorites) },
        )
        OptionRow(
            label = "Historial en la barra",
            selected = showHistory,
            onClick = { onToggle("hist", !showHistory) },
        )
        OptionRow(
            label = "Descargas en la barra",
            selected = showDownloads,
            onClick = { onToggle("down", !showDownloads) },
        )
        OptionRow(
            label = "Botón ir arriba (aparece al deslizar)",
            selected = showFloating,
            onClick = { onToggle("float", !showFloating) },
        )
    }
}

/**
 * Texto de «Acerca de»: aquí se define quién hizo la aplicación y cómo contactarle.
 * Una línea por renglón; edítalo y recompila.
 */
private val ACERCA_CREDITOS = listOf(
    "Hecho por JEM Desarrollos.",
    "Hecho con cariño para navegar desde el sofá.",
    "Motor: Firefox (GeckoView) · Complementos: tienda oficial de Mozilla.",
)

/**
 * Enlace de apoyo (Ko-fi de JEM Desarrollos; cubre PayPal). Antes: buymeacoffee, copia
 * su dirección aquí (p. ej. "https://buymeacoffee.com/tuusuario") y recompila: el
 * botón aparece solo cuando hay una dirección.
 */
private const val ACERCA_CAFE_URL = "https://ko-fi.com/jemdesarrollos"

/** «Acerca de» como pestaña interna: identidad de la app, versión y datos del equipo. */
@Composable
fun AcercaPage(version: String, onClose: () -> Unit, onAbrir: (String) -> Unit = {}) {
    val primero = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { primero.requestFocus() } }
    val motor = runCatching { org.mozilla.geckoview.BuildConfig.MOZ_APP_VERSION }.getOrDefault("—")
    val filas = listOf(
        "Versión" to version,
        "Motor" to "Firefox $motor (GeckoView)",
        "Android" to "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
        "Dispositivo" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
        "Arquitectura" to (android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "—"),
        "Memoria" to com.antena.tv.gecko.Memoria.leer(androidx.compose.ui.platform.LocalContext.current).let {
            "${it.libreMb} MB libres de ${it.totalMb} MB" + if (it.pocaRam) " · modo ahorro" else ""
        },
    )
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Acerca de", style = AntenaType.section, color = Fosforo, modifier = Modifier.weight(1f))
            Box(Modifier.focusRequester(primero)) {
                RemoteChip(label = "✕ Cerrar", small = true, onClick = onClose)
            }
        }
        Spacer(Modifier.height(18.dp))
        Image(
            painter = androidx.compose.ui.res.painterResource(com.antena.tv.R.drawable.ic_launcher_fg),
            contentDescription = null,
            modifier = Modifier.size(150.dp),
        )
        Text("Coco Surf", style = AntenaType.display, color = Fosforo)
        Text("Navegador pensado para el control remoto", style = AntenaType.body, color = FosforoTenue)
        Spacer(Modifier.height(22.dp))
        Column(
            Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Casco)
                .border(1.dp, Azul.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .padding(horizontal = 22.dp, vertical = 14.dp),
        ) {
            filas.forEachIndexed { i, (k, v) ->
                if (i > 0) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Azul.copy(alpha = 0.2f)))
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text(k, style = AntenaType.body, color = FosforoTenue, modifier = Modifier.width(170.dp))
                    Text(v, style = AntenaType.body, color = Fosforo, modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        SeccionActualizacion()
        Spacer(Modifier.height(22.dp))
        ACERCA_CREDITOS.forEachIndexed { i, linea ->
            Text(
                linea,
                style = if (i == 0) AntenaType.panel else AntenaType.body,
                color = if (i == 0) Fosforo else FosforoTenue,
            )
            Spacer(Modifier.height(6.dp))
        }
        if (ACERCA_CAFE_URL.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            RemoteChip(
                label = "Invítame un café (Ko-fi)",
                accent = true,
                onClick = {
                    // Se abre en el propio navegador: un TV no suele tener otro instalado.
                    onAbrir(ACERCA_CAFE_URL)
                },
            )
        }
    }
}

/** Buscar, descargar e instalar la última versión publicada en GitHub. */
@Composable
private fun SeccionActualizacion() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val alcance = androidx.compose.runtime.rememberCoroutineScope()
    val e = Actualizador.estado
    Column(Modifier.widthIn(max = 620.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val texto = when (e) {
            is Actualizador.Estado.Inactivo -> "Versión instalada: ${Actualizador.versionActual(ctx)}"
            is Actualizador.Estado.Buscando -> "Buscando…"
            is Actualizador.Estado.AlDia -> "Estás al día (${e.version})."
            is Actualizador.Estado.Nueva -> "Hay una versión nueva: ${e.version} (${e.mb} MB)."
            is Actualizador.Estado.SinPublicar -> "No hay actualizaciones publicadas: ${e.detalle}."
            is Actualizador.Estado.Error -> "No se pudo comprobar: ${e.motivo}"
            is Actualizador.Estado.Descargando -> "Descargando ${e.version}… ${e.pct} %"
            is Actualizador.Estado.Listo -> "Descargada la ${e.version}. Pulsa Instalar."
        }
        Text(texto, style = AntenaType.body, color = if (e is Actualizador.Estado.Nueva || e is Actualizador.Estado.Listo) Ambar else FosforoTenue)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when (e) {
                is Actualizador.Estado.Nueva -> {
                    RemoteChip(label = "Descargar e instalar", accent = true, onClick = { alcance.launch { Actualizador.descargar(ctx, e) } })
                    RemoteChip(label = "Buscar de nuevo", onClick = { alcance.launch { Actualizador.buscar(ctx) } })
                }
                is Actualizador.Estado.Listo -> RemoteChip(label = "Instalar", accent = true, onClick = { Actualizador.instalar(ctx, e) })
                is Actualizador.Estado.Descargando, is Actualizador.Estado.Buscando -> Unit
                else -> RemoteChip(label = "Buscar actualización", onClick = { alcance.launch { Actualizador.buscar(ctx) } })
            }
        }
        if (e is Actualizador.Estado.Nueva && e.notas.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(e.notas.take(600), style = AntenaType.meta, color = FosforoTenue)
        }
    }
}

/** Confirmación al intentar salir con «Atrás» desde la página de inicio. */
@Composable
fun SalirDialogo(onSalir: () -> Unit, onCancelar: () -> Unit) {
    val cancelar = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { cancelar.requestFocus() } }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .pointerInput(Unit) { detectTapGestures { onCancelar() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(440.dp)
                .cristal(RoundedCornerShape(EsquinaVentana), elevacion = 32.dp)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = androidx.compose.ui.res.painterResource(com.antena.tv.R.drawable.ic_launcher_fg),
                contentDescription = null,
                modifier = Modifier.size(84.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text("¿Salir de Coco Surf?", style = AntenaType.panel, color = Fosforo)
            Spacer(Modifier.height(4.dp))
            Text("Se cerrarán todas las pestañas abiertas.", style = AntenaType.body, color = FosforoTenue)
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.focusRequester(cancelar)) {
                    RemoteChip(label = "Cancelar", small = true, onClick = onCancelar)
                }
                RemoteChip(label = "Salir", small = true, peligro = true, onClick = onSalir)
            }
        }
    }
}

/*
 * Paso 2 · «Configuración» como pestaña: agrupa los ajustes que antes eran tres
 * overlays sueltos del menú (motor de búsqueda, página de inicio y barra de
 * herramientas). Las opciones y su efecto inmediato son exactamente los mismos;
 * dentro de una pestaña la vista no se cierra al navegar.
 */
@Composable
fun ConfiguracionPage(
    engines: List<SearchEngine>,
    engineId: String,
    onEngine: (String) -> Unit,
    homepage: String,
    paginaActual: String,
    onHomepage: (String) -> Unit,
    showFavorites: Boolean,
    showHistory: Boolean,
    showDownloads: Boolean,
    showFloating: Boolean,
    onToggle: (String, Boolean) -> Unit,
    onClose: () -> Unit,
    showComplementos: Boolean = true,
    acentoId: String = ACENTOS.first().id,
    onAcento: (String) -> Unit = {},
    velocidadId: String = "normal",
    onVelocidad: (String) -> Unit = {},
    modoPaginaId: String = "auto",
    onModoPagina: (String) -> Unit = {},
    onMonitorCambio: () -> Unit = {},
    onPrecisionCambio: () -> Unit = {},
    restaurarPestanas: Boolean = true,
    onRestaurarPestanas: (Boolean) -> Unit = {},
    precalentar: Boolean = true,
    onPrecalentar: (Boolean) -> Unit = {},
) {
    var pestana by remember { mutableStateOf(0) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Columna central de ancho cómodo: en una pantalla grande el texto no se estira.
        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Configuración", style = AntenaType.section, color = Fosforo, modifier = Modifier.weight(1f))
                RemoteChip(label = "", icon = ToolIcon.Close, small = true, onClick = onClose)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("General", "Control remoto", "Monitor").forEachIndexed { i, t ->
                    RemoteChip(label = t, small = true, accent = i == pestana, onClick = { pestana = i })
                }
            }
            Spacer(Modifier.height(20.dp))

            when (pestana) {
                0 -> {
                    SeccionAjustes("Apariencia") {
                        Text(
                            "Color de acento",
                            style = AntenaType.body,
                            color = Fosforo,
                            modifier = Modifier.padding(start = 14.dp, top = 10.dp),
                        )
                        Text(
                            "Se guarda en tu perfil y se aplica a toda la aplicación.",
                            style = AntenaType.meta,
                            color = FosforoTenue,
                            modifier = Modifier.padding(start = 14.dp, top = 2.dp, bottom = 12.dp),
                        )
                        Row(
                            Modifier.padding(start = 10.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ACENTOS.forEach { a ->
                                MuestraColor(a.color, elegido = a.id == acentoId, alElegir = { onAcento(a.id) })
                            }
                        }
                    }

                    SeccionAjustes("Motor de búsqueda") {
                        engines.forEach { e ->
                            OptionRow(label = e.name, selected = e.id == engineId, onClick = { onEngine(e.id) })
                        }
                    }

                    SeccionAjustes("Página de inicio") {
                        OptionRow(
                            label = "Inicio de Coco Surf (favoritos)",
                            selected = homepage == "cocosurf://inicio",
                            onClick = { onHomepage("cocosurf://inicio") },
                        )
                        if (paginaActual.startsWith("http")) {
                            OptionRow(
                                label = "Usar esta página: ${hostOf(paginaActual) ?: paginaActual}",
                                selected = homepage == paginaActual,
                                onClick = { onHomepage(paginaActual) },
                            )
                        }
                    }

                    SeccionAjustes("Al abrir y cerrar") {
                        FilaAjuste(
                            titulo = "Recordar las pestañas",
                            descripcion = "Al cerrar la app se guardan las páginas abiertas y se traen de vuelta al abrirla.",
                            onClick = { onRestaurarPestanas(!restaurarPestanas) },
                        ) { Interruptor(restaurarPestanas, onRestaurarPestanas) }
                        Separador(Modifier.padding(horizontal = 14.dp))
                        FilaAjuste(
                            titulo = "Precalentar el motor",
                            descripcion = "Mientras estás en Inicio el motor arranca por dentro para que la primera página cargue rápido. Usa unos 100 MB hasta que abres una página.",
                            onClick = { onPrecalentar(!precalentar) },
                        ) { Interruptor(precalentar, onPrecalentar) }
                    }

                    SeccionAjustes("Fondo de la página de inicio") { AjusteFondoInicio() }

                    SeccionAjustes("Modo de página") {
                        Text(
                            "El diseño de escritorio muestra completos los vídeos verticales de " +
                                "Facebook, Instagram y TikTok; el móvil los corta por los lados.",
                            style = AntenaType.meta,
                            color = FosforoTenue,
                            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp),
                        )
                        OptionRow("Automático (escritorio sólo en esos sitios)", modoPaginaId == "auto") { onModoPagina("auto") }
                        OptionRow("Siempre escritorio", modoPaginaId == "escritorio") { onModoPagina("escritorio") }
                        OptionRow("Siempre móvil", modoPaginaId == "movil") { onModoPagina("movil") }
                    }

                    SeccionAjustes("Barra de herramientas") {
                        val filas = listOf(
                            Triple("Complementos", "El botón con la lista de tus extensiones.", "ext") to showComplementos,
                            Triple("Favoritos", null, "fav") to showFavorites,
                            Triple("Historial", null, "hist") to showHistory,
                            Triple("Descargas", null, "down") to showDownloads,
                            Triple("Botón «ir arriba»", "Aparece al deslizar una página larga.", "float") to showFloating,
                        )
                        filas.forEachIndexed { i, (datos, encendido) ->
                            if (i > 0) Separador(Modifier.padding(horizontal = 14.dp))
                            FilaAjuste(
                                titulo = datos.first,
                                descripcion = datos.second,
                                onClick = { onToggle(datos.third, !encendido) },
                            ) { Interruptor(encendido, { onToggle(datos.third, it) }) }
                        }
                    }
                }

                1 -> {
                    SeccionAjustes("Velocidad del cursor") {
                        Text(
                            "Cuánto avanza el cursor con las flechas del mando (y el desplazamiento " +
                                "al llegar a los bordes). Mantén la flecha y acelera sola.",
                            style = AntenaType.meta,
                            color = FosforoTenue,
                            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp),
                        )
                        OptionRow("Lenta", velocidadId == "lenta") { onVelocidad("lenta") }
                        OptionRow("Normal", velocidadId == "normal") { onVelocidad("normal") }
                        OptionRow("Rápida", velocidadId == "rapida") { onVelocidad("rapida") }
                        OptionRow("Muy rápida", velocidadId == "turbo") { onVelocidad("turbo") }
                    }
                    SeccionAjustes("Cursor largo y corto") {
                        Text(
                            "Asigna una tecla de tu mando para alternar entre el cursor largo (rápido) y el corto " +
                                "(lento y fino, con un aro): útil para llegar a iconos pequeños como la × de las pestañas.",
                            style = AntenaType.meta,
                            color = FosforoTenue,
                            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp),
                        )
                        FilaAjuste(
                            titulo = "Tecla del cursor corto",
                            descripcion = "Pulsa «Asignar» y luego la tecla del mando que quieras usar. No sirven las flechas, OK, Atrás ni Menú.",
                            onClick = { com.antena.tv.cursor.Puntero.capturandoPrecision = true },
                        ) {
                            RemoteChip(
                                label = when {
                                    com.antena.tv.cursor.Puntero.capturandoPrecision -> "Pulsa una tecla…"
                                    com.antena.tv.cursor.Puntero.teclaPrecision == 0 -> "Asignar"
                                    else -> MonitorConfig.nombreTecla(com.antena.tv.cursor.Puntero.teclaPrecision)
                                },
                                small = true,
                                accent = com.antena.tv.cursor.Puntero.capturandoPrecision,
                                onClick = { com.antena.tv.cursor.Puntero.capturandoPrecision = true },
                            )
                        }
                        if (com.antena.tv.cursor.Puntero.teclaPrecision != 0) {
                            Separador(Modifier.padding(horizontal = 14.dp))
                            FilaAjuste(
                                titulo = "Quitar la tecla",
                                onClick = { com.antena.tv.cursor.Puntero.teclaPrecision = 0; onPrecisionCambio() },
                            ) { RemoteChip(label = "Quitar", small = true, onClick = { com.antena.tv.cursor.Puntero.teclaPrecision = 0; onPrecisionCambio() }) }
                        }
                    }
                    SeccionAjustes("Atajos del mando") {
                        listOf(
                            "Flechas" to "Mueven el cursor; sostenidas aceleran.",
                            "OK" to "Clic. Mantenido: menú de la página.",
                            "Menú (corta)" to "Modo desplazar: las flechas mueven la lista bajo el cursor.",
                            "Menú (larga)" to "Enciende o apaga el cursor.",
                            "Atrás" to "Muestra las barras; de nuevo, atrás en la página.",
                        ).forEachIndexed { i, (tecla, texto) ->
                            if (i > 0) Separador(Modifier.padding(horizontal = 14.dp))
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(tecla, style = AntenaType.body, color = Ambar, modifier = Modifier.width(150.dp))
                                Text(texto, style = AntenaType.body, color = FosforoTenue, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                else -> {
                    SeccionAjustes("Monitor de recursos") {
                        FilaAjuste(
                            titulo = "Mostrar el monitor",
                            descripcion = "Memoria, CPU y más, semitransparente sobre la página.",
                            onClick = { MonitorConfig.visible = !MonitorConfig.visible; onMonitorCambio() },
                        ) { Interruptor(MonitorConfig.visible, { MonitorConfig.visible = it; onMonitorCambio() }) }
                        Separador(Modifier.padding(horizontal = 14.dp))
                        FilaAjuste(
                            titulo = "Tecla rápida",
                            descripcion = "La que abre y cierra el monitor desde el mando.",
                            onClick = { MonitorConfig.capturando = true },
                        ) {
                            RemoteChip(
                                label = if (MonitorConfig.capturando) "Pulsa una tecla…"
                                else MonitorConfig.nombreTecla(MonitorConfig.tecla),
                                small = true,
                                accent = MonitorConfig.capturando,
                                onClick = { MonitorConfig.capturando = true },
                            )
                        }
                        Separador(Modifier.padding(horizontal = 14.dp))
                        FilaAjuste(
                            titulo = "Gráficos al lado",
                            descripcion = "Una línea con la evolución junto a cada cifra.",
                            onClick = { MonitorConfig.graficas = !MonitorConfig.graficas; onMonitorCambio() },
                        ) { Interruptor(MonitorConfig.graficas, { MonitorConfig.graficas = it; onMonitorCambio() }) }
                    }

                    SeccionAjustes("Posición") {
                        MonitorConfig.POSICIONES.forEach { (id, nombre) ->
                            OptionRow(nombre, MonitorConfig.posicion == id) {
                                MonitorConfig.posicion = id
                                onMonitorCambio()
                            }
                        }
                    }

                    SeccionAjustes("Qué mostrar") {
                        METRICAS_MONITOR.forEachIndexed { i, m ->
                            if (i > 0) Separador(Modifier.padding(horizontal = 14.dp))
                            val activa = m.id in MonitorConfig.metricas
                            val cambiar = { encender: Boolean ->
                                MonitorConfig.metricas =
                                    if (encender) MonitorConfig.metricas + m.id else MonitorConfig.metricas - m.id
                                onMonitorCambio()
                            }
                            FilaAjuste(titulo = m.nombre, onClick = { cambiar(!activa) }) {
                                Interruptor(activa, cambiar)
                            }
                        }
                    }
                    Text(
                        "La memoria se mide por proceso: Android no deja medir cada pestaña ni cada " +
                            "complemento por separado (las páginas comparten un proceso, y los " +
                            "complementos otro).",
                        style = AntenaType.meta,
                        color = FosforoTenue,
                        modifier = Modifier.padding(start = 6.dp, bottom = 22.dp),
                    )
                }
            }
        }
    }
}

/* ── Página de inicio (dashboard): buscador, fondo opcional y rejilla de favoritos ── */
@Composable
fun DashboardPage(
    favorites: List<Favorite>,
    onOpen: (String) -> Unit,
    onDelete: (Favorite) -> Unit,
    onAdd: () -> Unit,
    engineName: String = "",
    onSearch: (String) -> Unit = {},
) {
    // Fondo elegido por el usuario (nada por defecto: ahorra memoria).
    var hero by remember { mutableStateOf<Bitmap?>(null) }
    val contexto = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(FondoInicio.modo, FondoInicio.version) {
        FondoInicio.cargar(contexto)
        hero = when (FondoInicio.modo) {
            FondoInicio.IMAGEN -> FondoInicio.leerImagen(contexto)
            // La foto aleatoria baja varios MB: en un aparato justo de memoria no se carga.
            FondoInicio.ALEATORIO ->
                if (com.antena.tv.gecko.Memoria.leer(contexto).pocaRam) null else FondoInicio.leerAleatoria()
            else -> null
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Degradado base siempre presente.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Casco, Noche),
                    ),
                ),
        )
        hero?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Scrim para legibilidad 10-foot.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(
                                Noche.copy(alpha = 0.55f),
                                Noche.copy(alpha = 0.78f),
                                Noche.copy(alpha = 0.95f),
                            ),
                        ),
                    ),
            )
        }

        // Todo el conjunto, centrado en horizontal y en vertical.
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 48.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = androidx.compose.ui.res.painterResource(com.antena.tv.R.drawable.ic_launcher_fg),
                contentDescription = null,
                modifier = Modifier.size(86.dp),
            )
            Text("Coco Surf", style = AntenaType.panel, color = Fosforo)
            Spacer(Modifier.height(18.dp))
            BuscadorInicio(engineName = engineName, onSearch = onSearch)
            Spacer(Modifier.height(26.dp))

            // Rejilla de 5 columnas; «Añadir» al final, con las demás fichas.
            val celdas = favorites.map<Favorite, Favorite?> { it } + listOf<Favorite?>(null)
            celdas.chunked(5).forEach { fila ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(bottom = 14.dp),
                ) {
                    fila.forEach { fav ->
                        if (fav == null) {
                            AddFavTile(onClick = onAdd)
                        } else {
                            FavoriteTile(
                                fav,
                                onClick = { onOpen(fav.url) },
                                onDelete = { onDelete(fav) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Ajustes del fondo del inicio: sin imagen, foto aleatoria o la imagen del usuario (archivo o dirección). */
@Composable
private fun AjusteFondoInicio() {
    val c = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(Unit) { FondoInicio.cargar(c) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var pidiendoUrl by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    val selector = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) scope.launch {
            aviso = if (FondoInicio.usarArchivo(c, uri)) "Imagen guardada como fondo" else "No se pudo usar ese archivo"
        }
    }
    OptionRow("Sin imagen (más ligero)", FondoInicio.modo == FondoInicio.NINGUNO) { FondoInicio.elegirModo(c, FondoInicio.NINGUNO) }
    OptionRow("Foto aleatoria de internet", FondoInicio.modo == FondoInicio.ALEATORIO) { FondoInicio.elegirModo(c, FondoInicio.ALEATORIO) }
    OptionRow(
        if (FondoInicio.hayImagen(c)) "Mi imagen" else "Mi imagen (elige una abajo)",
        FondoInicio.modo == FondoInicio.IMAGEN,
    ) { FondoInicio.elegirModo(c, FondoInicio.IMAGEN) }
    Row(
        Modifier.padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RemoteChip(label = "Elegir archivo…", small = true, onClick = {
            runCatching { selector.launch(arrayOf("image/*")) }
                .onFailure { aviso = "Este aparato no tiene selector de archivos; usa una dirección web" }
        })
        RemoteChip(label = "Desde una dirección…", small = true, onClick = { pidiendoUrl = !pidiendoUrl })
        if (FondoInicio.hayImagen(c)) {
            RemoteChip(label = "Quitar imagen", small = true, onClick = { FondoInicio.quitar(c); aviso = "Imagen quitada" })
        }
    }
    if (pidiendoUrl) {
        BasicTextField(
            value = url,
            onValueChange = { url = it },
            textStyle = AntenaType.mono.copy(color = Fosforo),
            cursorBrush = SolidColor(Ambar),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                if (url.isNotBlank()) scope.launch {
                    aviso = "Descargando…"
                    aviso = if (FondoInicio.usarUrl(c, url)) "Imagen guardada como fondo" else "No se pudo descargar esa imagen"
                }
            }),
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Noche)
                .border(1.dp, Azul.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
        Text(
            "Pega la dirección de una imagen (.jpg, .png…) y pulsa Enter.",
            style = AntenaType.meta,
            color = FosforoTenue,
            modifier = Modifier.padding(start = 14.dp, bottom = 6.dp),
        )
    }
    aviso?.let {
        Text(it, style = AntenaType.meta, color = Ambar, modifier = Modifier.padding(start = 14.dp, bottom = 8.dp))
    }
    Text(
        "También puedes mantener OK sobre una imagen de cualquier página y elegir «Usar como fondo de inicio».",
        style = AntenaType.meta,
        color = FosforoTenue,
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
    )
}

/** Cuadro de búsqueda de la página de inicio: escribe y pulsa Enter para buscar con el motor elegido. */
@Composable
private fun BuscadorInicio(engineName: String, onSearch: (String) -> Unit) {
    var texto by remember { mutableStateOf("") }
    var enfocado by remember { mutableStateOf(false) }
    val fuente = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hover by fuente.collectIsHoveredAsState()
    val forma = RoundedCornerShape(26.dp)
    val buscar = { if (texto.isNotBlank()) onSearch(texto.trim()) }
    Row(
        Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .hoverable(fuente)
            .cristal(forma, elevacion = 12.dp, opacidad = 0.8f)
            .border(
                if (enfocado) 1.5.dp else 1.dp,
                if (enfocado) Ambar else if (hover) Fosforo.copy(alpha = 0.35f) else Color.Transparent,
                forma,
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (texto.isEmpty()) {
                Text(
                    if (engineName.isBlank()) "Buscar o escribir una dirección" else "Buscar con $engineName o escribir una dirección",
                    style = AntenaType.body,
                    color = FosforoTenue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = texto,
                onValueChange = { texto = it },
                textStyle = AntenaType.body.copy(color = Fosforo),
                cursorBrush = SolidColor(Ambar),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { buscar() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { enfocado = it.isFocused }
                    .onKeyEvent { ev ->
                        if (ev.key == Key.Enter && ev.type == KeyEventType.KeyUp) { buscar(); true } else false
                    },
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(28.dp).pointerInput(texto) { detectTapGestures { buscar() } }, contentAlignment = Alignment.Center) {
            ToolIconGlyph(ToolIcon.Search, if (enfocado || hover) Ambar else FosforoTenue, size = 22.dp)
        }
    }
}

@Composable
private fun FavoriteTile(fav: Favorite, onClick: () -> Unit, onDelete: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fuente = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hover by fuente.collectIsHoveredAsState()
    val center = rememberCenterHandler(onClick, onDelete)
    val shape = RoundedCornerShape(EsquinaTarjeta)
    Column(
        Modifier
            .width(138.dp)
            .hoverable(fuente)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(shape)
            .background(
                when {
                    focused -> Ambar.copy(alpha = 0.14f)
                    hover -> Fosforo.copy(alpha = 0.10f)
                    else -> Fosforo.copy(alpha = 0.05f)
                },
            )
            .border(if (focused) 1.5.dp else 0.dp, if (focused) Ambar else Color.Transparent, shape)
            .onKeyEvent(center)
            .pointerInput(fav.id) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onDelete() })
            }
            .padding(vertical = 16.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FaviconBox(
            url = fav.url,
            char = fav.name.first().uppercaseChar().toString(),
            size = 44.dp,
            background = Fosforo.copy(alpha = 0.94f),
            textColor = Noche,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            fav.name,
            style = AntenaType.meta,
            color = if (focused || hover) Fosforo else Fosforo.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/* ── Ficha «+ Añadir»: primera celda de la rejilla de inicio ── */
@Composable
private fun AddFavTile(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fuente = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hover by fuente.collectIsHoveredAsState()
    val center = rememberCenterHandler(onClick)
    val shape = RoundedCornerShape(EsquinaTarjeta)
    Column(
        Modifier
            .width(138.dp)
            .hoverable(fuente)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(shape)
            .background(
                when {
                    focused -> Ambar.copy(alpha = 0.14f)
                    hover -> Fosforo.copy(alpha = 0.08f)
                    else -> Color.Transparent
                },
            )
            .border(
                if (focused) 1.5.dp else 1.dp,
                if (focused) Ambar else Fosforo.copy(alpha = 0.16f),
                shape,
            )
            .onKeyEvent(center)
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(vertical = 16.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            ToolIconGlyph(ToolIcon.NewTab, if (focused || hover) Ambar else FosforoTenue, size = 28.dp)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Añadir",
            style = AntenaType.meta,
            color = if (focused || hover) Fosforo else FosforoTenue,
            maxLines = 1,
        )
    }
}

/* ── Página de favoritos (con carpetas) ── */
@Composable
fun FavoritesPage(
    favorites: List<Favorite>,
    folders: List<Folder>,
    selectedFolder: String?,
    onSelectFolder: (String?) -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (Favorite) -> Unit,
    onNewFolder: () -> Unit,
    onDeleteFolder: (Folder) -> Unit,
    onBack: () -> Unit,
) {
    val list = if (selectedFolder == null) favorites else favorites.filter { it.folderId == selectedFolder }
    Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 36.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Favoritos", style = AntenaType.section, color = Fosforo, modifier = Modifier.weight(1f))
            RemoteChip(label = "← Volver", onClick = onBack)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "OK: abrir · mantener OK: eliminar",
            style = AntenaType.meta,
            color = FosforoTenue,
        )
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RemoteChip(label = "Todos", small = true, onClick = { onSelectFolder(null) })
            folders.forEach { f ->
                val boxed = selectedFolder == f.id
                RemoteChip(
                    label = if (boxed) "▸ ${f.name}" else f.name,
                    small = true,
                    accent = boxed,
                    onClick = { onSelectFolder(f.id) },
                    onLongClick = { onDeleteFolder(f) },
                )
            }
            RemoteChip(label = "+ Carpeta", small = true, onClick = onNewFolder)
        }

        Spacer(Modifier.height(20.dp))
        if (list.isEmpty()) {
            Text(
                "No hay favoritos aquí. Guarda uno con ★ desde la barra de direcciones.",
                style = AntenaType.body,
                color = FosforoTenue,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id }) { fav ->
                    FavoriteRow(fav, onOpen = { onOpen(fav.url) }, onDelete = { onDelete(fav) })
                }
            }
        }
    }
}

@Composable
private fun FavoriteRow(fav: Favorite, onOpen: () -> Unit, onDelete: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val center = rememberCenterHandler(onOpen, onDelete)
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(shape)
            .background(if (focused) Ambar.copy(alpha = 0.14f) else Casco)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Ambar else Azul.copy(alpha = 0.3f),
                shape = shape,
            )
            .onKeyEvent(center)
            .pointerInput(fav.id) {
                detectTapGestures(onTap = { onOpen() }, onLongPress = { onDelete() })
            }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FaviconBox(
            url = fav.url,
            char = fav.name.first().uppercaseChar().toString(),
            size = 36.dp,
            background = if (focused) Ambar else Fosforo.copy(alpha = 0.94f),
            textColor = Noche,
            textStyle = AntenaType.body,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(fav.name, style = AntenaType.body, color = if (focused) Ambar else Fosforo, maxLines = 1)
            Text(
                fav.url,
                style = AntenaType.monoSmall,
                color = FosforoTenue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/* ── Página de historial con filtros por antigüedad ── */
enum class HistoryFilter(val label: String, val windowMs: Long) {
    HOUR("Última hora", 3_600_000L),
    TODAY("Hoy", 86_400_000L),
    WEEK("Última semana", 604_800_000L),
    MONTH("Último mes", 2_592_000_000L),
    ALL("Todo", Long.MAX_VALUE),
}

@Composable
fun HistoryPage(
    entries: List<HistoryEntry>,
    filter: HistoryFilter,
    onFilter: (HistoryFilter) -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (HistoryEntry) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    /** Paso 2 · como pestaña el chip de salida cierra la pestaña («✕ Cerrar»). */
    backLabel: String = "← Volver",
) {
    val now = System.currentTimeMillis()
    val visible = entries.filter {
        filter.windowMs == Long.MAX_VALUE || now - it.visitedAt <= filter.windowMs
    }
    val dayFmt = remember { SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault()) }

    Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 36.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Historial", style = AntenaType.section, color = Fosforo, modifier = Modifier.weight(1f))
            RemoteChip(label = "Limpiar", onClick = onClear)
            Spacer(Modifier.width(10.dp))
            RemoteChip(label = backLabel, onClick = onBack)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "OK: abrir · mantener OK: quitar del historial",
            style = AntenaType.meta,
            color = FosforoTenue,
        )
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryFilter.entries.forEach { f ->
                RemoteChip(
                    label = f.label,
                    small = true,
                    accent = f == filter,
                    onClick = { onFilter(f) },
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        if (visible.isEmpty()) {
            Text(
                "Sin actividad en este periodo.",
                style = AntenaType.body,
                color = FosforoTenue,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visible, key = { it.id }) { e ->
                    HistoryRow(e, dayFmt, onOpen = { onOpen(e.url) }, onDelete = { onDelete(e) })
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    e: HistoryEntry,
    fmt: SimpleDateFormat,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val center = rememberCenterHandler(onOpen, onDelete)
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(shape)
            .background(if (focused) Ambar.copy(alpha = 0.14f) else Casco)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Ambar else Azul.copy(alpha = 0.3f),
                shape = shape,
            )
            .onKeyEvent(center)
            .pointerInput(e.id) {
                detectTapGestures(onTap = { onOpen() }, onLongPress = { onDelete() })
            }
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(fmt.format(Date(e.visitedAt)), style = AntenaType.monoSmall, color = FosforoTenue)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                e.title.ifBlank { e.url },
                style = AntenaType.body,
                color = if (focused) Ambar else Fosforo,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                e.url,
                style = AntenaType.monoSmall,
                color = FosforoTenue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/* ── Página de descargas: abrir (selector de apps), borrar, ir a carpeta ── */
@Composable
fun DownloadsPage(
    entries: List<DownloadEntry>,
    onOpenFile: (DownloadEntry) -> Unit,
    onDelete: (DownloadEntry) -> Unit,
    onOpenFolder: () -> Unit,
    onBack: () -> Unit,
    /** Paso 2 · como pestaña el chip de salida cierra la pestaña («✕ Cerrar»). */
    backLabel: String = "← Volver",
) {
    Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 36.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Descargas", style = AntenaType.section, color = Fosforo, modifier = Modifier.weight(1f))
            RemoteChip(label = "Ir a la carpeta", onClick = onOpenFolder)
            Spacer(Modifier.width(10.dp))
            RemoteChip(label = backLabel, onClick = onBack)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "OK: abrir con… · mantener OK: eliminar",
            style = AntenaType.meta,
            color = FosforoTenue,
        )
        Spacer(Modifier.height(18.dp))

        if (entries.isEmpty()) {
            Text(
                "Aún no hay descargas. Cuando descargues un archivo aparecerá aquí.",
                style = AntenaType.body,
                color = FosforoTenue,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(entries, key = { it.id }) { d ->
                    DownloadRow(d, onOpen = { onOpenFile(d) }, onDelete = { onDelete(d) })
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(d: DownloadEntry, onOpen: () -> Unit, onDelete: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val center = rememberCenterHandler(onOpen, onDelete)
    val shape = RoundedCornerShape(10.dp)
    val dateFmt = remember { SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault()) }
    Row(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(shape)
            .background(if (focused) Ambar.copy(alpha = 0.14f) else Casco)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Ambar else Azul.copy(alpha = 0.3f),
                shape = shape,
            )
            .onKeyEvent(center)
            .pointerInput(d.id) {
                detectTapGestures(onTap = { onOpen() }, onLongPress = { onDelete() })
            }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (focused) Ambar else Escudo.copy(alpha = 0.28f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("↓", style = AntenaType.panel, color = if (focused) Noche else Escudo)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                d.title,
                style = AntenaType.body,
                color = if (focused) Ambar else Fosforo,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${dateFmt.format(Date(d.addedAt))} · ${hostOf(d.url) ?: d.url}",
                style = AntenaType.monoSmall,
                color = FosforoTenue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/* ── Buscar pestañas (Fase 9) ── */

/** Nodos de la lista: encabezado de sección, encabezado de día y pestaña. */
private sealed interface TabNode {
    data class Seccion(val titulo: String, val expandida: Boolean, val total: Int) : TabNode
    data class Dia(val etiqueta: String) : TabNode
    data class Abierta(val tab: PageTab) : TabNode
    data class Cerrada(val tab: TabCerrada) : TabNode
}

/** Agrupa por día, de más reciente a más viejo, con encabezado legible. */
internal fun <T> porDia(items: List<T>, fecha: (T) -> Long): List<Pair<String, List<T>>> {
    val diaFmt = SimpleDateFormat("yyyyMMdd", Locale.US)
    val ahora = System.currentTimeMillis()
    val hoy = diaFmt.format(Date(ahora))
    val ayer = diaFmt.format(Date(ahora - 86_400_000L))
    val fechaFmt = SimpleDateFormat("dd MMM", Locale.getDefault())
    return items
        .sortedByDescending(fecha)
        .groupBy { diaFmt.format(Date(fecha(it))) }
        .map { (_, lista) ->
            val primero = fecha(lista.first())
            val clave = diaFmt.format(Date(primero))
            val titulo = when (clave) {
                hoy -> "Hoy"
                ayer -> "Ayer"
                else -> fechaFmt.format(Date(primero))
            }
            titulo to lista
        }
}

@Composable
fun BuscarTabsPage(
    abiertas: List<PageTab>,
    activaId: Int,
    cerradas: List<TabCerrada>,
    onAbrir: (PageTab) -> Unit,
    onRestaurar: (TabCerrada) -> Unit,
    onBack: () -> Unit,
) {
    var filtro by remember { mutableStateOf("") }
    var verAbiertas by remember { mutableStateOf(true) }
    var verCerradas by remember { mutableStateOf(true) }
    var campoFocado by remember { mutableStateOf(false) }

    val campo = remember { FocusRequester() }
    val fila0 = remember { FocusRequester() }
    var dentro by remember { mutableStateOf(false) }
    var enCabecera by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // Guardián de foco (mismo criterio que la bitácora): el panel es modal y
        // detrás siguen la barra y la página; si el foco escapa, vuelve al campo.
        while (true) {
            delay(400)
            if (!dentro) runCatching { campo.requestFocus() }
        }
    }

    val q = filtro.trim().lowercase()
    val abiertasF = abiertas.filter {
        q.isEmpty() || it.title.lowercase().contains(q) || it.url.lowercase().contains(q)
    }
    val cerradasF = cerradas.filter {
        q.isEmpty() || it.title.lowercase().contains(q) || it.url.lowercase().contains(q)
    }

    val nodos = buildList {
        if (q.isEmpty() || abiertasF.isNotEmpty()) {
            add(TabNode.Seccion("Pestañas abiertas", verAbiertas, abiertasF.size))
            if (verAbiertas) {
                porDia(abiertasF) { it.creadaAt }.forEach { (dia, lista) ->
                    add(TabNode.Dia(dia))
                    lista.forEach { add(TabNode.Abierta(it)) }
                }
            }
        }
        if (q.isEmpty() || cerradasF.isNotEmpty()) {
            add(TabNode.Seccion("Cerrado recientemente", verCerradas, cerradasF.size))
            if (verCerradas) {
                porDia(cerradasF) { it.cerradaAt }.forEach { (dia, lista) ->
                    add(TabNode.Dia(dia))
                    lista.forEach { add(TabNode.Cerrada(it)) }
                }
            }
        }
    }

    fun enCabeceraFoco() {
        dentro = true
        enCabecera = true
    }
    fun enListaFoco() {
        dentro = true
        enCabecera = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .onFocusChanged { dentro = it.hasFocus }
            .onPreviewKeyEvent { ev ->
                // ↓ desde el encabezado (campo/chips) entra a la lista: la página
                // de detrás se interpone en la búsqueda de foco y se come la tecla.
                if (enCabecera && ev.key == Key.DirectionDown && ev.type == KeyEventType.KeyDown) {
                    runCatching { fila0.requestFocus() }
                    true
                } else {
                    false
                }
            }
            .padding(start = 48.dp, end = 48.dp, top = 36.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.onFocusChanged { if (it.hasFocus) enCabeceraFoco() },
        ) {
            Text("Buscar pestañas", style = AntenaType.section, color = Fosforo, modifier = Modifier.weight(1f))
            Box(Modifier.onFocusChanged { if (it.hasFocus) enCabeceraFoco() }) {
                RemoteChip(label = "← Volver", onClick = onBack)
            }
        }
        Spacer(Modifier.height(12.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .focusRequester(campo)
                .onFocusChanged {
                    campoFocado = it.isFocused
                    if (it.hasFocus) enCabeceraFoco()
                }
                .clip(RoundedCornerShape(10.dp))
                .background(Noche)
                .border(
                    2.dp,
                    if (campoFocado) Ambar else Azul.copy(alpha = 0.5f),
                    RoundedCornerShape(10.dp),
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            if (filtro.isEmpty()) {
                Text("Filtrar por título o dirección", style = AntenaType.body, color = FosforoTenue)
            }
            BasicTextField(
                value = filtro,
                onValueChange = { filtro = it },
                textStyle = AntenaType.body.copy(color = Fosforo),
                cursorBrush = SolidColor(Ambar),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "${abiertasF.size} abiertas · ${cerradasF.size} cerradas · OK: abrir o restaurar",
            style = AntenaType.meta,
            color = FosforoTenue,
        )
        Spacer(Modifier.height(14.dp))

        if (nodos.isEmpty()) {
            Text(
                if (q.isEmpty()) "No hay pestañas todavía."
                else "Sin coincidencias para «$filtro».",
                style = AntenaType.body,
                color = FosforoTenue,
            )
        } else {
            Box(Modifier.focusRequester(fila0)) {
                LazyColumn(
                    Modifier.onFocusChanged { if (it.hasFocus) dentro = true },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(nodos) { n ->
                        when (n) {
                            is TabNode.Seccion -> SeccionRow(
                                titulo = n.titulo,
                                expandida = n.expandida,
                                total = n.total,
                                onFocus = { enListaFoco() },
                                onToggle = {
                                    if (n.titulo == "Pestañas abiertas") verAbiertas = !verAbiertas
                                    else verCerradas = !verCerradas
                                },
                            )
                            is TabNode.Dia -> DiaRow(n.etiqueta)
                            is TabNode.Abierta -> TabFila(
                                titulo = n.tab.title.ifBlank { hostOf(n.tab.url) ?: n.tab.url },
                                sub = if (n.tab.url.startsWith("cocosurf://")) "Inicio de Coco Surf" else n.tab.url,
                                meta = null,
                                activa = n.tab.id == activaId,
                                onOpen = { onAbrir(n.tab) },
                                onFocus = { enListaFoco() },
                            )
                            is TabNode.Cerrada -> TabFila(
                                titulo = n.tab.title.ifBlank { hostOf(n.tab.url) ?: n.tab.url },
                                sub = n.tab.url,
                                meta = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(n.tab.cerradaAt)),
                                activa = false,
                                onOpen = { onRestaurar(n.tab) },
                                onFocus = { enListaFoco() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeccionRow(
    titulo: String,
    expandida: Boolean,
    total: Int,
    onFocus: () -> Unit,
    onToggle: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val center = rememberCenterHandler(onToggle)
    val shape = RoundedCornerShape(9.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                if (it.hasFocus) onFocus()
            }
            .focusable()
            .clip(shape)
            .background(if (focused) Ambar.copy(alpha = 0.14f) else Color.Transparent)
            .onKeyEvent(center)
            .pointerInput(Unit) { detectTapGestures { onToggle() } }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (expandida) "▾" else "▸", style = AntenaType.body, color = if (focused) Ambar else Azul)
        Spacer(Modifier.width(10.dp))
        Text(
            titulo,
            style = AntenaType.body,
            color = if (focused) Ambar else Fosforo,
            modifier = Modifier.weight(1f),
        )
        Text("($total)", style = AntenaType.monoSmall, color = FosforoTenue)
    }
}

@Composable
private fun DiaRow(etiqueta: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp),
    ) {
        Text(etiqueta.uppercase(), style = AntenaType.meta, color = FosforoTenue)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Azul.copy(alpha = 0.3f)))
    }
}

@Composable
private fun TabFila(
    titulo: String,
    sub: String,
    meta: String?,
    activa: Boolean,
    onFocus: () -> Unit,
    onOpen: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val center = rememberCenterHandler(onOpen)
    val shape = RoundedCornerShape(9.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                if (it.hasFocus) onFocus()
            }
            .focusable()
            .clip(shape)
            .background(if (focused) Ambar.copy(alpha = 0.14f) else Casco)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Ambar else Azul.copy(alpha = 0.25f),
                shape = shape,
            )
            .onKeyEvent(center)
            .pointerInput(Unit) { detectTapGestures { onOpen() } }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(
                    when {
                        focused -> Ambar
                        activa -> Escudo
                        else -> Azul.copy(alpha = 0.6f)
                    },
                ),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                titulo,
                style = AntenaType.body,
                color = if (focused) Ambar else Fosforo,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                sub,
                style = AntenaType.monoSmall,
                color = FosforoTenue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (meta != null) {
            Spacer(Modifier.width(10.dp))
            Text(meta, style = AntenaType.meta, color = if (focused) Ambar else FosforoTenue)
        }
    }
}

/* ── Bitácora de la aplicación (Fase 6) ── */

@Composable
fun BitacoraPage(
    entries: List<LogEntry>,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onCopy: (LogEntry) -> Unit,
    onExportar: (() -> Unit)? = null,
) {
    var soloErrores by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val fila0 = remember { FocusRequester() }
    // Guardián de foco: el panel es modal y detrás siguen la barra y la página.
    // Estado persistente del subtree: si el foco se va fuera, vuelve al primer chip.
    var dentro by remember { mutableStateOf(false) }
    var enChips by remember { mutableStateOf(true) }
    fun enPanel() {
        dentro = true
    }
    fun chipFoco() {
        dentro = true
        enChips = true
    }
    fun filaFoco() {
        dentro = true
        enChips = false
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            if (!dentro) runCatching { first.requestFocus() }
        }
    }
    val errores = remember(entries) { entries.count { it.kind == LogKind.ERROR } }
    val shown = if (soloErrores) entries.filter { it.kind == LogKind.ERROR } else entries

    Column(
        Modifier
            .fillMaxSize()
            .onFocusChanged { dentro = it.hasFocus }
            .onPreviewKeyEvent { ev ->
                // ↓ desde los chips entra a la lista. Sin esto la página de detrás
                // se interpone en el busqueda de foco y se come la tecla.
                if (enChips && ev.key == Key.DirectionDown && ev.type == KeyEventType.KeyDown) {
                    runCatching { fila0.requestFocus() }
                    true
                } else {
                    false
                }
            }
            .padding(start = 48.dp, end = 48.dp, top = 36.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.onFocusChanged { if (it.hasFocus) enPanel() },
        ) {
            Text("Bitácora", style = AntenaType.section, color = Fosforo, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .focusRequester(first)
                    .onFocusChanged { if (it.hasFocus) chipFoco() },
            ) {
                RemoteChip(
                    label = if (soloErrores) "Ver todo" else "Solo errores",
                    accent = soloErrores,
                    small = true,
                    onClick = { soloErrores = !soloErrores },
                )
            }
            if (onExportar != null) {
                Spacer(Modifier.width(10.dp))
                Box(Modifier.onFocusChanged { if (it.hasFocus) chipFoco() }) {
                    RemoteChip(label = "Exportar", icon = ToolIcon.Share, small = true, onClick = onExportar)
                }
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.onFocusChanged { if (it.hasFocus) chipFoco() }) {
                RemoteChip(label = "Limpiar", small = true, onClick = onClear)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "${entries.size} eventos · $errores errores · más reciente arriba",
            style = AntenaType.meta,
            color = FosforoTenue,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "OK: copiar la entrada · incluye memoria, cierres de golpe y avisos del sistema · se conserva entre sesiones",
            style = AntenaType.meta,
            color = FosforoTenue,
        )
        Spacer(Modifier.height(16.dp))

        if (shown.isEmpty()) {
            Text(
                if (soloErrores) "Sin errores: nada que diagnosticar."
                else "Aún no hay eventos. Navega un poco y vuelve a mirar.",
                style = AntenaType.body,
                color = FosforoTenue,
            )
        } else {
            Box(Modifier.focusRequester(fila0)) {
                LazyColumn(
                    Modifier.onFocusChanged { if (it.hasFocus) enPanel() },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown) { e ->
                        BitacoraRow(e, onCopy = { onCopy(e) }, onFocus = { filaFoco() })
                    }
                }
            }
        }
    }
}

@Composable
private fun BitacoraRow(e: LogEntry, onCopy: () -> Unit, onFocus: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val center = rememberCenterHandler(onCopy)
    val shape = RoundedCornerShape(9.dp)
    val kindColor = when (e.kind) {
        LogKind.ERROR -> Ambar
        LogKind.SITIO -> Fosforo
        LogKind.PAGINA -> Azul
        LogKind.SISTEMA -> Azul.copy(alpha = 0.7f)
    }
    val textColor = when {
        focused -> Ambar
        e.kind == LogKind.ERROR -> Ambar
        e.kind == LogKind.SITIO -> Fosforo
        else -> FosforoTenue
    }
    Row(
        Modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                if (it.hasFocus) onFocus()
            }
            .focusable()
            .clip(shape)
            .background(if (focused) Ambar.copy(alpha = 0.14f) else Casco)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Ambar else Azul.copy(alpha = 0.25f),
                shape = shape,
            )
            .onKeyEvent(center)
            .pointerInput(e) { detectTapGestures { onCopy() } }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            e.hora(),
            style = AntenaType.monoSmall,
            color = if (focused) Ambar else FosforoTenue,
            modifier = Modifier.width(70.dp),
        )
        Text(
            e.kind.label,
            style = AntenaType.meta,
            color = kindColor,
            modifier = Modifier.width(64.dp),
        )
        Text(
            e.text,
            style = AntenaType.meta,
            color = textColor,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/* ── Descargas: integración con DownloadManager del sistema ── */

/** Inicia la descarga de un archivo con el DownloadManager y lo registra. */
/* ── Visor de PDF dentro de la app (Fase 4) ── */

private fun renderPdfPage(renderer: PdfRenderer, index: Int): android.graphics.Bitmap {
    val page = renderer.openPage(index)
    try {
        val scale = minOf(1600f / page.width, 940f / page.height)
        val w = (page.width * scale).toInt().coerceAtLeast(1)
        val h = (page.height * scale).toInt().coerceAtLeast(1)
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.WHITE)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bmp
    } finally {
        page.close()
    }
}

@Composable
fun PdfViewerScreen(entry: DownloadEntry, context: Context) {
    val rendererState = remember { mutableStateOf<PdfRenderer?>(null) }
    val errorState = remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(0) }

    DisposableEffect(entry.id) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = runCatching { dm.getUriForDownloadedFile(entry.dmId) }.getOrNull()
        var pfd: ParcelFileDescriptor? = null
        if (uri != null) {
            pfd = runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
        }
        rendererState.value = pfd?.let { runCatching { PdfRenderer(it) }.getOrNull() }
        errorState.value = rendererState.value == null
        onDispose {
            runCatching { rendererState.value?.close() }
            rendererState.value = null
            runCatching { pfd?.close() }
        }
    }

    val renderer = rendererState.value
    if (renderer == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (errorState.value) "No se pudo abrir el PDF" else "Preparando…",
                style = AntenaType.body,
                color = FosforoTenue,
            )
        }
        return
    }

    val pageCount = renderer.pageCount.coerceAtLeast(1)
    if (page >= pageCount) page = pageCount - 1
    val bmp = remember(renderer, page) {
        runCatching { renderPdfPage(renderer, page) }.getOrNull()
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .focusable()
                .onKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyUp) {
                        when (ev.key) {
                            Key.DirectionLeft -> { if (page > 0) page -= 1; true }
                            Key.DirectionRight, Key.DirectionCenter, Key.Enter -> {
                                if (page < pageCount - 1) page += 1 else page = 0
                                true
                            }
                            else -> false
                        }
                    } else false
                },
            contentAlignment = Alignment.Center,
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Página ${page + 1}",
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(Casco)
                .padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Página ${page + 1} de $pageCount",
                style = AntenaType.meta,
                color = Fosforo,
            )
            Spacer(Modifier.width(16.dp))
            Text(
                "← → cambiar de página · Atrás para salir",
                style = AntenaType.monoSmall,
                color = FosforoTenue,
            )
        }
    }
}

/* ── Reproductor de video/audio en ventana completa (Fase 4) ── */
@Composable
fun MediaPlayerScreen(url: String) {
    // Motor ÚNICO Gecko (PLAN §20): el reproductor es una GeckoView con sesión
    // propia sobre el runtime compartido — mismos códecs y DRM que una pestaña,
    // y desaparece el último android.webkit.WebView de la app. Al abrir un .mp4
    // Gecko muestra su reproductor nativo (play/pantalla completa/controles).
    AndroidView(
        factory = { ctx ->
            GeckoView(ctx).also { vista ->
                val s = GeckoSession()
                s.open(GeckoMotor.runtime(ctx))
                s.loadUri(url)
                vista.setSession(s)
            }
        },
        onRelease = { vista ->
            runCatching { vista.session?.close() }
            (vista.parent as? android.view.ViewGroup)?.removeView(vista)
        },
        modifier = Modifier.fillMaxSize().background(Noche),
    )
}

fun startSystemDownload(
    context: Context,
    url: String,
    mime: String?,
    disposition: String?,
    onRegistered: (DownloadEntry) -> Unit,
) {
    runCatching {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val name = parseFileName(disposition, url)
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(name)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            if (mime != null) setMimeType(mime)
            // Archivo en la carpeta pública Descargas de este paquete.
            setDestinationInExternalFilesDir(context, android.os.Environment.DIRECTORY_DOWNLOADS, name)
        }
        val dmId = dm.enqueue(request)
        onRegistered(
            DownloadEntry(
                id = UUID.randomUUID().toString(),
                dmId = dmId,
                url = url,
                title = name,
                addedAt = System.currentTimeMillis(),
            ),
        )
        Bitacora.add(LogKind.SISTEMA, "Descarga iniciada: $name")
        android.widget.Toast.makeText(context, "Descargando $name…", android.widget.Toast.LENGTH_LONG).show()
    }.onFailure {
        // Fase 6 · una descarga que ni arranca es el fallo más fácil de perder.
        Bitacora.add(LogKind.ERROR, "No se pudo iniciar la descarga (${it.javaClass.simpleName}) · $url")
    }
}

/** Fase 6 · anota en la bitácora cómo terminó una descarga al recibir el aviso. */
fun logDownloadResult(context: Context, id: Long) {
    runCatching {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = dm.query(DownloadManager.Query().setFilterById(id)) ?: return
        if (!cursor.moveToFirst()) return
        cursor.use { c ->
            val titulo = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)) ?: id.toString()
            val estado = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val razon = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            when (estado) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    Bitacora.add(LogKind.SISTEMA, "Descarga terminada: $titulo")
                    android.widget.Toast.makeText(context, "Descarga terminada: $titulo", android.widget.Toast.LENGTH_LONG).show()
                }
                DownloadManager.STATUS_FAILED -> {
                    Bitacora.add(LogKind.ERROR, "Descarga fallida (${motivoDescarga(razon)}) · $titulo")
                    android.widget.Toast.makeText(context, "Descarga fallida: $titulo", android.widget.Toast.LENGTH_LONG).show()
                }
                else -> Unit
            }
        }
    }.onFailure {
        Bitacora.add(LogKind.ERROR, "No se pudo leer el estado de la descarga $id")
    }
}

private fun motivoDescarga(reason: Int): String = when (reason) {
    DownloadManager.ERROR_FILE_ERROR -> "error de archivo"
    DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "código HTTP no admitido"
    DownloadManager.ERROR_HTTP_DATA_ERROR -> "error HTTP"
    DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "demasiadas redirecciones"
    DownloadManager.ERROR_INSUFFICIENT_SPACE -> "sin espacio"
    DownloadManager.ERROR_DEVICE_NOT_FOUND -> "almacenamiento no disponible"
    DownloadManager.ERROR_CANNOT_RESUME -> "no se pudo reanudar"
    DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "el archivo ya existe"
    else -> "código $reason"
}

private fun parseFileName(disposition: String?, url: String): String {
    disposition?.let { d ->
        Regex("filename\\*?=(?:UTF-8'')?\"?([^\";]+)", RegexOption.IGNORE_CASE)
            .find(d)?.groupValues?.get(1)?.let { return it.trim() }
    }
    val last = url.substringAfterLast('/').substringBefore('?')
    return if (last.isNotBlank()) last else "descarga"
}

/** Abre el archivo con el selector de aplicaciones del sistema. */
fun openDownloadedFile(context: Context, entry: DownloadEntry) {
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    // dmId < 0: archivo que la app generó ella misma (PDF de una página), no del DownloadManager.
    val uri = if (entry.dmId < 0) {
        runCatching {
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                java.io.File(entry.url.removePrefix("file://")),
            )
        }.getOrNull()
    } else {
        runCatching { dm.getUriForDownloadedFile(entry.dmId) }.getOrNull()
    }
    if (uri == null) return
    val mime = android.webkit.MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(entry.title.substringAfterLast('.', "").lowercase())
        ?: "*/*"
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(view, "Abrir con"))
}

/** Elimina la descarga (archivo + registro). */
fun deleteDownloadedFile(context: Context, entry: DownloadEntry) {
    if (entry.dmId < 0) {
        runCatching { java.io.File(entry.url.removePrefix("file://")).delete() }
        return
    }
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    runCatching { dm.remove(entry.dmId) }
}

/* ── Fase 5 · menú contextual (mantener OK / pulsación larga) ── */
/* El modelo (ContextTarget) y las reglas viven en Contexto.kt (lógica pura). */

/** Icono de cada acción del menú contextual (catálogo ToolIcon). */
private fun iconoDe(accion: AccionContexto): ToolIcon = when (accion) {
    AccionContexto.Abrir -> ToolIcon.Link
    AccionContexto.AbrirEnPestana, AccionContexto.AbrirSegundoPlano -> ToolIcon.NewTab
    AccionContexto.CopiarVinculo,
    AccionContexto.CopiarImagen,
    AccionContexto.CopiarVinculoImagen,
    AccionContexto.CopiarSeleccion,
    AccionContexto.CopiarPagina,
    -> ToolIcon.Copy
    AccionContexto.CompartirVinculo, AccionContexto.CompartirSeleccion -> ToolIcon.Share
    AccionContexto.GuardarVinculo, AccionContexto.GuardarImagen -> ToolIcon.Download
    AccionContexto.UsarComoFondo -> ToolIcon.Image
    AccionContexto.BusquedaVisual, AccionContexto.BuscarSeleccion -> ToolIcon.Search
    AccionContexto.SeleccionarTexto -> ToolIcon.Text
    AccionContexto.Atras -> ToolIcon.Back
    AccionContexto.Adelante -> ToolIcon.Forward
    AccionContexto.Actualizar -> ToolIcon.Reload
    AccionContexto.TraducirPagina -> ToolIcon.Globe
    AccionContexto.GuardarPdf -> ToolIcon.Doc
    AccionContexto.Imprimir -> ToolIcon.Print
    AccionContexto.NuevaPestana -> ToolIcon.NewTab
}

@Composable
fun ContextMenuCard(
    target: ContextTarget,
    pageUrl: String,
    puedeAtras: Boolean,
    puedeAdelante: Boolean,
    onRetryFocus: () -> Unit,
    /** Una sola salida: la fila ya sabe qué acción es (Contexto.kt decide las filas). */
    onAccion: (AccionContexto) -> Unit,
    onClose: () -> Unit,
) {
    val first = remember { FocusRequester() }
    var rowFocused by remember { mutableStateOf(false) }
    val composeView = LocalView.current
    LaunchedEffect(Unit) {
        // Solo al abrir: el foco tarda en salir del WebView. Se reclama un par
        // de veces para no secuestrar después la navegación entre filas (↓/↑).
        repeat(3) { i ->
            delay(if (i == 0) 60L else 450L)
            if (!rowFocused) {
                onRetryFocus()
                composeView.requestFocus()
                first.requestFocus()
            }
        }
    }

    val title = tituloContexto(target)
    val shown = muestraContexto(target, pageUrl)
    // Lo propio del elemento (texto, enlace, imagen) y, siempre al final, la página.
    val filas = remember(target, puedeAtras, puedeAdelante) {
        filasContexto(target).filter { it.accion != AccionContexto.CopiarPagina } +
            filasPagina(puedeAtras, puedeAdelante)
    }
    val altoMax = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp - 40.dp
    val iconoTitulo = when {
        target.img != null -> ToolIcon.Image
        !target.texto.isNullOrBlank() -> ToolIcon.Text
        else -> ToolIcon.Link
    }

    // Con cursor, la tarjeta nace junto a él (como un clic derecho); sin cursor
    // (pantalla táctil) se centra.
    val densidad = androidx.compose.ui.platform.LocalDensity.current.density
    // La tarjeta se queda donde nació: el cursor se mueve libremente sobre ella.
    val anclaX = remember { com.antena.tv.cursor.Puntero.pos.x }
    val anclaY = remember { com.antena.tv.cursor.Puntero.pos.y - com.antena.tv.cursor.Puntero.origenY }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f))
            .pointerInput(onClose) { detectTapGestures { onClose() } },
    ) {
        Column(
            Modifier
                .layout { medida, c ->
                    val p = medida.measure(c.copy(minWidth = 0, minHeight = 0))
                    val margen = (12 * densidad).toInt()
                    val x = if (com.antena.tv.cursor.Puntero.activo) {
                        anclaX.toInt() + margen
                    } else {
                        (c.maxWidth - p.width) / 2
                    }
                    val y = if (com.antena.tv.cursor.Puntero.activo) {
                        anclaY.toInt()
                    } else {
                        (c.maxHeight - p.height) / 2
                    }
                    layout(c.maxWidth, c.maxHeight) {
                        p.place(
                            x.coerceIn(margen, (c.maxWidth - p.width - margen).coerceAtLeast(margen)),
                            y.coerceIn(margen, (c.maxHeight - p.height - margen).coerceAtLeast(margen)),
                        )
                    }
                }
                .width(330.dp)
                .heightIn(max = altoMax)
                .cristal(RoundedCornerShape(EsquinaTarjeta))
                .pointerInput(Unit) { detectTapGestures { } }
                .listaDesplazable()
                .padding(vertical = 8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolIconCanvas(iconoTitulo, Ambar, 18.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = AntenaType.body.copy(fontSize = 15.sp), color = Fosforo)
                    Text(
                        shown,
                        style = AntenaType.monoSmall.copy(fontSize = 11.sp),
                        color = FosforoTenue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().height(1.dp).background(Fosforo.copy(alpha = 0.08f)))

            filas.forEachIndexed { i, fila ->
                if (fila.seccion != null) {
                    Text(
                        fila.seccion.uppercase(),
                        style = AntenaType.meta.copy(fontSize = 11.sp),
                        color = FosforoTenue,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp),
                    )
                }
                if (i == 0) {
                    Box(
                        Modifier
                            .focusRequester(first)
                            .onFocusChanged { rowFocused = it.isFocused },
                    ) {
                        MenuRow(fila.etiqueta, icon = iconoDe(fila.accion), onClick = { onAccion(fila.accion) })
                    }
                } else {
                    MenuRow(fila.etiqueta, icon = iconoDe(fila.accion), onClick = { onAccion(fila.accion) })
                }
            }

        }
    }
}

/* ── Paso 7 · panel individual de una extensión ── */
/**
 * Lo que ofrece el panel de UNA extensión: su identidad (icono real o
 * iniciales, nombre, versión y autor), su estado REAL (`list()`) y sus filas
 * individuales. Las filas las decide `filasExtension` (Extensiones.kt): no hay
 * ninguna que no se pueda ejecutar — sin página de opciones no hay fila de
 * opciones, y sin acción de barra no se ofrece «abrir su panel».
 */
@Composable
fun ExtensionCard(
    ext: ExtInfo,
    imagen: Bitmap?,
    /** Contador/estadística que la propia extensión informa (uBO: bloqueos). */
    badge: String?,
    /**
     * La extensión tiene acción de barra, así que responderá al clic con su
     * propia interfaz (popup/panel). Lo informa el motor (`acciones`).
     */
    tieneInterfaz: Boolean,
    /** Fallo real del motor (activar/desactivar…), para no ocultarlo. */
    aviso: String?,
    onRetryFocus: () -> Unit,
    onAccion: (AccionExtension) -> Unit,
    onClose: () -> Unit,
) {
    val first = remember { FocusRequester() }
    var rowFocused by remember { mutableStateOf(false) }
    val composeView = LocalView.current
    LaunchedEffect(Unit) {
        repeat(3) { i ->
            delay(if (i == 0) 60L else 450L)
            if (!rowFocused) {
                onRetryFocus()
                composeView.requestFocus()
                first.requestFocus()
            }
        }
    }

    val activa = ext.activa
    val filas = remember(ext, tieneInterfaz) { filasExtension(ext, tieneInterfaz) }
    val icono = { accion: AccionExtension ->
        when (accion) {
            AccionExtension.AbrirInterfaz -> ToolIcon.NewTab
            AccionExtension.Opciones -> ToolIcon.Gear
            AccionExtension.SitioAutor -> ToolIcon.Link
            AccionExtension.FichaAmo -> ToolIcon.Star
            AccionExtension.Alternar -> ToolIcon.Power
            AccionExtension.Administrar -> ToolIcon.Folder
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Noche.copy(alpha = 0.72f))
            .pointerInput(onClose) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(660.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Casco)
                .border(1.dp, if (activa) Ambar.copy(alpha = 0.5f) else Azul.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(vertical = 16.dp)
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExtIcono(ext, imagen, lado = 40.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(ext.nombre, style = AntenaType.panel, color = Fosforo)
                    Text(subtituloExtension(ext), style = AntenaType.monoSmall, color = FosforoTenue)
                }
                Text(
                    etiquetaEstado(ext),
                    style = AntenaType.monoSmall,
                    color = if (activa) Escudo else Ambar,
                )
            }

            ext.descripcion?.takeIf { it.isNotBlank() }?.let { desc ->
                Text(
                    desc,
                    style = AntenaType.meta,
                    color = FosforoTenue,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 10.dp),
                )
            }

            badge?.takeIf { it.isNotBlank() }?.let { b ->
                Row(
                    Modifier.padding(start = 22.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToolIconCanvas(ToolIcon.Search, Escudo, 14.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Contador de la extensión: $b", style = AntenaType.monoSmall, color = Escudo)
                }
            }

            aviso?.takeIf { it.isNotBlank() }?.let { a ->
                Text(
                    a,
                    style = AntenaType.monoSmall,
                    color = Ambar,
                    modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 10.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Azul.copy(alpha = 0.35f)))

            filas.forEachIndexed { i, fila ->
                if (i == 0) {
                    Box(
                        Modifier
                            .focusRequester(first)
                            .onFocusChanged { rowFocused = it.isFocused },
                    ) {
                        MenuRow(fila.etiqueta, icon = icono(fila.accion), onClick = { onAccion(fila.accion) })
                    }
                } else {
                    MenuRow(fila.etiqueta, icon = icono(fila.accion), onClick = { onAccion(fila.accion) })
                }
            }

            Text(
                "BACK para cerrar",
                style = AntenaType.meta,
                color = FosforoTenue,
                modifier = Modifier.padding(start = 22.dp, top = 14.dp),
            )
        }
    }
}

/* ── Portapapeles (Fase 5) ── */

fun copyTextToClipboard(context: Context, text: String) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Coco Surf", text))
    }
}

/** Paso 5 · comparte texto (selección o vínculo) con el selector de Android. */
fun compartirTexto(context: Context, texto: String) {
    if (texto.isBlank()) return
    runCatching {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, texto)
        }
        context.startActivity(Intent.createChooser(send, "Compartir"))
    }
}

private const val MAX_CLIP_BYTES = 25L * 1024 * 1024

/**
 * Copia una imagen al portapapeles como content:// (FileProvider), para que
 * otras apps puedan leerla. Devuelve false si no se pudo descargar.
 */
suspend fun copyImageToClipboard(context: Context, url: String): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val dir = java.io.File(context.cacheDir, "clip").apply { mkdirs() }
            val ext = url.substringAfterLast('.', "")
                .substringBefore('?').substringBefore('&')
                .lowercase().take(5).filter { it.isLetterOrDigit() }
            val file = java.io.File(dir, "imagen_${System.currentTimeMillis()}.${ext.ifBlank { "img" }}")
            var total = 0L
            java.net.URL(url).openStream().use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        total += n
                        if (total > MAX_CLIP_BYTES) {
                            out.close()
                            file.delete()
                            return@runCatching false
                        }
                        out.write(buf, 0, n)
                    }
                }
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            withContext(Dispatchers.Main) {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newUri(context.contentResolver, "Imagen", uri))
            }
            true
        }.getOrElse { false }
    }

/** Abre la carpeta de descargas del sistema. */
fun openDownloadsFolder(context: Context) {
    runCatching {
        context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
    }.onFailure {
        // Fallback: ajustes de almacenamiento.
        runCatching {
            context.startActivity(Intent("android.settings.INTERNAL_STORAGE_SETTINGS"))
        }
    }
}
