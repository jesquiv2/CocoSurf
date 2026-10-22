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
import com.antena.tv.gecko.MemoriaSistema
import com.antena.tv.ui.Azul
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

            // ── RAM de todo el aparato ──
            val sis by produceState<MemoriaSistema.Desglose?>(null) {
                while (true) {
                    value = withContext(Dispatchers.IO) { MemoriaSistema.leer() }
                    delay(2000)
                }
            }
            sis?.let { d -> SeccionAjustes("Dónde está la RAM del televisor") { RepartoRam(d) } }

            // ── Procesos de Coco Surf (los de las demás apps no se pueden leer desde dentro) ──
            val propios by produceState(emptyList<com.antena.tv.gecko.Recursos.Proceso>()) {
                while (true) {
                    value = withContext(Dispatchers.Default) { com.antena.tv.gecko.Recursos.procesos(contexto) }
                    delay(3000)
                }
            }
            SeccionAjustes("Procesos de Coco Surf") {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    propios.sortedByDescending { it.mb }.forEach { pr ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(pr.etiqueta, style = AntenaType.body, color = FosforoTenue)
                            Text("${pr.mb} MB", style = AntenaType.body, color = Fosforo)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Para ver quién consume en las DEMÁS apps y en qué momento se dispara, Android sólo lo " +
                            "permite desde un PC: usa herramientas/memoria-tv.ps1 (viene en el repositorio) con " +
                            "el televisor conectado por adb. Guarda un CSV con cada proceso cada pocos segundos.",
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


/** Barra apilada del reparto de la RAM y su leyenda. */
@Composable
private fun RepartoRam(d: MemoriaSistema.Desglose) {
    val partes = listOf(
        Triple("Libre", d.libreMb, Escudo),
        Triple("Caché de archivos (se suelta sola)", d.cacheMb, FosforoTenue),
        Triple("Apps y servicios", d.appsMb, Azul),
        Triple("Kernel", d.kernelMb, Ambar),
        Triple("Controladores de vídeo/gráficos (sin contabilizar)", d.sinContabilizarMb, Alerta),
    )
    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
            partes.forEach { (_, mb, c) ->
                Box(Modifier.weight(mb.coerceAtLeast(1).toFloat()).fillMaxHeight().background(c))
            }
        }
        Spacer(Modifier.height(10.dp))
        partes.forEach { (nombre, mb, c) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(10.dp).height(10.dp).clip(CircleShape).background(c))
                Spacer(Modifier.width(10.dp))
                Text(nombre, style = AntenaType.body, color = FosforoTenue, modifier = Modifier.weight(1f))
                Text("$mb MB · ${if (d.totalMb > 0) mb * 100 / d.totalMb else 0} %", style = AntenaType.body, color = Fosforo)
            }
        }
        Spacer(Modifier.height(6.dp))
        val extra = buildList {
            add("Total ${d.totalMb} MB · disponible sin matar apps: ${d.disponibleMb} MB")
            if (d.compartidaMb > 0) add("Compartida (dentro de la caché): ${d.compartidaMb} MB")
            if (d.cmaTotalMb > 0) add("Reserva CMA de vídeo: ${d.cmaUsadaMb} de ${d.cmaTotalMb} MB en uso")
            if (d.swapUsadoMb > 0) add("Swap en uso: ${d.swapUsadoMb} MB")
        }
        extra.forEach { Text(it, style = AntenaType.meta, color = FosforoTenue) }
    }
}
