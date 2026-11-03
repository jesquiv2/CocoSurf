package com.antena.tv.browser

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antena.tv.cursor.Puntero
import com.antena.tv.ui.AntenaType
import com.antena.tv.ui.EsquinaVentana
import com.antena.tv.ui.Fosforo
import com.antena.tv.ui.FosforoTenue
import com.antena.tv.ui.cristal
import kotlinx.coroutines.delay

/**
 * Una opción del modal de acciones. Con [confirmar] pide una segunda pulsación antes de
 * ejecutarse (borrados); con [sub] abre otra lista (mover a una carpeta).
 */
class AccionItem(
    val etiqueta: String,
    val icono: ToolIcon,
    val peligro: Boolean = false,
    /** Pregunta de confirmación; null = se ejecuta de inmediato. */
    val confirmar: String? = null,
    val sub: List<AccionItem>? = null,
    val alElegir: () -> Unit = {},
)

/**
 * Modal de «¿qué quiero hacer con esto?»: se abre al mantener OK sobre un favorito, una
 * entrada del historial, una descarga… Vive en la raíz de la pantalla (ver [AccionesHost]),
 * así las listas sólo llaman a [Acciones.mostrar] con sus opciones.
 */
object Acciones {
    class Pedido(val titulo: String, val detalle: String?, val items: List<AccionItem>)

    var pedido by mutableStateOf<Pedido?>(null)
        private set

    fun mostrar(titulo: String, detalle: String? = null, items: List<AccionItem>) {
        pedido = Pedido(titulo, detalle, items)
    }

    fun cerrar() {
        pedido = null
    }
}

private sealed interface Vista {
    class Sub(val padre: AccionItem) : Vista
    class Confirmar(val item: AccionItem) : Vista
}

@Composable
fun AccionesHost() {
    val p = Acciones.pedido ?: return
    var pila by remember(p) { mutableStateOf<List<Vista>>(emptyList()) }
    val vista = pila.lastOrNull()

    // Mientras está abierto las flechas del mando mueven el foco entre sus opciones, no el cursor.
    DisposableEffect(Unit) {
        Puntero.pausadoExtra = true
        onDispose { Puntero.pausadoExtra = false }
    }
    BackHandler(true) {
        if (pila.isNotEmpty()) pila = pila.dropLast(1) else Acciones.cerrar()
    }

    val primero = remember(vista) { FocusRequester() }
    LaunchedEffect(vista) {
        delay(60)
        runCatching { primero.requestFocus() }
    }

    fun elegir(item: AccionItem) {
        when {
            item.sub != null -> pila = pila + Vista.Sub(item)
            item.confirmar != null -> pila = pila + Vista.Confirmar(item)
            else -> {
                Acciones.cerrar()
                item.alElegir()
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) { detectTapGestures { Acciones.cerrar() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(480.dp)
                .cristal(RoundedCornerShape(EsquinaVentana), elevacion = 32.dp)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(horizontal = 18.dp, vertical = 20.dp),
        ) {
            val titulo = when (vista) {
                is Vista.Confirmar -> vista.item.confirmar ?: ""
                is Vista.Sub -> vista.padre.etiqueta
                null -> p.titulo
            }
            val detalle = when (vista) {
                is Vista.Confirmar -> p.titulo
                is Vista.Sub -> p.titulo
                null -> p.detalle
            }
            Text(
                titulo,
                style = AntenaType.panel,
                color = Fosforo,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
            if (!detalle.isNullOrBlank()) {
                Text(
                    detalle,
                    style = AntenaType.meta,
                    color = FosforoTenue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            when (vista) {
                is Vista.Confirmar -> Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                ) {
                    // El foco nace en «Cancelar»: un OK de más no borra nada.
                    Box(Modifier.focusRequester(primero)) {
                        RemoteChip(label = "Cancelar", small = true, onClick = { pila = pila.dropLast(1) })
                    }
                    RemoteChip(
                        label = vista.item.etiqueta,
                        icon = vista.item.icono,
                        small = true,
                        peligro = true,
                        onClick = {
                            Acciones.cerrar()
                            vista.item.alElegir()
                        },
                    )
                }
                else -> {
                    val lista = if (vista is Vista.Sub) vista.padre.sub.orEmpty() else p.items
                    Column(
                        Modifier.acotada(lista.size).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        lista.forEachIndexed { i, item ->
                            val fila: @Composable () -> Unit = {
                                MenuRow(
                                    label = item.etiqueta,
                                    icon = item.icono,
                                    peligro = item.peligro,
                                    value = if (item.sub != null) "›" else null,
                                    onClick = { elegir(item) },
                                )
                            }
                            if (i == 0) Box(Modifier.focusRequester(primero)) { fila() } else fila()
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Atrás: " + if (pila.isEmpty()) "cerrar" else "volver",
                style = AntenaType.meta,
                color = FosforoTenue,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }
    }
}

/** Sin peso real: la lista sólo se acota cuando es larga (carpetas) para que no se salga de la pantalla. */
private fun Modifier.acotada(n: Int): Modifier = if (n > 7) this.height(350.dp) else this

/** Opciones comunes de una dirección: abrir, copiar, compartir y (si procede) quitarla. */
fun accionesDeSitio(
    ctx: Context,
    url: String,
    onAbrir: () -> Unit,
    onEliminar: (() -> Unit)? = null,
    textoEliminar: String = "Eliminar",
    preguntaEliminar: String = "¿Eliminar?",
    antes: List<AccionItem> = emptyList(),
): List<AccionItem> = buildList {
    add(AccionItem("Abrir", ToolIcon.Link, alElegir = onAbrir))
    addAll(antes)
    add(AccionItem("Copiar dirección", ToolIcon.Copy, alElegir = { copyTextToClipboard(ctx, url) }))
    add(AccionItem("Compartir enlace…", ToolIcon.Share, alElegir = { compartirTexto(ctx, url) }))
    if (onEliminar != null) {
        add(AccionItem(textoEliminar, ToolIcon.Trash, peligro = true, confirmar = preguntaEliminar, alElegir = onEliminar))
    }
}

/** Envía un archivo a otra aplicación (LocalSend, Bluetooth, reproductor de vídeo…). */
fun compartirArchivo(ctx: Context, archivo: java.io.File): Boolean = runCatching {
    val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", archivo)
    val mime = android.webkit.MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(archivo.extension.lowercase()) ?: "*/*"
    val enviar = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(enviar, "Enviar a…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}.isSuccess

/** Opciones de una descarga: abrir, enviarla a otra app, copiar de dónde vino y borrarla. */
fun accionesDeDescarga(
    ctx: Context,
    d: com.antena.tv.db.DownloadEntry,
    onAbrir: (com.antena.tv.db.DownloadEntry) -> Unit,
    onEnviar: (com.antena.tv.db.DownloadEntry) -> Unit,
    onEliminar: (com.antena.tv.db.DownloadEntry) -> Unit,
): List<AccionItem> = buildList {
    add(AccionItem("Abrir", ToolIcon.Link, alElegir = { onAbrir(d) }))
    add(AccionItem("Enviar a otra app…", ToolIcon.Share, alElegir = { onEnviar(d) }))
    if (d.url.startsWith("http")) {
        add(AccionItem("Copiar dirección de origen", ToolIcon.Copy, alElegir = { copyTextToClipboard(ctx, d.url) }))
    }
    add(AccionItem("Eliminar descarga", ToolIcon.Trash, peligro = true, confirmar = "¿Eliminar esta descarga y su archivo?", alElegir = { onEliminar(d) }))
}
