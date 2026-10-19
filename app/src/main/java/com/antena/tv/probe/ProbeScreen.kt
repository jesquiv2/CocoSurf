package com.antena.tv.probe

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.antena.tv.ui.AntenaType
import com.antena.tv.ui.Ambar
import com.antena.tv.ui.Azul
import com.antena.tv.ui.Escudo
import com.antena.tv.ui.Fosforo
import com.antena.tv.ui.FosforoTenue

private sealed interface ProbeUi {
    data object Loading : ProbeUi
    data class Ready(val result: ProbeResult) : ProbeUi
    data class Failed(val message: String) : ProbeUi
}

@Composable
fun ProbeScreen() {
    val context = LocalContext.current
    var state by remember { mutableStateOf<ProbeUi>(ProbeUi.Loading) }

    LaunchedEffect(Unit) {
        state = try {
            ProbeUi.Ready(Probe.collect(context))
        } catch (t: Throwable) {
            ProbeUi.Failed("${t.javaClass.simpleName}: ${t.message ?: "sin detalle"}")
        }
    }

    when (val s = state) {
        is ProbeUi.Loading -> Loading()
        is ProbeUi.Failed -> Failed(s.message)
        is ProbeUi.Ready -> Content(s.result)
    }
}

@Composable
private fun Loading() {
    Column {
        Text2("Leyendo el televisor…", AntenaType.panel, Fosforo)
        Spacer(Modifier.height(8.dp))
        Text2(
            "Recopilo los códecs de vídeo, el DRM y la WebView del TV (a título informativo: Coco Surf navega con GeckoView).",
            AntenaType.meta, Azul,
        )
    }
}

@Composable
private fun Failed(message: String) {
    Column {
        Text2("No pude leer el televisor", AntenaType.panel, Ambar)
        Spacer(Modifier.height(10.dp))
        Text2("La sonda falló con: $message", AntenaType.body, Fosforo)
        Spacer(Modifier.height(8.dp))
        Text2(
            "Reinicia la app. Si vuelve a fallar, mira la Bitácora: la navegación es GeckoView y no depende de la WebView del TV.",
            AntenaType.meta, Azul,
        )
    }
}

@Composable
private fun Content(r: ProbeResult) {
    val widevine = when (r.widevine?.uppercase()) {
        "L1" -> "L1 · descifrado en hardware"
        "L3" -> "L3 · descifrado en software"
        "L2" -> "L2"
        null -> "No disponible en este TV"
        else -> r.widevine
    }
    val gms = r.gms?.let { "sí · $it" } ?: "no instalado"
    val play = r.playStore?.let { "sí · $it" } ?: "no instalada"

    Column(Modifier.verticalScroll(rememberScrollState())) {
            Text2("Este aparato", AntenaType.section, Fosforo)
            Spacer(Modifier.height(6.dp))
            Text2(
                "Equipo, memoria, pantalla y capacidades de vídeo",
                AntenaType.meta, FosforoTenue,
            )

            SecTitle("Equipo")
            Field("Equipo", "${r.manufacturer} ${r.model}")
            Field("Sistema", r.system)
            Field("Versión de Coco Surf", r.appVersion)

            SecTitle("Memoria")
            Field("RAM", r.ram)

            SecTitle("Pantalla")
            Field("Resolución", r.screen)

            SecTitle("Navegación")
            Field("Motor de página", "GeckoView (Firefox) · con extensiones")
            Field("WebView del TV (informativo)", r.webView)
            Field("Agente de usuario (WebView)", r.userAgent)

            SecTitle("Códecs de vídeo")
            r.codecs.forEach { CodecField(it) }
            Field("Decodificadores instalados", r.decoderCount.toString())

            SecTitle("DRM")
            Field("Widevine", widevine)

            SecTitle("Servicios de Google")
            Field("Google Play services", gms)
            Field("Play Store", play)

            SecTitle("Contraseñas y credenciales")
            Field("Autofill del sistema", r.autofill)
            Field("Passkeys", r.passkeys)

            SecTitle("Almacenamiento")
            Field("Disco de datos", r.storage)

            Spacer(Modifier.height(48.dp))
        }
    }

@Composable
private fun Text2(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Text(text = text, style = style, color = color, modifier = modifier)
}

@Composable
private fun SecTitle(text: String) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .background(if (focused) Ambar.copy(alpha = 0.10f) else Color.Transparent)
            .padding(top = 30.dp, bottom = 10.dp, start = if (focused) 8.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box2()
        Spacer(Modifier.width(14.dp))
        Text2(text, AntenaType.panel, Fosforo)
    }
}

@Composable
private fun Box2() {
    Spacer(
        Modifier
            .size(10.dp)
            .background(Ambar),
    )
}

@Composable
private fun Field(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text2(label, AntenaType.meta, Azul, Modifier.width(250.dp))
        Spacer(Modifier.width(16.dp))
        Text2(value, AntenaType.mono, Fosforo, Modifier.weight(1f))
    }
}

@Composable
private fun CodecField(row: CodecRow) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text2(row.label, AntenaType.meta, Azul, Modifier.width(250.dp))
        Spacer(Modifier.width(16.dp))
        Text2(
            if (row.supported) "sí" else "no",
            AntenaType.mono,
            if (row.supported) Escudo else Azul,
        )
        Spacer(Modifier.width(16.dp))
        Text2(
            row.sample ?: if (row.supported) "" else "sin decodificador",
            AntenaType.monoSmall,
            FosforoTenue,
            Modifier.weight(1f),
        )
    }
}
