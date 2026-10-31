package com.antena.tv.browser

import android.net.Uri
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.antena.tv.archivos.Almacenamiento
import com.antena.tv.archivos.Tipo
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.media3.common.Tracks
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableIntStateOf
import com.antena.tv.cursor.Puntero
import com.antena.tv.log.Bitacora
import com.antena.tv.log.LogKind
import com.antena.tv.ui.Ambar
import com.antena.tv.ui.AntenaType
import com.antena.tv.ui.Fosforo
import com.antena.tv.ui.FosforoTenue
import java.io.File
import kotlinx.coroutines.delay

/**
 * Reproductor de video y audio del propio navegador (ExoPlayer). Sirve para ver lo descargado sin salir de
 * la app: OK pausa o reanuda, ← → saltan 10 s (mantenidas, más), ↑ ↓ muestran los controles, Atrás cierra.
 */
@Composable
fun ReproductorLocal(archivo: File, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var actual by remember { mutableStateOf(archivo) }
    val esAudio = Almacenamiento.tipoDe(actual) == Tipo.AUDIO
    val player = remember { ExoPlayer.Builder(ctx).build() }
    // Lista de reproducción: los videos y audios de la misma carpeta (p. ej. las descargas).
    val lista = remember { hermanos(archivo) }
    var panelLista by remember { mutableStateOf(false) }
    var seleccion by remember { mutableIntStateOf(0) }
    val estadoLista = rememberLazyListState()
    var aviso by remember { mutableStateOf<String?>(null) }
    var reproduciendo by remember { mutableStateOf(true) }
    var posicion by remember { mutableLongStateOf(0L) }
    var duracion by remember { mutableLongStateOf(0L) }
    var controles by remember { mutableStateOf(true) }
    var ultimaAccion by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var error by remember { mutableStateOf<String?>(null) }
    var detalle by remember { mutableStateOf("") }
    var pistas by remember { mutableStateOf("") }
    val foco = remember { FocusRequester() }

    /** Guarda dónde va el archivo actual para poder seguir luego (aunque se cierre la pestaña o falte memoria). */
    fun guardarPosicion() {
        if (error == null && player.playbackState != Player.STATE_IDLE) {
            Posiciones.guardar(ctx, actual, player.currentPosition, player.duration)
        }
    }

    /** Pone un archivo en el reproductor: revisa que sea un video de verdad y retoma donde se quedó. */
    fun cargar(f: File) {
        if (f != actual) guardarPosicion()
        actual = f
        error = null
        detalle = ""
        pistas = ""
        aviso = null
        val malo = revisarArchivo(f)
        if (malo != null) {
            player.stop()
            error = malo.first
            detalle = malo.second
            Bitacora.add(LogKind.ERROR, "Reproductor: ${f.name} · ${malo.first}")
            return
        }
        val previa = Posiciones.leer(ctx, f)
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(f)))
        player.prepare()
        if (previa != null) {
            player.seekTo(previa)
            aviso = "Sigues donde lo dejaste (${formatoTiempo(previa)})"
        }
        player.playWhenReady = true
    }

    fun siguiente(paso: Int) {
        val i = lista.indexOf(actual)
        val j = i + paso
        if (i >= 0 && j in lista.indices) cargar(lista[j])
    }

    DisposableEffect(player) {
        // Con el reproductor abierto las flechas y OK son suyos, no del cursor.
        Puntero.pausadoExtra = true
        val oyente = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                reproduciendo = isPlaying
            }

            override fun onPlaybackStateChanged(estado: Int) {
                if (estado == Player.STATE_ENDED) {
                    Posiciones.olvidar(ctx, actual)
                    siguiente(1)
                }
            }

            override fun onTracksChanged(t: Tracks) {
                pistas = descripcionPistas(t)
            }

            override fun onPlayerError(e: PlaybackException) {
                error = "No se pudo reproducir este archivo."
                val causa = generateSequence<Throwable>(e) { it.cause }.drop(1).joinToString(" ← ") { "${it.javaClass.simpleName}: ${it.message}" }
                detalle = buildString {
                    appendLine("Código: ${e.errorCodeName} (${e.errorCode})")
                    if (causa.isNotBlank()) appendLine("Causa: $causa")
                    append(infoArchivo(actual))
                    if (pistas.isNotBlank()) append("\nPistas: $pistas")
                    append("\n" + pista(e.errorCode))
                }
                Bitacora.add(LogKind.ERROR, "Reproductor: ${actual.name} · ${detalle.replace("\n", " | ")}")
            }
        }
        player.addListener(oyente)
        onDispose {
            guardarPosicion()
            player.removeListener(oyente)
            player.release()
            Puntero.pausadoExtra = false
        }
    }

    LaunchedEffect(player) {
        cargar(archivo)
        var vueltas = 0
        while (true) {
            posicion = player.currentPosition
            duracion = player.duration.coerceAtLeast(0L)
            if (controles && player.isPlaying && System.currentTimeMillis() - ultimaAccion > 4_000) controles = false
            if (++vueltas % 12 == 0) {
                guardarPosicion()
                if (vueltas % 24 == 0) aviso = null
            }
            delay(400)
        }
    }
    LaunchedEffect(panelLista, seleccion) {
        if (panelLista) runCatching { estadoLista.animateScrollToItem((seleccion - 2).coerceAtLeast(0)) }
    }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { foco.requestFocus() }
    }
    BackHandler { if (panelLista) panelLista = false else onClose() }

    fun mostrar() {
        controles = true
        ultimaAccion = System.currentTimeMillis()
    }

    fun saltar(ms: Long) {
        player.seekTo((player.currentPosition + ms).coerceIn(0L, player.duration.coerceAtLeast(0L)))
        mostrar()
    }

    fun alternar() {
        if (player.isPlaying) player.pause() else player.play()
        mostrar()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(foco)
            .focusable()
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) {
                    ev.key in setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionLeft, Key.DirectionRight)
                } else {
                    val repetido = ev.nativeKeyEvent.repeatCount
                    if (panelLista) {
                        when (ev.key) {
                            Key.DirectionUp -> { seleccion = (seleccion - 1).coerceAtLeast(0); true }
                            Key.DirectionDown -> { seleccion = (seleccion + 1).coerceAtMost((lista.size - 1).coerceAtLeast(0)); true }
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                if (repetido == 0) lista.getOrNull(seleccion)?.let { cargar(it); panelLista = false }
                                true
                            }
                            Key.DirectionLeft, Key.DirectionRight -> true
                            else -> false
                        }
                    } else when (ev.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> {
                            if (repetido == 0) {
                                if (error != null) Almacenamiento.abrirConApp(ctx, actual)?.let { error = it } else alternar()
                            }
                            true
                        }
                        Key.MediaPlay -> { player.play(); mostrar(); true }
                        Key.MediaPause -> { player.pause(); mostrar(); true }
                        Key.DirectionLeft, Key.MediaRewind -> { saltar(if (repetido > 3) -30_000 else -10_000); true }
                        Key.DirectionRight, Key.MediaFastForward -> { saltar(if (repetido > 3) 30_000 else 10_000); true }
                        Key.MediaNext -> { siguiente(1); true }
                        Key.MediaPrevious -> { siguiente(-1); true }
                        Key.DirectionUp -> { mostrar(); true }
                        Key.DirectionDown -> {
                            if (lista.size > 1) {
                                seleccion = lista.indexOf(actual).coerceAtLeast(0)
                                panelLista = true
                            } else mostrar()
                            true
                        }
                        else -> false
                    }
                }
            }
            .pointerInput(Unit) { detectTapGestures { if (controles) alternar() else mostrar() } },
    ) {
        if (!esAudio) {
            AndroidView(
                factory = { c -> SurfaceView(c).also { player.setVideoSurfaceView(it) } },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("♪", style = AntenaType.display, color = Ambar)
                Spacer(Modifier.height(8.dp))
                Text(actual.nameWithoutExtension, style = AntenaType.panel, color = Fosforo)
            }
        }

        error?.let {
            Column(
                Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(it, style = AntenaType.panel, color = Fosforo)
                Spacer(Modifier.height(10.dp))
                // Lo que hace falta para diagnosticar: código del error, causa, archivo, cabecera y pistas.
                Text(detalle, style = AntenaType.monoSmall, color = FosforoTenue)
                Spacer(Modifier.height(14.dp))
                Text("OK: abrir con otra app del televisor · Atrás: cerrar", style = AntenaType.meta, color = Ambar)
            }
        }

        aviso?.let {
            Text(
                it,
                style = AntenaType.meta,
                color = Ambar,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        if (panelLista) {
            Column(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(400.dp)
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .padding(vertical = 20.dp),
            ) {
                Text(
                    "Lista · ${actual.parentFile?.name ?: ""}",
                    style = AntenaType.panel, color = Fosforo, maxLines = 1,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Text(
                    "↑ ↓ elegir · OK reproducir · Atrás cerrar",
                    style = AntenaType.meta, color = FosforoTenue,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
                LazyColumn(state = estadoLista, modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(lista) { i, f ->
                        val enFoco = i == seleccion
                        val suena = f == actual
                        val pos = Posiciones.leer(ctx, f)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (enFoco) Ambar.copy(alpha = 0.22f) else Color.Transparent)
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (suena) "▶" else "  ", style = AntenaType.body, color = Ambar)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.name, style = AntenaType.body, color = if (enFoco) Fosforo else FosforoTenue, maxLines = 1)
                                if (pos != null) Text("en ${formatoTiempo(pos)}", style = AntenaType.meta, color = FosforoTenue)
                            }
                        }
                    }
                }
            }
        }

        if (controles || !reproduciendo) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                    .padding(horizontal = 56.dp, vertical = 30.dp),
            ) {
                Text(actual.name, style = AntenaType.body, color = Fosforo, maxLines = 1)
                Spacer(Modifier.height(10.dp))
                // Barra de avance fina.
                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.22f))) {
                    val frac = if (duracion > 0) (posicion.toFloat() / duracion).coerceIn(0f, 1f) else 0f
                    Box(Modifier.fillMaxWidth(frac).height(5.dp).background(Ambar))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatoTiempo(posicion), style = AntenaType.monoSmall, color = FosforoTenue)
                    Text(
                        (if (reproduciendo) "OK: pausa · ← → saltar 10 s" else "En pausa · OK: seguir") +
                            (if (lista.size > 1) " · ↓ lista (${lista.indexOf(actual) + 1}/${lista.size})" else "") + " · Atrás: cerrar",
                        style = AntenaType.meta,
                        color = FosforoTenue,
                    )
                    Text(formatoTiempo(duracion), style = AntenaType.monoSmall, color = FosforoTenue)
                }
            }
        }
    }
}

