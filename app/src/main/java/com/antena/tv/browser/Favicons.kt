package com.antena.tv.browser

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antena.tv.ui.AntenaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Favicon del sitio con caché en memoria.
 * Dos fuentes en cadena: el icono real del sitio (DuckDuckGo) y el servicio de
 * Google como respaldo; si ninguna responde, la ficha muestra la inicial del
 * nombre. Los fallos también se cachean para no repetir la descarga en cada
 * recomposición.
 */
object Favicons {
    private val SIN = Any()
    private val cache: MutableMap<String, Any> =
        java.util.Collections.synchronizedMap(mutableMapOf())

    suspend fun get(url: String): Bitmap? {
        val host = hostOf(url) ?: return null
        cache[host]?.let { return it as? Bitmap }
        val bmp = withContext(Dispatchers.IO) { descargar(host) }
        cache[host] = bmp ?: SIN
        return bmp
    }

    private fun descargar(host: String): Bitmap? {
        val fuentes = listOf(
            "https://icons.duckduckgo.com/ip3/$host.ico",
            "https://www.google.com/s2/favicons?sz=128&domain=$host",
        )
        for (src in fuentes) {
            try {
                val conn = java.net.URL(src).openConnection()
                    .apply { connectTimeout = 5000; readTimeout = 6000 }
                val bytes = conn.getInputStream().use { it.readBytes() }
                var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                if (bmp.width <= 0 || bmp.height <= 0) continue
                // Algunas fuentes devuelven iconos gigantes: los reducimos a 128 px.
                if (bmp.width > 128) {
                    val alto = (128f * bmp.height / bmp.width).toInt().coerceAtLeast(1)
                    val reducido = Bitmap.createScaledBitmap(bmp, 128, alto, true)
                    if (reducido !== bmp) bmp.recycle()
                    bmp = reducido
                }
                return bmp
            } catch (_: Throwable) {
                // Fuente no disponible: probamos la siguiente.
            }
        }
        return null
    }
}

/**
 * Caja cuadrada con el logo del sitio; si no hay favicon, la inicial del nombre.
 * El fondo claro hace que los logos (casi siempre a color) resalten sobre la
 * ficha oscura.
 */
@Composable
fun FaviconBox(
    url: String,
    char: String,
    size: Dp,
    background: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = AntenaType.panel,
    /**
     * Aire alrededor del logo: 7 dp para las fichas grandes (inicio, favoritos).
     * Los chips de pestaña del Paso 2 lo reducen, porque a 18 dp con 7 dp de
     * padding el icono quedaría casi invisible.
     */
    iconPadding: Dp = 7.dp,
) {
    var bmp by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) { bmp = Favicons.get(url) }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        val icono = bmp
        if (icono != null) {
            Image(
                bitmap = icono.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(iconPadding),
            )
        } else {
            Text(char, style = textStyle, color = textColor)
        }
    }
}
