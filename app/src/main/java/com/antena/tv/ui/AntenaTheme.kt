package com.antena.tv.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.antena.tv.R

/*
 * Coco Surf · sistema de diseño (oscuro, minimalista).
 *
 * Reglas, para que todo se vea de una pieza:
 *  - Una sola familia de color: fondo `Noche`, superficies de cristal `Casco`,
 *    texto `Fosforo` y UN acento que el usuario elige (`Ambar` conserva su nombre
 *    histórico, pero es el acento del perfil, no necesariamente naranja).
 *  - Líneas finas (1 dp) y sombras suaves en vez de bordes gruesos.
 *  - El acento sólo marca foco, selección y lo importante; el resto es neutro.
 *  - Esquinas: 10 (chips) · 12 (botones y filas) · 16 (tarjetas) · 22 (ventanas).
 */

/** Acentos que el usuario puede elegir (se guarda en su perfil). */
data class AcentoTema(val id: String, val nombre: String, val color: Color)

val ACENTOS = listOf(
    AcentoTema("turquesa", "Turquesa", Color(0xFF2BD9C5)),
    AcentoTema("cielo", "Cielo", Color(0xFF5AA9FF)),
    AcentoTema("violeta", "Violeta", Color(0xFF9D8CFF)),
    AcentoTema("rosa", "Rosa", Color(0xFFFF7AB6)),
    AcentoTema("coral", "Coral", Color(0xFFFF8F6B)),
    AcentoTema("dorado", "Dorado", Color(0xFFF2C14E)),
    AcentoTema("menta", "Menta", Color(0xFF7EE0A0)),
    AcentoTema("blanco", "Blanco", Color(0xFFE9EDF4)),
)

/** Estado del tema: cambiar `acento` repinta toda la interfaz al instante. */
object TemaApp {
    var acento by mutableStateOf(ACENTOS.first().color)
        private set
    var acentoId by mutableStateOf(ACENTOS.first().id)
        private set

    fun elegir(id: String) {
        val a = ACENTOS.firstOrNull { it.id == id } ?: ACENTOS.first()
        acento = a.color
        acentoId = a.id
    }
}

// Superficies
val Noche = Color(0xFF0A0D12)
val Casco = Color(0xFF131923)
val Fosforo = Color(0xFFE9EDF4)
val Azul = Color(0xFF7C8AA5)
val Escudo = Color(0xFF6FD6A0)
val FosforoTenue = Fosforo.copy(alpha = 0.62f)

/** El acento del perfil. (Nombre histórico: antes era el ámbar fijo.) */
val Ambar: Color get() = TemaApp.acento

/** Acciones destructivas o de salida. */
val Alerta = Color(0xFFFF6B6B)

private val Atkinson = FontFamily(
    Font(R.font.atkinson_regular, FontWeight.Normal),
    Font(R.font.atkinson_bold, FontWeight.Bold),
)

private val PlexMono = FontFamily(
    Font(R.font.plex_mono, FontWeight.Normal),
)

object AntenaType {
    val display = TextStyle(
        fontFamily = Atkinson, fontWeight = FontWeight.Bold,
        fontSize = 44.sp, lineHeight = 50.sp,
    )
    val section = TextStyle(
        fontFamily = Atkinson, fontWeight = FontWeight.Bold,
        fontSize = 30.sp, lineHeight = 36.sp,
    )
    val panel = TextStyle(
        fontFamily = Atkinson, fontWeight = FontWeight.Bold,
        fontSize = 22.sp, lineHeight = 28.sp,
    )
    val body = TextStyle(
        fontFamily = Atkinson, fontSize = 17.sp, lineHeight = 24.sp,
    )
    val meta = TextStyle(
        fontFamily = Atkinson, fontSize = 14.sp, lineHeight = 19.sp,
    )
    val mono = TextStyle(
        fontFamily = PlexMono, fontSize = 15.sp, lineHeight = 22.sp,
    )
    val monoSmall = TextStyle(
        fontFamily = PlexMono, fontSize = 13.sp, lineHeight = 19.sp,
    )
}

@Composable
fun AntenaTheme(content: @Composable () -> Unit) {
    val acento = TemaApp.acento
    val esquema = darkColorScheme(
        primary = acento,
        onPrimary = Noche,
        background = Noche,
        onBackground = Fosforo,
        surface = Casco,
        onSurface = Fosforo,
        secondary = Azul,
        onSecondary = Fosforo,
    )
    MaterialTheme(colorScheme = esquema, content = content)
}