/** Los videos y audios de la misma carpeta, por nombre. */
private fun hermanos(f: File): List<File> {
    val dir = f.parentFile ?: return listOf(f)
    val l = dir.listFiles()?.filter { it.isFile && Almacenamiento.tipoDe(it).let { t -> t == Tipo.VIDEO || t == Tipo.AUDIO } }
        ?.sortedBy { it.name.lowercase() }.orEmpty()
    return if (f in l) l else listOf(f) + l
}

/**
 * Historial pequeño de por dónde iba cada archivo (ruta → posición, duración, fecha). Se guarda al pausar,
 * cada 5 s y al cerrar la pestaña; solo se conservan los 60 más recientes.
 */
private object Posiciones {
    private fun prefs(c: android.content.Context) = c.getSharedPreferences("reproductor_posiciones", android.content.Context.MODE_PRIVATE)

    fun leer(c: android.content.Context, f: File): Long? =
        prefs(c).getString(f.path, null)?.split('|')?.getOrNull(0)?.toLongOrNull()

    fun olvidar(c: android.content.Context, f: File) = prefs(c).edit().remove(f.path).apply()

    fun guardar(c: android.content.Context, f: File, pos: Long, dur: Long) {
        // Al principio o casi al final no tiene sentido «retomar».
        if (pos < 5_000 || (dur > 0 && pos > dur - 10_000)) {
            olvidar(c, f)
            return
        }
        val p = prefs(c)
        val e = p.edit().putString(f.path, "$pos|$dur|${System.currentTimeMillis()}")
        val todas = p.all
        if (todas.size > 60) {
            todas.entries.sortedBy { (it.value as? String)?.split('|')?.getOrNull(2)?.toLongOrNull() ?: 0L }
                .take(todas.size - 60).forEach { e.remove(it.key) }
        }
        e.apply()
    }
}

