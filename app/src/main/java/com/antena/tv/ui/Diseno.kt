package com.antena.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Piezas comunes del sistema de diseño. Todo lo que quiera verse «de Coco Surf»
 * parte de aquí: superficie de cristal, foco suave y interruptor.
 */

val EsquinaChip = 10.dp
val EsquinaBoton = 12.dp
val EsquinaTarjeta = 16.dp
val EsquinaVentana = 22.dp

/**
 * Superficie de cristal: degradado translúcido, línea de 1 dp que se aclara en la
 * esquina de arriba (la «luz») y una sombra amplia y suave que la separa del fondo.
 */
fun Modifier.cristal(
    forma: Shape = RoundedCornerShape(EsquinaTarjeta),
    elevacion: Dp = 22.dp,
    opacidad: Float = 0.97f,
): Modifier = this
    .shadow(elevacion, forma, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
    .clip(forma)
    .background(
        Brush.verticalGradient(
            listOf(
                Color(0xFF1A2230).copy(alpha = opacidad),
                Color(0xFF0F151E).copy(alpha = opacidad),
            ),
        ),
    )
    .border(
        1.dp,
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.10f)),
        ),
        forma,
    )

/** Fondo neutro y plano para filas y campos dentro de una tarjeta. */
fun Modifier.plano(forma: Shape = RoundedCornerShape(EsquinaBoton), alfa: Float = 0.05f): Modifier =
    this.clip(forma).background(Fosforo.copy(alpha = alfa))

/** Interruptor fino, con el color del acento cuando está encendido. */
@Composable
fun Interruptor(encendido: Boolean, alCambiar: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    var enfocado by remember { mutableStateOf(false) }
    val fuente = remember { MutableInteractionSource() }
    val hover by fuente.collectIsHoveredAsState()
    val pista = if (encendido) Ambar.copy(alpha = 0.85f) else Fosforo.copy(alpha = 0.16f)
    Box(
        modifier
            .hoverable(fuente)
            .onFocusChanged { enfocado = it.isFocused }
            .focusable()
            .width(46.dp)
            .height(26.dp)
            .clip(CircleShape)
            .background(pista)
            .border(
                if (enfocado) 1.5.dp else 1.dp,
                if (enfocado) Ambar else Fosforo.copy(alpha = if (hover) 0.35f else 0.14f),
                CircleShape,
            )
            .onKeyEvent { ev ->
                val ok = ev.key == androidx.compose.ui.input.key.Key.DirectionCenter ||
                    ev.key == androidx.compose.ui.input.key.Key.Enter
                if (ok && ev.type == androidx.compose.ui.input.key.KeyEventType.KeyUp) {
                    alCambiar(!encendido)
                    true
                } else {
                    false
                }
            }
            .pointerInput(encendido) { detectTapGestures { alCambiar(!encendido) } },
        contentAlignment = if (encendido) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (encendido) Noche else Fosforo.copy(alpha = 0.85f)),
        )
    }
}

/** Línea separadora de 1 dp. */
@Composable
fun Separador(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Fosforo.copy(alpha = 0.08f)))
}

/** Pastilla de color del selector de acento. */
@Composable
fun MuestraColor(color: Color, elegido: Boolean, alElegir: () -> Unit) {
    var enfocado by remember { mutableStateOf(false) }
    val fuente = remember { MutableInteractionSource() }
    val hover by fuente.collectIsHoveredAsState()
    Box(
        Modifier
            .hoverable(fuente)
            .onFocusChanged { enfocado = it.isFocused }
            .focusable()
            .size(40.dp)
            .clip(CircleShape)
            .border(
                if (elegido || enfocado) 2.dp else 1.dp,
                if (elegido) Fosforo else if (enfocado || hover) Fosforo.copy(alpha = 0.7f) else Fosforo.copy(alpha = 0.15f),
                CircleShape,
            )
            .padding(5.dp)
            .clip(CircleShape)
            .background(color)
            .onKeyEvent { ev ->
                val ok = ev.key == androidx.compose.ui.input.key.Key.DirectionCenter ||
                    ev.key == androidx.compose.ui.input.key.Key.Enter
                if (ok && ev.type == androidx.compose.ui.input.key.KeyEventType.KeyUp) {
                    alElegir()
                    true
                } else {
                    false
                }
            }
            .pointerInput(Unit) { detectTapGestures { alElegir() } },
    )
}
