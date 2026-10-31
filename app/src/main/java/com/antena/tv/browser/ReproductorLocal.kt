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
import com.antena.tv.cursor.Puntero
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
    val esAudio = Almacenamiento.tipoDe(archivo) == Tipo.AUDIO
    val player = remember(archivo) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(archivo)))
            prepare()
            playWhenReady = true
        }
    }
    var reproduciendo by remember { mutableStateOf(true) }
    var posicion by remember { mutableLongStateOf(0L) }
    var duracion by remember { mutableLongStateOf(0L) }
    var controles by remember { mutableStateOf(true) }
    var ultimaAccion by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var error by remember { mutableStateOf<String?>(null) }
    val foco = remember { FocusRequester() }

    DisposableEffect(player) {
        // Con el reproductor abierto las flechas y OK son suyos, no del cursor.
        Puntero.pausadoExtra = true
        val oyente = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                reproduciendo = isPlaying
            }

            override fun onPlayerError(e: PlaybackException) {
                error = "No se pudo reproducir este archivo (${e.errorCodeName})."
            }
        }
        player.addListener(oyente)
        onDispose {
            player.removeListener(oyente)
            player.release()
            Puntero.pausadoExtra = false
        }
    }

    LaunchedEffect(player) {
        while (true) {
            posicion = player.currentPosition
            duracion = player.duration.coerceAtLeast(0L)
            if (controles && player.isPlaying && System.currentTimeMillis() - ultimaAccion > 4_000) controles = false
            delay(400)
        }
    }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { foco.requestFocus() }
    }
    BackHandler { onClose() }

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
                    when (ev.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> {
                            if (repetido == 0) alternar()
                            true
                        }
                        Key.MediaPlay -> { player.play(); mostrar(); true }
                        Key.MediaPause -> { player.pause(); mostrar(); true }
                        Key.DirectionLeft, Key.MediaRewind -> { saltar(if (repetido > 3) -30_000 else -10_000); true }
                        Key.DirectionRight, Key.MediaFastForward -> { saltar(if (repetido > 3) 30_000 else 10_000); true }
                        Key.DirectionUp, Key.DirectionDown -> { mostrar(); true }
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
                Text(archivo.nameWithoutExtension, style = AntenaType.panel, color = Fosforo)
            }
        }

        error?.let {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(it, style = AntenaType.panel, color = Fosforo)
                Spacer(Modifier.height(6.dp))
                Text("El televisor puede no tener el códec de este video.", style = AntenaType.body, color = FosforoTenue)
                Spacer(Modifier.height(6.dp))
                Text("Atrás para cerrar", style = AntenaType.meta, color = FosforoTenue)
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
                Text(archivo.name, style = AntenaType.body, color = Fosforo, maxLines = 1)
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
                        if (reproduciendo) "OK: pausa · ← → saltar 10 s · Atrás: cerrar" else "En pausa · OK: seguir · Atrás: cerrar",
                        style = AntenaType.meta,
                        color = FosforoTenue,
                    )
                    Text(formatoTiempo(duracion), style = AntenaType.monoSmall, color = FosforoTenue)
                }
            }
        }
    }
}

private fun formatoTiempo(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val seg = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, seg) else "%d:%02d".format(m, seg)
}