/** Devuelve (mensaje, detalle) si el archivo no puede ser un video, o null si vale la pena intentarlo. */
private fun revisarArchivo(f: File): Pair<String, String>? {
    if (!f.exists()) return "El archivo ya no existe." to infoArchivo(f)
    if (f.length() == 0L) return "El archivo está vacío (descarga fallida)." to infoArchivo(f)
    val cab = leerCabecera(f)
    val texto = String(cab, Charsets.ISO_8859_1).trimStart().lowercase()
    if (texto.startsWith("<") || texto.startsWith("{")) {
        return "Esto no es un video: es una página web o un texto guardado con extensión de video." to
            (infoArchivo(f) + "\nSuele pasar cuando el sitio (MediaFire, etc.) pide un paso más antes de dar el archivo. Descárgalo de nuevo.")
    }
    return null
}

private fun leerCabecera(f: File, n: Int = 16): ByteArray = runCatching {
    f.inputStream().use { val b = ByteArray(n); val k = it.read(b); if (k <= 0) ByteArray(0) else b.copyOf(k) }
}.getOrDefault(ByteArray(0))

private fun infoArchivo(f: File): String {
    val cab = leerCabecera(f)
    val hex = cab.joinToString(" ") { "%02x".format(it) }
    val ascii = String(cab, Charsets.ISO_8859_1).map { if (it in ' '..'~') it else '.' }.joinToString("")
    val formato = when {
        cab.size >= 8 && String(cab, 4, 4, Charsets.ISO_8859_1) == "ftyp" -> "MP4/MOV (ftyp)"
        cab.size >= 4 && cab[0] == 0x1A.toByte() && cab[1] == 0x45.toByte() -> "Matroska/WebM"
        cab.size >= 4 && String(cab, 0, 4, Charsets.ISO_8859_1) == "RIFF" -> "AVI/WAV (RIFF)"
        cab.size >= 3 && String(cab, 0, 3, Charsets.ISO_8859_1) == "ID3" -> "MP3 (ID3)"
        cab.isNotEmpty() && cab[0] == 0x47.toByte() -> "MPEG-TS"
        else -> "formato no reconocido"
    }
    return "Archivo: ${f.name}\nTamaño: ${ServidorTam(f.length())} (${f.length()} bytes)\nCabecera: $hex  «$ascii»  → $formato"
}

private fun ServidorTam(b: Long): String = com.antena.tv.archivos.Almacenamiento.tamano(b)

private fun descripcionPistas(t: Tracks): String = t.groups.joinToString("; ") { g ->
    val f = g.mediaTrackGroup.getFormat(0)
    val ok = if (g.isSupported) "sí" else "NO soportada"
    "${f.sampleMimeType ?: "?"}${f.codecs?.let { " [$it]" } ?: ""}${if (f.width > 0) " ${f.width}x${f.height}" else ""} ($ok)"
}

private fun pista(codigo: Int): String = when (codigo) {
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ->
        "Pista: el contenedor está dañado o no es de un formato que este reproductor lea (descarga incompleta o archivo raro)."
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODING_FAILED, PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
        "Pista: el televisor no tiene decodificador para el códec de video/audio de este archivo."
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND, PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
        "Pista: no se puede leer el archivo (borrado o sin permiso)."
    else -> "Pista: envía este texto para diagnosticar."
}

private fun formatoTiempo(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val seg = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, seg) else "%d:%02d".format(m, seg)
}
