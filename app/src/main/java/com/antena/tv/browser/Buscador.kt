package com.antena.tv.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antena.tv.cursor.Puntero
import com.antena.tv.db.HistoryEntry
import com.antena.tv.ui.Alerta
import com.antena.tv.ui.Ambar
import com.antena.tv.ui.AntenaType
import com.antena.tv.ui.EsquinaTarjeta
import com.antena.tv.ui.Fosforo
import com.antena.tv.ui.FosforoTenue
import com.antena.tv.ui.Noche
import com.antena.tv.ui.cristal
import kotlinx.coroutines.delay

/** Dónde se busca. */
private enum class Ambito(val etiqueta: String) {
    PAGINA("En la página"),
    PESTANAS("Pestañas"),
    HISTORIAL("Historial"),
}

/**
 * Buscador pequeño, como el de cualquier navegador: cuelga de la barra y deja ver la página.
 * - «En la página»: busca el texto en la pestaña actual, resalta las coincidencias y salta entre ellas.
 * - «Pestañas»: filtra las pestañas abiertas y las cerradas hace poco por título o dirección.
 * - «Historial»: lo visitado en el pasado.
 */
@Composable
fun BuscadorModal(
    abiertas: List<PageTab>,
    activaId: Int,
    cerradas: List<TabCerrada>,
    historial: List<HistoryEntry>,
    /** ¿La pestaña actual es una página web (la única donde se puede buscar texto)? */
    hayPagina: Boolean,
    buscarEnPagina: (texto: String, atras: Boolean, alResultado: (actual: Int, total: Int) -> Unit) -> Unit,
    limpiarBusqueda: () -> Unit,
    onAbrirTab: (PageTab) -> Unit,
    onRestaurar: (TabCerrada) -> Unit,
    onAbrirUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var ambito by remember { mutableStateOf(if (hayPagina) Ambito.PAGINA else Ambito.PESTANAS) }
    var texto by remember { mutableStateOf("") }
    var actual by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var buscado by remember { mutableStateOf(false) }
    val campo = remember { FocusRequester() }

    // Mientras está abierto las flechas del mando mueven el foco entre campo, botones y resultados.
    DisposableEffect(Unit) {
        Puntero.pausadoExtra = true
        onDispose {
            Puntero.pausadoExtra = false
            limpiarBusqueda()
        }
    }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { campo.requestFocus() }
    }

    fun buscarPagina(atras: Boolean) {
        if (texto.isBlank()) {
            limpiarBusqueda(); actual = 0; total = 0; buscado = false
            return
        }
        buscarEnPagina(texto, atras) { a, t -> actual = a; total = t; buscado = true }
    }
    // La búsqueda en la página va sola mientras se escribe (con una pausa para no ahogar al motor).
    LaunchedEffect(texto, ambito) {
        if (ambito == Ambito.PAGINA) {
            delay(300)
            buscarPagina(false)
        } else {
            limpiarBusqueda()
        }
    }

    val q = texto.trim().lowercase()
    fun coincide(titulo: String, url: String) =
        q.isEmpty() || titulo.lowercase().contains(q) || url.lowercase().contains(q)

    Column(
        modifier
            .width(560.dp)
            .cristal(RoundedCornerShape(EsquinaTarjeta), elevacion = 24.dp)
            .padding(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Ambito.entries.forEach { a ->
                RemoteChip(
                    label = a.etiqueta,
                    small = true,
                    accent = a == ambito,
                    enabled = a != Ambito.PAGINA || hayPagina,
                    onClick = { ambito = a },
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        var enfocado by remember { mutableStateOf(false) }
        val forma = RoundedCornerShape(12.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(forma)
                .background(Noche)
                .border(if (enfocado) 2.dp else 1.dp, if (enfocado) Ambar else Fosforo.copy(alpha = 0.08f), forma)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolIconGlyph(ToolIcon.Search, if (enfocado) Ambar else FosforoTenue, size = 20.dp)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (texto.isEmpty()) {
                    Text(
                        when (ambito) {
                            Ambito.PAGINA -> "Buscar texto en esta página"
                            Ambito.PESTANAS -> "Buscar por título o dirección"
                            Ambito.HISTORIAL -> "Buscar en lo que has visitado"
                        },
                        style = AntenaType.body,
                        color = FosforoTenue,
                    )
                }
                BasicTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    singleLine = true,
                    textStyle = AntenaType.body.copy(color = Fosforo),
                    cursorBrush = SolidColor(Ambar),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (ambito == Ambito.PAGINA) buscarPagina(false) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(campo)
                        .onFocusChanged { enfocado = it.isFocused },
                )
            }
            if (ambito == Ambito.PAGINA && texto.isNotBlank() && buscado) {
                Text(
                    if (total > 0) "$actual de $total" else "Sin resultados",
                    style = AntenaType.meta.copy(fontSize = 13.sp),
                    color = if (total > 0) FosforoTenue else Alerta,
                )
            }
        }

        when (ambito) {
            Ambito.PAGINA -> {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RemoteChip(label = "Anterior", small = true, enabled = total > 0, onClick = { buscarPagina(true) })
                    RemoteChip(label = "Siguiente", small = true, enabled = total > 0, accent = total > 0, onClick = { buscarPagina(false) })
                }
            }
            Ambito.PESTANAS -> {
                val ab = abiertas.filter { coincide(it.title, it.url) }
                val ce = cerradas.filter { coincide(it.title, it.url) }
                Spacer(Modifier.height(8.dp))
                Column(Modifier.heightIn(max = 300.dp).listaDesplazable(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (ab.isEmpty() && ce.isEmpty()) SinResultados(q.isEmpty(), "No hay pestañas todavía.")
                    ab.forEach { t ->
                        FilaSitio(
                            titulo = t.title.ifBlank { hostOf(t.url) ?: t.url } + if (t.id == activaId) "  ·  actual" else "",
                            url = t.url.takeUnless { it.startsWith("cocosurf://") } ?: "",
                            hora = null,
                            onAbrir = { onAbrirTab(t) },
                            onEliminar = null,
                        )
                    }
                    if (ce.isNotEmpty()) {
                        Text("CERRADAS HACE POCO", style = AntenaType.meta.copy(fontSize = 12.sp), color = FosforoTenue, modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 2.dp))
                        ce.forEach { t ->
                            FilaSitio(
                                titulo = t.title.ifBlank { t.url },
                                url = t.url,
                                hora = t.cerradaAt,
                                onAbrir = { onRestaurar(t) },
                                onEliminar = null,
                            )
                        }
                    }
                }
            }
            Ambito.HISTORIAL -> {
                val hs = historial.filter { coincide(it.title, it.url) }.take(40)
                Spacer(Modifier.height(8.dp))
                Column(Modifier.heightIn(max = 300.dp).listaDesplazable(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (hs.isEmpty()) SinResultados(q.isEmpty(), "Aún no hay historial.")
                    hs.forEach { e ->
                        FilaSitio(
                            titulo = e.title.ifBlank { e.url },
                            url = e.url,
                            hora = e.visitedAt,
                            onAbrir = { onAbrirUrl(e.url) },
                            onEliminar = null,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Atrás: cerrar", style = AntenaType.meta.copy(fontSize = 12.sp), color = FosforoTenue, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun SinResultados(vacia: Boolean, sinDatos: String) {
    Text(
        if (vacia) sinDatos else "Sin coincidencias.",
        style = AntenaType.body,
        color = FosforoTenue,
        modifier = Modifier.padding(8.dp),
    )
}
