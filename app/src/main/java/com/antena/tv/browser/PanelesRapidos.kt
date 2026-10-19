package com.antena.tv.browser

import android.app.DownloadManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antena.tv.cursor.zonaScroll
import com.antena.tv.db.DownloadEntry
import com.antena.tv.db.HistoryEntry
import com.antena.tv.ui.Alerta
import com.antena.tv.ui.Ambar
import com.antena.tv.ui.AntenaType
import com.antena.tv.ui.Azul
import com.antena.tv.ui.Casco
import com.antena.tv.ui.EsquinaTarjeta
import com.antena.tv.ui.EsquinaVentana
import com.antena.tv.ui.cristal
import com.antena.tv.ui.Escudo
import com.antena.tv.ui.Fosforo
import com.antena.tv.ui.FosforoTenue
import com.antena.tv.ui.Noche
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Paneles rápidos al estilo de Edge: una lista corta y buscable que sale bajo la
 * barra (o se fija como columna a la derecha) para Historial y Descargas, con
 * un enlace a la página completa.
 */
enum class PanelTipo { DESCARGAS, HISTORIAL, COMPLEMENTOS, FAVORITOS }

/* ───────────── Complementos ───────────── */

/**
 * Lista corta de extensiones bajo el botón de la barra: encender o apagar cada
 * una, elegir cuáles tienen su botón en la barra y abrir su ficha.
 */
