package com.antena.tv.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antena.tv.archivos.Almacenamiento
import com.antena.tv.archivos.Compartir
import com.antena.tv.archivos.Tipo
import com.antena.tv.archivos.Volumen
import com.antena.tv.ui.Alerta
import com.antena.tv.ui.Ambar
import com.antena.tv.ui.AntenaType
import com.antena.tv.ui.Azul
import com.antena.tv.ui.Casco
import com.antena.tv.ui.Escudo
import com.antena.tv.ui.Fosforo
import com.antena.tv.ui.FosforoTenue
import com.antena.tv.ui.cristal
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Administrador de archivos: recorre las descargas y el almacenamiento (también USB), instala APK, reproduce
 * video y audio, abre el resto con otra app y comparte una carpeta por la red local.
 */
@Composable
fun ArchivosPage(
    onClose: () -> Unit,
    onReproducir: (File) -> Unit,
    carpetaInicial: File? = null,
    backLabel: String = "✕ Cerrar",
) {
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    val volumenes = remember { Almacenamiento.volumenes(ctx) }
    var actual by remember { mutableStateOf(carpetaInicial) }
    var raizActual by remember { mutableStateOf(volumenes.firstOrNull { carpetaInicial != null && carpetaInicial.path.startsWith(it.ruta.path) }?.ruta) }
    var entradas by remember { mutableStateOf<List<File>?>(emptyList()) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var porBorrar by remember { mutableStateOf<File?>(null) }
    var accesoTotal by remember { mutableStateOf(Almacenamiento.tieneAccesoTotal()) }
    var version by remember { mutableStateOf(0) }

    LaunchedEffect(actual, version) {
        val dir = actual ?: return@LaunchedEffect
        entradas = withContext(Dispatchers.IO) { Almacenamiento.listar(dir) }
    }

    fun abrir(f: File) {
        when (Almacenamiento.tipoDe(f)) {
            Tipo.CARPETA -> { actual = f; mensaje = null }
            Tipo.VIDEO, Tipo.AUDIO -> onReproducir(f)
            Tipo.APK -> mensaje = Almacenamiento.instalarApk(ctx, f)
            else -> mensaje = Almacenamiento.abrirConApp(ctx, f)
        }
    }

    fun arriba() {
        val a = actual ?: return
        val padre = a.parentFile
        actual = if (raizActual != null && a.path == raizActual!!.path) null else padre
        if (actual == null) raizActual = null
    }

    Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 30.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Archivos", style = AntenaType.section, color = Fosforo, modifier = Modifier.weight(1f))
            if (actual != null) {
                RemoteChip(label = "← Subir", small = true, onClick = { arriba() })
                Spacer(Modifier.width(10.dp))
                if (Compartir.servidor == null) {
                    RemoteChip(
                        label = "Compartir esta carpeta",
                        small = true,
                        accent = true,
                        onClick = { actual?.let { Compartir.iniciar(it) } },
                    )
                    Spacer(Modifier.width(10.dp))
                }
            }
            RemoteChip(label = backLabel, small = true, onClick = onClose)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            actual?.path ?: "Elige una unidad · OK: abrir · mantener OK: eliminar",
            style = AntenaType.monoSmall,
            color = FosforoTenue,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        mensaje?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = AntenaType.meta, color = Ambar)
        }
        Spacer(Modifier.height(10.dp))

        // ── Compartir por la red ──
        val servidor = Compartir.servidor
        if (servidor != null) {
            TarjetaCompartir()
            Spacer(Modifier.height(12.dp))
        }
        Compartir.error?.let {
            Text(it, style = AntenaType.meta, color = Alerta)
            Spacer(Modifier.height(8.dp))
        }

        // ── Eliminar: confirmación ──
        porBorrar?.let { f ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .cristal(RoundedCornerShape(14.dp), elevacion = 8.dp, opacidad = 0.9f)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("¿Eliminar «${f.name}»?", style = AntenaType.body, color = Fosforo, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                RemoteChip(label = "Cancelar", small = true, onClick = { porBorrar = null })
                Spacer(Modifier.width(10.dp))
                RemoteChip(
                    label = "Eliminar",
                    small = true,
                    peligro = true,
                    onClick = {
                        val ok = Almacenamiento.eliminar(f)
                        mensaje = if (ok) "Eliminado." else "No se pudo eliminar."
                        porBorrar = null
                        version++
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        if (actual == null) {
            // Lista de unidades.
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(volumenes, key = { it.ruta.path }) { v ->
                    FilaVolumen(
                        v,
                        onClick = {
                            if (v.requierePermiso && !accesoTotal) {
                                mensaje = "Para ver este almacenamiento hace falta el permiso «Acceso a todos los archivos»."
                            } else {
                                actual = v.ruta
                                raizActual = v.ruta
                                mensaje = null
                            }
                        },
                    )
                }
                if (!accesoTotal) {
                    item(key = "permiso") {
                        Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                            Text(
                                "Android pide un permiso especial para ver todo el almacenamiento (no sólo las descargas de la app). " +
                                    "Pulsa «Dar permiso», activa el interruptor de Coco Surf y vuelve.",
                                style = AntenaType.meta,
                                color = FosforoTenue,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                RemoteChip(label = "Dar permiso", small = true, accent = true, onClick = { Almacenamiento.pedirAccesoTotal(ctx) })
                                RemoteChip(label = "Ya lo di: actualizar", small = true, onClick = { accesoTotal = Almacenamiento.tieneAccesoTotal() })
                            }
                        }
                    }
                }
            }
        } else {
            val lista = entradas
            when {
                lista == null -> Text(
                    "No se puede leer esta carpeta (sin permiso).",
                    style = AntenaType.body,
                    color = Alerta,
                )
                lista.isEmpty() -> Text("La carpeta está vacía.", style = AntenaType.body, color = FosforoTenue)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(lista, key = { it.path }) { f ->
                        FilaArchivo(f, onClick = { abrir(f) }, onLong = { porBorrar = f })
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaCompartir() {
    val s = Compartir.servidor ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .cristal(RoundedCornerShape(14.dp), elevacion = 8.dp, opacidad = 0.92f)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Compartiendo «${Compartir.carpeta?.name ?: ""}» en tu red", style = AntenaType.body, color = Escudo)
                Spacer(Modifier.height(6.dp))
                Text(
                    "En el navegador de un PC de la misma red abre:",
                    style = AntenaType.meta,
                    color = FosforoTenue,
                )
                Text(Compartir.direccion ?: "", style = AntenaType.panel, color = Fosforo)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PIN", style = AntenaType.meta, color = FosforoTenue)
                Text(s.pin, style = AntenaType.display, color = Ambar)
            }
            Spacer(Modifier.width(18.dp))
            RemoteChip(label = "Dejar de compartir", small = true, peligro = true, onClick = { Compartir.detener() })
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Cualquiera en tu red que tenga el PIN puede ver, bajar, subir y borrar lo de esta carpeta. Se apaga al cerrar la app.",
            style = AntenaType.meta,
            color = FosforoTenue,
        )
    }
}

@Composable
private fun FilaVolumen(v: Volumen, onClick: () -> Unit) {
    FilaBase(
        icono = "▣",
        titulo = v.nombre,
        detalle = v.ruta.path,
        color = Azul,
        onClick = onClick,
        onLong = null,
    )
}

@Composable
private fun FilaArchivo(f: File, onClick: () -> Unit, onLong: () -> Unit) {
    val tipo = Almacenamiento.tipoDe(f)
    val fmt = remember { SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()) }
    val (icono, color) = when (tipo) {
        Tipo.CARPETA -> "▸" to Azul
        Tipo.VIDEO -> "▶" to Escudo
        Tipo.AUDIO -> "♪" to Escudo
        Tipo.IMAGEN -> "▨" to Ambar
        Tipo.APK -> "⬇" to Ambar
        Tipo.OTRO -> "·" to FosforoTenue
    }
    val detalle = if (tipo == Tipo.CARPETA) fmt.format(Date(f.lastModified()))
    else "${Almacenamiento.tamano(f.length())} · ${fmt.format(Date(f.lastModified()))}"
    FilaBase(icono, f.name, detalle, color, onClick, onLong)
}

@Composable
private fun FilaBase(
    icono: String,
    titulo: String,
    detalle: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    onLong: (() -> Unit)?,
) {
    var enfocado by remember { mutableStateOf(false) }
    val centro = rememberCenterHandler(onClick, onLong)
    val forma = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { enfocado = it.isFocused }
            .focusable()
            .clip(forma)
            .background(if (enfocado) Ambar.copy(alpha = 0.14f) else Casco)
            .border(if (enfocado) 2.dp else 1.dp, if (enfocado) Ambar else Azul.copy(alpha = 0.3f), forma)
            .onKeyEvent(centro)
            .pointerInput(titulo) { detectTapGestures(onTap = { onClick() }, onLongPress = { onLong?.invoke() }) }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(if (enfocado) Ambar else color.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(icono, style = AntenaType.panel, color = if (enfocado) com.antena.tv.ui.Noche else color)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, style = AntenaType.body, color = if (enfocado) Ambar else Fosforo, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detalle, style = AntenaType.monoSmall, color = FosforoTenue, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
