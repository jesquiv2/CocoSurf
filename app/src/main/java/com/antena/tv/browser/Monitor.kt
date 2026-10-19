package com.antena.tv.browser

import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antena.tv.gecko.Memoria
import com.antena.tv.gecko.Recursos.Muestra
import com.antena.tv.ui.AntenaType

/** Una cifra que el monitor puede mostrar. */
class MetricaMonitor(
    val id: String,
    val nombre: String,
    val color: Color,
    /** Valor para el gráfico (null = sin gráfico). */
    val serie: ((Muestra) -> Float?)? = null,
    /** Valor máximo de la escala del gráfico (null = el máximo de lo visto). */
    val maximo: ((Muestra) -> Float)? = null,
    val texto: (Muestra) -> String,
)

private val VERDE = Color(0xFF4CAF68)
private val AZUL = Color(0xFF4A9BD9)
private val NARANJA = Color(0xFFFF8A3D)
private val ROJO = Color(0xFFE5645A)
private val MORADO = Color(0xFFB08CFF)

/** Todo lo que se puede elegir en Configuración → Monitor. */
val METRICAS_MONITOR = listOf(
    MetricaMonitor("ram", "RAM libre", VERDE, { it.libreMb.toFloat() }, { it.totalMb.toFloat() }) { "${it.libreMb} MB" },
    MetricaMonitor("presion", "Presión", NARANJA) { Memoria.etiqueta(it.presion) },
    MetricaMonitor("paginas", "Páginas", AZUL, { it.paginasMb.toFloat() }) { "${it.paginasMb} MB" },
    MetricaMonitor("complementos", "Complem.", MORADO, { it.complementosMb.toFloat() }) { "${it.complementosMb} MB" },
    MetricaMonitor("graficos", "Gráficos", AZUL, { it.graficosMb.toFloat() }) { "${it.graficosMb} MB" },
    MetricaMonitor("app", "Interfaz", AZUL, { it.appMb.toFloat() }) { "${it.appMb} MB" },
    // Algunos Android no dejan leer la CPU de todo el aparato: entonces se muestra la de la app.
    MetricaMonitor("cpu", "CPU", NARANJA, { it.cpuTotal ?: it.cpuApp }, { 100f }) { m ->
        m.cpuTotal?.let { "${it.toInt()} %" } ?: m.cpuApp?.let { "${it.toInt()} % app" } ?: "—"
    },
    MetricaMonitor("cpuapp", "CPU app", NARANJA, { it.cpuApp }, { 100f }) { m ->
        m.cpuApp?.let { "${it.toInt()} %" } ?: "—"
    },
    MetricaMonitor("red", "Red ↓", VERDE, { it.redKbs }) { "${it.redKbs.toInt()} KB/s" },
    MetricaMonitor("fps", "FPS UI", Color.White, { it.fps.toFloat() }, { 60f }) { "${it.fps}" },
    MetricaMonitor("termico", "Térmico", ROJO) { m ->
        when (m.termico) {
            -1 -> "—"
            0 -> "normal"
            1 -> "ligero"
            2 -> "moderado"
            3 -> "severo"
            else -> "crítico"
        }
    },
    MetricaMonitor("pestanas", "Pestañas", Color.White) { "${it.vivas}" },
)

/** Estado del monitor en pantalla (se guarda en la configuración). */
object MonitorConfig {
    var visible by mutableStateOf(false)
    /** "sup_izq", "sup_der", "inf_izq" o "inf_der". */
    var posicion by mutableStateOf("sup_der")
    var graficas by mutableStateOf(true)
    var metricas by mutableStateOf(setOf("ram", "presion", "paginas", "complementos", "cpu", "fps"))
    var tecla by mutableIntStateOf(KeyEvent.KEYCODE_INFO)
    /** Esperando que el usuario pulse la tecla que abrirá el monitor. */
    var capturando by mutableStateOf(false)

    val POSICIONES = listOf(
        "sup_izq" to "Arriba izquierda",
        "sup_der" to "Arriba derecha",
        "inf_izq" to "Abajo izquierda",
        "inf_der" to "Abajo derecha",
    )

    /** Nombre legible de una tecla del mando. */
    fun nombreTecla(codigo: Int): String =
        KeyEvent.keyCodeToString(codigo).removePrefix("KEYCODE_").replace('_', ' ').lowercase()
            .replaceFirstChar { it.uppercase() }

    /** Teclas que no se pueden usar (las necesita el cursor y la navegación). */
    fun reservada(codigo: Int) = codigo in setOf(
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_HOME,
    )
}

/**
 * Monitor de recursos semitransparente, a la manera de MSI Afterburner: etiqueta y
 * cifra a la izquierda y su gráfico al lado (no debajo), para ocupar poca altura.
 */
@Composable
fun MonitorRecursos(muestras: List<Muestra>, modifier: Modifier = Modifier) {
    val ultima = muestras.lastOrNull() ?: return
    val elegidas = METRICAS_MONITOR.filter { it.id in MonitorConfig.metricas }
    if (elegidas.isEmpty()) return
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.52f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        elegidas.forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    m.nombre,
                    style = AntenaType.monoSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    color = m.color,
                    modifier = Modifier.width(78.dp),
                )
                val cifra = m.texto(ultima)
                Text(
                    cifra,
                    style = AntenaType.monoSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    color = if (m.id == "ram" && ultima.presion >= Memoria.Presion.CRITICA) ROJO else Color.White,
                    modifier = Modifier.width(74.dp),
                )
                if (MonitorConfig.graficas && m.serie != null) {
                    Spacer(Modifier.width(6.dp))
                    Sparkline(muestras, m, Modifier.size(width = 64.dp, height = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun Sparkline(muestras: List<Muestra>, m: MetricaMonitor, modifier: Modifier) {
    val valores = muestras.takeLast(60).map { m.serie?.invoke(it) }
    Canvas(modifier) {
        val v = valores.filterNotNull()
        if (v.size < 2) return@Canvas
        val tope = m.maximo?.invoke(muestras.last()) ?: (v.max().coerceAtLeast(1f))
        val camino = Path()
        valores.forEachIndexed { i, x ->
            val px = i * size.width / (valores.size - 1).coerceAtLeast(1)
            val py = size.height - (((x ?: 0f) / tope).coerceIn(0f, 1f)) * size.height
            if (i == 0) camino.moveTo(px, py) else camino.lineTo(px, py)
        }
        // Raya base tenue y la línea del valor.
        drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        drawPath(camino, m.color.copy(alpha = 0.95f), style = Stroke(1.5.dp.toPx()))
    }
}