@Composable
fun PanelComplementos(
    extensiones: List<com.antena.tv.gecko.ExtInfo>,
    iconos: Map<String, android.graphics.Bitmap>,
    ancladas: (com.antena.tv.gecko.ExtInfo) -> Boolean,
    fijado: Boolean,
    onFijar: () -> Unit,
    onCerrar: () -> Unit,
    onAlternar: (String) -> Unit,
    onAnclar: (com.antena.tv.gecko.ExtInfo) -> Unit,
    onAbrirExt: (com.antena.tv.gecko.ExtInfo) -> Unit,
    onPagina: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lista = extensiones.filter { it.id != com.antena.tv.gecko.EXT_INTERNA }
    PanelRapido(
        titulo = "Complementos",
        icono = ToolIcon.Puzzle,
        fijado = fijado,
        onFijar = onFijar,
        onCerrar = onCerrar,
        onTitulo = onPagina,
        menu = listOf("Administrar complementos" to onPagina),
        modifier = modifier,
    ) {
        Column(
            Modifier.weight(1f, fill = false).listaDesplazable(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (lista.isEmpty()) {
                Text("Aún no hay complementos.", style = AntenaType.body, color = FosforoTenue, modifier = Modifier.padding(8.dp))
            }
            lista.forEach { ext ->
                FilaAjuste(
                    titulo = ext.nombre,
                    descripcion = if (ext.activa) "Activo" else "Desactivado",
                    onClick = { onAbrirExt(ext) },
                    icono = { ExtIcono(ext, iconos[ext.id], lado = 30.dp) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.antena.tv.ui.Interruptor(ext.activa, { onAlternar(ext.id) })
                        Spacer(Modifier.width(4.dp))
                        RemoteChip(
                            label = "",
                            icon = if (ancladas(ext)) ToolIcon.PinFilled else ToolIcon.Pin,
                            accent = ancladas(ext),
                            small = true,
                            ghost = true,
                            onClick = { onAnclar(ext) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "La chincheta muestra el botón en la barra.",
            style = AntenaType.meta.copy(fontSize = 12.sp),
            color = FosforoTenue,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/* ───────────── Favoritos ───────────── */

@Composable
fun PanelFavoritos(
    favoritos: List<com.antena.tv.db.Favorite>,
    fijado: Boolean,
    onFijar: () -> Unit,
    onCerrar: () -> Unit,
    onAbrir: (String) -> Unit,
    onEliminar: (com.antena.tv.db.Favorite) -> Unit,
    onAnadir: () -> Unit,
    onPagina: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var busqueda by remember { mutableStateOf("") }
    val lista = favoritos.filter {
        busqueda.isBlank() || it.name.contains(busqueda, true) || it.url.contains(busqueda, true)
    }
    PanelRapido(
        titulo = "Favoritos",
        icono = ToolIcon.Star,
        fijado = fijado,
        onFijar = onFijar,
        onCerrar = onCerrar,
        onTitulo = onPagina,
        menu = listOf(
            "Añadir favorito" to onAnadir,
            "Abrir todos los favoritos" to onPagina,
        ),
        modifier = modifier,
    ) {
        CampoBusqueda(busqueda, { busqueda = it }, "Buscar en favoritos")
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.weight(1f, fill = false).listaDesplazable(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (lista.isEmpty()) {
                Text(
                    if (favoritos.isEmpty()) "Aún no hay favoritos." else "Sin resultados.",
                    style = AntenaType.body,
                    color = FosforoTenue,
                    modifier = Modifier.padding(8.dp),
                )
            }
            lista.forEach { f ->
                FilaSitio(
                    titulo = f.name,
                    url = f.url,
                    hora = null,
                    onAbrir = { onAbrir(f.url) },
                    onEliminar = { onEliminar(f) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RemoteChip(label = "Añadir", icon = ToolIcon.NewTab, small = true, onClick = onAnadir)
            RemoteChip(label = "Todos", small = true, onClick = onPagina)
        }
    }
}

/** Marco común: cabecera (título que abre la página completa, fijar, menú, cerrar) y cuerpo. */
@Composable
fun PanelRapido(
    titulo: String,
    icono: ToolIcon,
    fijado: Boolean,
    onFijar: () -> Unit,
    onCerrar: () -> Unit,
    onTitulo: () -> Unit,
    menu: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
    cuerpo: @Composable ColumnScope.() -> Unit,
) {
    var menuAbierto by remember { mutableStateOf(false) }
    val forma = if (fijado) RoundedCornerShape(0.dp) else RoundedCornerShape(EsquinaTarjeta)
    Column(
        (if (fijado) modifier.background(Casco).border(1.dp, Fosforo.copy(alpha = 0.08f)) else modifier.cristal(forma))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemoteChip(label = titulo, icon = icono, small = true, ghost = true, onClick = onTitulo)
            Spacer(Modifier.weight(1f))
            RemoteChip(
                label = "",
                icon = if (fijado) ToolIcon.PinFilled else ToolIcon.Pin,
                accent = fijado,
                small = true,
                ghost = true,
                onClick = onFijar,
            )
            Spacer(Modifier.width(2.dp))
            RemoteChip(
                label = "",
                icon = ToolIcon.More,
                small = true,
                ghost = true,
                openState = menuAbierto,
                onClick = { menuAbierto = !menuAbierto },
            )
            Spacer(Modifier.width(2.dp))
            RemoteChip(label = "", icon = ToolIcon.Close, small = true, ghost = true, onClick = onCerrar)
        }
        if (menuAbierto) {
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Noche)
                    .padding(6.dp),
            ) {
                menu.forEach { (texto, accion) ->
                    MenuRow(texto, onClick = { menuAbierto = false; accion() })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        cuerpo()
    }
}

/** Lista que se desplaza con el dedo, la rueda, el foco y el cursor del mando (en sus bordes). */
@Composable
fun Modifier.listaDesplazable(): Modifier {
    val estado = rememberScrollState()
    return this.zonaScroll(estado).verticalScroll(estado)
}

/** Campo de búsqueda de los paneles. */
@Composable
private fun CampoBusqueda(valor: String, alCambiar: (String) -> Unit, pista: String) {
    var enfocado by remember { mutableStateOf(false) }
    val forma = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(forma)
            .background(Noche)
            .border(if (enfocado) 2.dp else 1.dp, if (enfocado) Ambar else Fosforo.copy(alpha = 0.08f), forma)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolIconGlyph(ToolIcon.Search, if (enfocado) Ambar else FosforoTenue, size = 20.dp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (valor.isEmpty()) Text(pista, style = AntenaType.body, color = FosforoTenue)
            BasicTextField(
                value = valor,
                onValueChange = alCambiar,
                singleLine = true,
                textStyle = AntenaType.body.copy(color = Fosforo),
                cursorBrush = SolidColor(Ambar),
                modifier = Modifier.fillMaxWidth().onFocusChanged { enfocado = it.isFocused },
            )
        }
    }
}

/* ───────────── Descargas ───────────── */

/** Estado de una descarga según el DownloadManager del sistema. */
data class EstadoDescarga(val estado: Int, val bajado: Long, val total: Long)

/** Consulta, de una vez, el estado de varias descargas (fuera del hilo principal). */
fun consultarDescargas(context: Context, ids: List<Long>): Map<Long, EstadoDescarga> {
    if (ids.isEmpty()) return emptyMap()
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val salida = mutableMapOf<Long, EstadoDescarga>()
    runCatching {
        dm.query(DownloadManager.Query().setFilterById(*ids.toLongArray()))?.use { c ->
            val iId = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val iSt = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            val iBy = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val iTo = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            while (c.moveToNext()) {
                salida[c.getLong(iId)] = EstadoDescarga(c.getInt(iSt), c.getLong(iBy), c.getLong(iTo))
            }
        }
    }
    return salida
}

private fun tamano(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1048576.0)
    else -> String.format(Locale.getDefault(), "%.2f GB", bytes / 1073741824.0)
}

@Composable
fun PanelDescargas(
    entries: List<DownloadEntry>,
    fijado: Boolean,
    onFijar: () -> Unit,
    onCerrar: () -> Unit,
    onAbrirArchivo: (DownloadEntry) -> Unit,
    onEliminar: (DownloadEntry) -> Unit,
    onCarpeta: () -> Unit,
    onPagina: () -> Unit,
    onLimpiar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var busqueda by remember { mutableStateOf("") }
    var verTodas by remember { mutableStateOf(false) }
    // Progreso en vivo mientras haya algo descargando.
    val estados by produceState(emptyMap<Long, EstadoDescarga>(), entries) {
        while (true) {
            value = withContext(Dispatchers.IO) {
                consultarDescargas(context, entries.map { it.dmId }.filter { it >= 0 })
            }
            delay(700)
        }
    }
    val filtradas = entries.filter {
        busqueda.isBlank() || it.title.contains(busqueda, true) || it.url.contains(busqueda, true)
    }
    val visibles = if (verTodas || busqueda.isNotBlank()) filtradas else filtradas.take(4)

    PanelRapido(
        titulo = "Descargas",
        icono = ToolIcon.Download,
        fijado = fijado,
        onFijar = onFijar,
        onCerrar = onCerrar,
        onTitulo = onPagina,
        menu = listOf(
            "Abrir página de descargas" to onPagina,
            "Abrir carpeta de descargas" to onCarpeta,
            "Borrar lista de descargas" to onLimpiar,
        ),
        modifier = modifier,
    ) {
        CampoBusqueda(busqueda, { busqueda = it }, "Buscar descargas")
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.weight(1f, fill = false).listaDesplazable(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (visibles.isEmpty()) {
                Text(
                    if (entries.isEmpty()) "Aún no hay descargas." else "Sin coincidencias.",
                    style = AntenaType.body,
                    color = FosforoTenue,
                    modifier = Modifier.padding(8.dp),
                )
            }
            visibles.forEach { d ->
                FilaDescarga(d, estados[d.dmId], onAbrir = { onAbrirArchivo(d) }, onEliminar = { onEliminar(d) })
            }
        }
        // «Todas las descargas» y «Abrir carpeta» están en el menú ⋯.
        if (!verTodas && busqueda.isBlank() && filtradas.size > 4) {
            Spacer(Modifier.height(8.dp))
            RemoteChip(label = "Ver más (${filtradas.size - 4})", small = true, onClick = { verTodas = true })
        }
    }
}

@Composable
private fun FilaDescarga(
    d: DownloadEntry,
    st: EstadoDescarga?,
    onAbrir: () -> Unit,
    onEliminar: () -> Unit,
) {
    var enfocado by remember { mutableStateOf(false) }
    val fuente = remember { MutableInteractionSource() }
    val hover by fuente.collectIsHoveredAsState()
    val centro = rememberCenterHandler(onAbrir, onEliminar)
    val forma = RoundedCornerShape(12.dp)
    val fecha = remember { SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault()) }
    val enCurso = st != null && st.estado != DownloadManager.STATUS_SUCCESSFUL &&
        st.estado != DownloadManager.STATUS_FAILED
    val fallida = st?.estado == DownloadManager.STATUS_FAILED
    // Archivo propio (PDF de una página): no pasa por el DownloadManager.
    val propio = d.dmId < 0
    val tamanoPropio = if (propio) java.io.File(d.url.removePrefix("file://")).length() else 0L
    val faltante = st == null && !propio // el sistema ya no la conoce (archivo borrado)
    val fraccion = if (st != null && st.total > 0) (st.bajado.toFloat() / st.total).coerceIn(0f, 1f) else 0f
    val detalle = when {
        fallida -> "Descarga fallida"
        faltante -> "Archivo no disponible · ${fecha.format(Date(d.addedAt))}"
        enCurso && st != null && st.total > 0 ->
            "${(fraccion * 100).toInt()} % · ${tamano(st.bajado)} de ${tamano(st.total)}"
        enCurso -> "Descargando… ${tamano(st?.bajado ?: 0)}"
        propio -> "${tamano(tamanoPropio)} · PDF de la página · ${fecha.format(Date(d.addedAt))}"
        else -> "${tamano(st?.total ?: 0)} · ${hostOf(d.url) ?: d.url} · ${fecha.format(Date(d.addedAt))}"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .hoverable(fuente)
            .onFocusChanged { enfocado = it.isFocused }
            .focusable()
            .clip(forma)
            .background(
                when {
                    enfocado -> Ambar.copy(alpha = 0.14f)
                    hover -> Ambar.copy(alpha = 0.08f)
                    else -> Fosforo.copy(alpha = 0.05f)
                },
            )
            .border(if (enfocado) 2.dp else 0.dp, if (enfocado) Ambar else Color.Transparent, forma)
            .onKeyEvent(centro)
            .pointerInput(d.id) { detectTapGestures(onTap = { onAbrir() }, onLongPress = { onEliminar() }) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            fallida -> Alerta.copy(alpha = 0.25f)
                            enCurso -> Ambar.copy(alpha = 0.22f)
                            else -> Escudo.copy(alpha = 0.25f)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                ToolIconGlyph(
                    if (fallida) ToolIcon.Close else ToolIcon.Download,
                    when {
                        fallida -> Alerta
                        enCurso -> Ambar
                        else -> Escudo
                    },
                    size = 22.dp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    d.title,
                    style = AntenaType.body,
                    color = if (enfocado) Ambar else Fosforo,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    detalle,
                    style = AntenaType.meta.copy(fontSize = 13.sp),
                    color = if (fallida) Alerta else FosforoTenue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RemoteChip(label = "", icon = ToolIcon.Trash, small = true, ghost = true, onClick = onEliminar)
        }
        if (enCurso) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Fosforo.copy(alpha = 0.12f))) {
                Box(Modifier.fillMaxWidth(if ((st?.total ?: 0L) > 0L) fraccion else 0.06f).fillMaxHeight().background(Ambar))
            }
        }
    }
}

/* ───────────── Historial ───────────── */

private enum class RangoHistorial(val etiqueta: String) { TODO("Todo"), HOY("Hoy"), AYER("Ayer"), SEMANA("7 días") }

@Composable
fun PanelHistorial(
    entries: List<HistoryEntry>,
    cerradas: List<TabCerrada>,
    fijado: Boolean,
    onFijar: () -> Unit,
    onCerrar: () -> Unit,
    onAbrir: (String) -> Unit,
    onRestaurar: (TabCerrada) -> Unit,
    onEliminar: (HistoryEntry) -> Unit,
    onPagina: () -> Unit,
    onBorrarDatos: () -> Unit,
    onLimpiar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var busqueda by remember { mutableStateOf("") }
    var pestana by remember { mutableStateOf(0) }       // 0 = Todos, 1 = Cerradas recientemente
    var rango by remember { mutableStateOf(RangoHistorial.TODO) }
    val ahora = System.currentTimeMillis()
    val hoy = remember(ahora / 60_000) {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    fun cabe(t: Long) = when (rango) {
        RangoHistorial.TODO -> true
        RangoHistorial.HOY -> t >= hoy
        RangoHistorial.AYER -> t in (hoy - 86_400_000L) until hoy
        RangoHistorial.SEMANA -> t >= hoy - 6 * 86_400_000L
    }
    fun coincide(titulo: String, url: String) =
        busqueda.isBlank() || titulo.contains(busqueda, true) || url.contains(busqueda, true)

    val hist = entries.filter { cabe(it.visitedAt) && coincide(it.title, it.url) }.take(150)
    val cerr = cerradas.filter { coincide(it.title, it.url) }

    PanelRapido(
        titulo = "Historial",
        icono = ToolIcon.Clock,
        fijado = fijado,
        onFijar = onFijar,
        onCerrar = onCerrar,
        onTitulo = onPagina,
        menu = listOf(
            "Abrir historial completo" to onPagina,
            "Borrar datos de navegación…" to onBorrarDatos,
            "Limpiar todo el historial" to onLimpiar,
        ),
        modifier = modifier,
    ) {
        CampoBusqueda(busqueda, { busqueda = it }, "Buscar en el historial")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RemoteChip(label = "Todos", small = true, accent = pestana == 0, onClick = { pestana = 0 })
            RemoteChip(
                label = "Cerradas recientemente" + if (cerradas.isNotEmpty()) " (${cerradas.size})" else "",
                small = true,
                accent = pestana == 1,
                onClick = { pestana = 1 },
            )
        }
        if (pestana == 0) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RangoHistorial.entries.forEach { r ->
                    RemoteChip(label = r.etiqueta, small = true, ghost = true, accent = r == rango, onClick = { rango = r })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.weight(1f, fill = false).listaDesplazable(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (pestana == 0) {
                if (hist.isEmpty()) {
                    Text("Sin resultados.", style = AntenaType.body, color = FosforoTenue, modifier = Modifier.padding(8.dp))
                }
                porDia(hist) { it.visitedAt }.forEach { (dia, lista) ->
                    EncabezadoDia(dia)
                    lista.forEach { e ->
                        FilaSitio(
                            titulo = e.title.ifBlank { e.url },
                            url = e.url,
                            hora = e.visitedAt,
                            onAbrir = { onAbrir(e.url) },
                            onEliminar = { onEliminar(e) },
                        )
                    }
                }
            } else {
                if (cerr.isEmpty()) {
                    Text(
                        "No has cerrado pestañas en esta sesión.",
                        style = AntenaType.body,
                        color = FosforoTenue,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                porDia(cerr) { it.cerradaAt }.forEach { (dia, lista) ->
                    EncabezadoDia(dia)
                    lista.forEach { t ->
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
    }
}

@Composable
private fun EncabezadoDia(texto: String) {
    Text(
        texto.uppercase(),
        style = AntenaType.meta.copy(fontSize = 12.sp),
        color = FosforoTenue,
        modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun FilaSitio(
    titulo: String,
    url: String,
    hora: Long?,
    onAbrir: () -> Unit,
    onEliminar: (() -> Unit)?,
) {
    var enfocado by remember { mutableStateOf(false) }
    val fuente = remember { MutableInteractionSource() }
    val hover by fuente.collectIsHoveredAsState()
    val centro = rememberCenterHandler(onAbrir, onEliminar)
    val forma = RoundedCornerShape(10.dp)
    val horaFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Row(
        Modifier
            .fillMaxWidth()
            .hoverable(fuente)
            .onFocusChanged { enfocado = it.isFocused }
            .focusable()
            .clip(forma)
            .background(
                when {
                    enfocado -> Ambar.copy(alpha = 0.14f)
                    hover -> Ambar.copy(alpha = 0.08f)
                    else -> Color.Transparent
                },
            )
            .border(if (enfocado) 2.dp else 0.dp, if (enfocado) Ambar else Color.Transparent, forma)
            .onKeyEvent(centro)
            .pointerInput(url, hora ?: 0L) {
                detectTapGestures(onTap = { onAbrir() }, onLongPress = { onEliminar?.invoke() })
            }
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FaviconBox(
            url = url,
            char = hostOf(url)?.firstOrNull()?.uppercase() ?: "?",
            size = 30.dp,
            background = Fosforo.copy(alpha = 0.9f),
            textColor = Noche,
            iconPadding = 4.dp,
            textStyle = AntenaType.meta,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                titulo,
                style = AntenaType.body.copy(fontSize = 16.sp, lineHeight = 22.sp),
                color = if (enfocado) Ambar else Fosforo,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (hora != null) "${hostOf(url) ?: url} · ${horaFmt.format(Date(hora))}" else (hostOf(url) ?: url),
                style = AntenaType.meta.copy(fontSize = 12.sp),
                color = FosforoTenue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onEliminar != null) {
            RemoteChip(label = "", icon = ToolIcon.Close, small = true, ghost = true, onClick = onEliminar)
        }
    }
}

/* ───────────── Borrar datos de navegación ───────────── */

/** Qué se borra y desde cuándo (`desde` = milisegundos hacia atrás; 0 = todo). */
data class SeleccionBorrado(
    val ventanaMs: Long,
    val historial: Boolean,
    val descargas: Boolean,
    val cookies: Boolean,
    val cache: Boolean,
)

@Composable
fun BorrarDatosDialogo(onBorrar: (SeleccionBorrado) -> Unit, onCancelar: () -> Unit) {
    val intervalos = listOf(
        "Última hora" to 3_600_000L,
        "24 horas" to 86_400_000L,
        "7 días" to 7 * 86_400_000L,
        "4 semanas" to 28 * 86_400_000L,
        "Siempre" to 0L,
    )
    var intervalo by remember { mutableStateOf(1) }
    var historial by remember { mutableStateOf(true) }
    var descargas by remember { mutableStateOf(true) }
    var cookies by remember { mutableStateOf(true) }
    var cache by remember { mutableStateOf(true) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .pointerInput(Unit) { detectTapGestures { onCancelar() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(540.dp)
                .cristal(RoundedCornerShape(EsquinaVentana), elevacion = 32.dp)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(26.dp),
        ) {
            Text("Borrar datos de navegación", style = AntenaType.panel, color = Fosforo)
            Spacer(Modifier.height(14.dp))
            Text("Intervalo de tiempo", style = AntenaType.meta, color = FosforoTenue)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                intervalos.forEachIndexed { i, (txt, _) ->
                    RemoteChip(label = txt, small = true, accent = i == intervalo, onClick = { intervalo = i })
                }
            }
            Spacer(Modifier.height(14.dp))
            Casilla("Historial de navegación", historial) { historial = !historial }
            Casilla("Historial de descargas (los archivos se conservan)", descargas) { descargas = !descargas }
            Casilla("Cookies y otros datos de sitios (siempre completo)", cookies) { cookies = !cookies }
            Casilla("Imágenes y archivos en caché (siempre completo)", cache) { cache = !cache }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.align(Alignment.End)) {
                RemoteChip(label = "Cancelar", small = true, onClick = onCancelar)
                RemoteChip(
                    label = "Borrar ahora",
                    icon = ToolIcon.Trash,
                    small = true,
                    peligro = true,
                    onClick = {
                        onBorrar(SeleccionBorrado(intervalos[intervalo].second, historial, descargas, cookies, cache))
                    },
                )
            }
        }
    }
}

@Composable
private fun Casilla(texto: String, marcada: Boolean, alCambiar: () -> Unit) {
    var enfocado by remember { mutableStateOf(false) }
    val fuente = remember { MutableInteractionSource() }
    val hover by fuente.collectIsHoveredAsState()
    val centro = rememberCenterHandler(alCambiar)
    val forma = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .hoverable(fuente)
            .onFocusChanged { enfocado = it.isFocused }
            .focusable()
            .clip(forma)
            .background(if (enfocado) Ambar.copy(alpha = 0.14f) else if (hover) Ambar.copy(alpha = 0.08f) else Color.Transparent)
            .onKeyEvent(centro)
            .pointerInput(alCambiar) { detectTapGestures { alCambiar() } }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (marcada) Ambar else Color.Transparent)
                .border(2.dp, if (marcada) Ambar else Azul.copy(alpha = 0.7f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (marcada) Text("✓", style = AntenaType.meta.copy(fontSize = 15.sp), color = Noche)
        }
        Spacer(Modifier.width(14.dp))
        Text(texto, style = AntenaType.body, color = if (enfocado) Ambar else Fosforo)
    }
}
