package com.antena.tv.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.antena.tv.gecko.Memoria
import com.antena.tv.ui.Alerta
import com.antena.tv.ui.Ambar
import com.antena.tv.ui.AntenaType
import com.antena.tv.ui.Escudo
import com.antena.tv.ui.Fosforo
import com.antena.tv.ui.FosforoTenue
import com.antena.tv.ui.Separador
import kotlinx.coroutines.delay

/** Cifras de pestañas, tomadas de la pantalla principal cada segundo. */
data class CifrasPestanas(val vivas: Int, val descartadas: Int, val total: Int)

/**
 * Diagnóstico en vivo: memoria libre y presión, pestañas con motor encendido o
 * descartadas, y un botón para liberar memoria al instante. Sirve para entender
 * por qué una página va lenta o por qué la app soltó una pestaña.
 */
@Composable
fun DiagnosticoPage(
    cifras: () -> CifrasPestanas,
    version: String,
    onLiberar: () -> Int,
) {
    val contexto = LocalContext.current
    var liberadas by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    val estado by produceState(Memoria.leer(contexto) to cifras()) {
        while (true) {
            value = Memoria.leer(contexto) to cifras()
            delay(1000)
        }
    }
    val mem = estado.first
    val pest = estado.second
    val usado = (mem.totalMb - mem.libreMb).coerceAtLeast(0)
    val fraccion = if (mem.totalMb > 0) usado.toFloat() / mem.totalMb else 0f
    val colorPresion = when (mem.presion) {
        Memoria.Presion.NORMAL -> Escudo
        Memoria.Presion.BAJA -> Ambar
        Memoria.Presion.CRITICA, Memoria.Presion.EXTREMA -> Alerta
    }
    val tope = Memoria.maxVivas(contexto)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
            SeccionAjustes("Memoria") {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${mem.libreMb} MB libres de ${mem.totalMb} MB",
                            style = AntenaType.body,
                            color = Fosforo,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            Memoria.etiqueta(mem.presion),
                            style = AntenaType.body,
                            color = colorPresion,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Fosforo.copy(alpha = 0.10f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraccion.coerceIn(0.02f, 1f))
                                .background(colorPresion),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (mem.pocaRam) {
                            "Aparato con poca memoria: modo ahorro activo (se sueltan las pestañas de fondo)."
                        } else {
                            "Memoria holgada."
                        },
                        style = AntenaType.meta,
                        color = FosforoTenue,
                    )
                }
            }

            SeccionAjustes("Pestañas") {
                FilaDato("Con motor encendido", "${pest.vivas} de máximo $tope")
                Separador(Modifier.padding(horizontal = 14.dp))
                FilaDato("Descartadas (se recargan al abrirlas)", "${pest.descartadas}")
                Separador(Modifier.padding(horizontal = 14.dp))
                FilaDato("Abiertas en total", "${pest.total}")
            }

            SeccionAjustes("Motor") {
                FilaDato("Aplicación", "Coco Surf $version")
                Separador(Modifier.padding(horizontal = 14.dp))
                FilaDato("Motor web", "Firefox ${runCatching { org.mozilla.geckoview.BuildConfig.MOZ_APP_VERSION }.getOrDefault("—")}")
                Separador(Modifier.padding(horizontal = 14.dp))
                FilaDato("Procesos de contenido", "uno compartido (ahorra memoria)")
            }

            Row(
                Modifier.padding(start = 6.dp, bottom = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RemoteChip(
                    label = "Liberar memoria ahora",
                    small = true,
                    accent = true,
                    onClick = { liberadas = onLiberar() },
                )
                liberadas?.let {
                    Spacer(Modifier.width(14.dp))
                    Text(
                        if (it > 0) "Se soltaron $it pestañas de fondo." else "No había pestañas de fondo que soltar.",
                        style = AntenaType.meta,
                        color = FosforoTenue,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilaDato(etiqueta: String, valor: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(etiqueta, style = AntenaType.body, color = FosforoTenue, modifier = Modifier.weight(1f))
        Text(valor, style = AntenaType.body, color = Fosforo)
    }
}
