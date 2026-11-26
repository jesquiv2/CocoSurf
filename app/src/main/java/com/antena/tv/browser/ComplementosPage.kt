// M5 · pantalla «Complementos»: administración de las extensiones del motor
// Gecko dentro de Antena (lista, activar/desactivar, desinstalar e instalar
// desde la tienda AMO). Es la versión Compose del panel del spike, con el mismo
// flujo probado allí: descargar → montar → lista → activar/desactivar → quitar.
package com.antena.tv.browser

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.antena.tv.gecko.EXT_INTERNA
import com.antena.tv.gecko.ExtInfo
import com.antena.tv.ui.Interruptor
import com.antena.tv.ui.Separador
import com.antena.tv.ui.cristal
import com.antena.tv.ui.EsquinaVentana
import com.antena.tv.ui.Ambar
import com.antena.tv.ui.AntenaType
import com.antena.tv.ui.Azul
import com.antena.tv.ui.Casco
import com.antena.tv.ui.Escudo
import com.antena.tv.ui.Fosforo
import com.antena.tv.ui.FosforoTenue
import com.antena.tv.ui.Noche

/**
 * Addon del catálogo: nombre visible, **id real** que Gecko le asigna al
 * montarlo (`getMetaData().getId()` — es la única referencia fiable para saber
 * si ya está instalado), enlace directo al .xpi firmado y su ficha oficial en
 * AMO (la ficha es la página que el usuario abre si quiere revisar permisos,
 * versión o compatibilidad antes de instalar).
 */
internal data class AddonTienda(
    val nombre: String,
    val id: String,
    val xpi: String,
    val ficha: String,
    /**
     * Limitación real y comprobada de esa extensión en Android/GeckoView, si la
     * tiene. Se muestra junto a la fila para que nadie instale algo creyendo que
     * hace más de lo que puede; null cuando funciona completa.
     */
    val nota: String? = null,
)

/**
 * Slug de AMO de un enlace del catálogo: vale tanto el `.xpi`
 * (`…/downloads/latest/<slug>/latest.xpi`) como la ficha (`…/addon/<slug>/`).
 *
 * Existe por el fallo real del paso 1: el catálogo llevaba un slug inventado
 * (`sponsorblock-for-youtube`) y AMO respondía 404, que Gecko sólo sabía contar
 * como `InstallException null`. Con esto, `ComplementosTiendaTest` comprueba que
 * el `.xpi` y la ficha son **del mismo complemento** antes de publicar el catálogo.
 */
internal fun slugDeAmox(url: String): String? {
    val patrones = listOf(
        Regex("/downloads/latest/([^/]+)/"),
        Regex("/addon/([^/]+)/?"),
    )
    return patrones.firstNotNullOfOrNull { it.find(url)?.groupValues?.get(1) }
}

/** Índice general de AMO (el «Explorar la tienda» de siempre). */
private const val AMO_INDICE = "https://addons.mozilla.org/es-ES/firefox/extensions/"

/** Links directos a la tienda AMO (.xpi firmados) para montar en un toque. */
internal val TIENDA = listOf(
    AddonTienda(
        "uBlock Origin",
        "uBlock0@raymondhill.net",
        "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi",
        "https://addons.mozilla.org/es-ES/firefox/addon/ublock-origin/",
    ),
    // El escudo verde: bloqueador de anuncios y rastreadores (AMO: adguard-adblocker).
    AddonTienda(
        "AdGuard AdBlocker",
        "adguardadblocker@adguard.com",
        "https://addons.mozilla.org/firefox/downloads/latest/adguard-adblocker/latest.xpi",
        "https://addons.mozilla.org/es-ES/firefox/addon/adguard-adblocker/",
    ),
    AddonTienda(
        "Dark Reader",
        "addon@darkreader.org",
        "https://addons.mozilla.org/firefox/downloads/latest/darkreader/latest.xpi",
        "https://addons.mozilla.org/es-ES/firefox/addon/darkreader/",
    ),
    // OJO: el slug de AMO es «sponsorblock». «sponsorblock-for-youtube» no existe
    // (devuelve 404) y era la causa de «ERROR al instalar: InstallException null».
    AddonTienda(
        "SponsorBlock",
        "sponsorBlocker@ajay.app",
        "https://addons.mozilla.org/firefox/downloads/latest/sponsorblock/latest.xpi",
        "https://addons.mozilla.org/es-ES/firefox/addon/sponsorblock/",
    ),
    // ── Paso 8 · evaluadas contra su manifiesto firmado de AMO ──
    // Chrome Mask: declara `gecko_android` (min 115), tiene popup y página de
    // opciones propias, y reescribe la UA por sitio con webRequestBlocking.
    AddonTienda(
        "Chrome Mask",
        "chrome-mask@overengineer.dev",
        "https://addons.mozilla.org/firefox/downloads/latest/chrome-mask/latest.xpi",
        "https://addons.mozilla.org/es-ES/firefox/addon/chrome-mask/",
    ),
    // Privacy Badger: bloqueo heurístico real; su popup en Android se abre en una
    // pestaña (su propio polyfill `firefoxandroid.js` usa `tabs.create`, que la
    // app ya atiende desde el paso 7).
    AddonTienda(
        "Privacy Badger",
        "jid1-MnnxcxisBPnSXQ@jetpack",
        "https://addons.mozilla.org/firefox/downloads/latest/privacy-badger17/latest.xpi",
        "https://addons.mozilla.org/es-ES/firefox/addon/privacy-badger17/",
    ),
    // Google Search Fixer: no declara `action` ni página de opciones (no tendrá
    // filas propias), pero sí reescribe la UA de las páginas de Google.
    AddonTienda(
        "Google Search Fixer",
        "{58c32ac4-0d6c-4d6f-ae2c-96aaf8ffcb66}",
        "https://addons.mozilla.org/firefox/downloads/latest/google-search-fixer/latest.xpi",
        "https://addons.mozilla.org/es-ES/firefox/addon/google-search-fixer/",
        nota = "Sólo actúa en las páginas de Google y no trae icono ni página de opciones " +
            "propias: no tiene botón que pulsar, funciona mientras esté activa",
    ),
)

/**
 * ¿Esta ficha del catálogo ya está montada en el motor?
 *
 * La prueba se hace contra la lista **real** que informa Gecko
 * (`WebExtensionController.list()`), que es la única fuente de verdad: pulsar
 * «Instalar» no marca nada. El id del manifiesto es la referencia y el nombre
 * visible (`getMetaData().getName()`) sirve de respaldo por si el id cambiara de
 * una versión a otra del complemento.
 */
internal fun addonInstalado(addon: AddonTienda, instaladas: List<ExtInfo>): Boolean =
    instaladas.any { ext ->
        ext.id.equals(addon.id, ignoreCase = true) ||
            ext.nombre.trim().equals(addon.nombre, ignoreCase = true)
    }

/**
 * Catálogo de la tienda sin lo que el motor ya tiene instalado: la sección
 * «disponibles» sólo ofrece lo que de verdad se puede montar. Como la lista de
 * instaladas la informa Gecko, un addon desinstalado (o que dejó de estar
 * instalado) reaparece aquí solo, sin refrescos manuales del usuario.
 */
internal fun addonsDisponibles(instaladas: List<ExtInfo>): List<AddonTienda> =
    TIENDA.filterNot { addonInstalado(it, instaladas) }

@Composable
fun ComplementosPage(
    extensiones: List<ExtInfo>,
    /** Paso 7 · icono real de cada extensión, tal como lo entrega el motor. */
    iconos: Map<String, Bitmap>,
    aviso: String?,
    onAlternar: (String) -> Unit,
    onDesinstalar: (ExtInfo) -> Unit,
    onInstalar: (String) -> Unit,
    onAbrirTienda: (String) -> Unit,
    onPestañaGecko: () -> Unit,
    /** ¿Esta extensión tiene su botón en la barra? */
    ancladas: (ExtInfo) -> Boolean = { true },
    onAnclar: (ExtInfo) -> Unit = {},
    onAbrirExt: (ExtInfo) -> Unit = {},
    onClose: () -> Unit = {},
) {
    var confirmando by remember { mutableStateOf<ExtInfo?>(null) }
    var pidiendoUrl by remember { mutableStateOf(false) }
    val propias = extensiones.filter { it.id != EXT_INTERNA }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Complementos", style = AntenaType.section, color = Fosforo, modifier = Modifier.weight(1f))
                RemoteChip(label = "", icon = ToolIcon.Close, small = true, onClick = onClose)
            }
            Text(
                "Extensiones de Firefox: se aplican a todas las pestañas.",
                style = AntenaType.meta,
                color = FosforoTenue,
            )
            Spacer(Modifier.height(22.dp))

            SeccionAjustes("Instalados · ${propias.size}") {
                if (propias.isEmpty()) {
                    Text(
                        "Aún no hay complementos instalados.",
                        style = AntenaType.body,
                        color = FosforoTenue,
                        modifier = Modifier.padding(14.dp),
                    )
                }
                propias.forEachIndexed { i, ext ->
                    if (i > 0) Separador(Modifier.padding(horizontal = 14.dp))
                    FilaAjuste(
                        titulo = ext.nombre,
                        descripcion = "v${ext.version ?: "?"} · " +
                            if (ext.activa) "activo" else "desactivado",
                        onClick = { onAbrirExt(ext) },
                        icono = { ExtIcono(ext, iconos[ext.id], lado = 36.dp) },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Interruptor(ext.activa, { onAlternar(ext.id) })
                            Spacer(Modifier.width(10.dp))
                            RemoteChip(
                                label = "",
                                icon = if (ancladas(ext)) ToolIcon.PinFilled else ToolIcon.Pin,
                                accent = ancladas(ext),
                                small = true,
                                ghost = true,
                                onClick = { onAnclar(ext) },
                            )
                            Spacer(Modifier.width(2.dp))
                            RemoteChip(
                                label = "",
                                icon = ToolIcon.Trash,
                                small = true,
                                ghost = true,
                                onClick = { confirmando = ext },
                            )
                        }
                    }
                }
            }

            // Sólo lo que falta por instalar: el estado lo informa Gecko, así que
            // instalar hace desaparecer la fila y desinstalar la devuelve.
            val disponibles = addonsDisponibles(extensiones)
            SeccionAjustes("Añadir") {
                FilaAjuste(
                    titulo = "Explorar la tienda de Firefox",
                    descripcion = "addons.mozilla.org",
                    onClick = { onAbrirTienda(AMO_INDICE) },
                ) { ToolIconGlyph(ToolIcon.Link, FosforoTenue, size = 22.dp) }
                disponibles.forEach { addon ->
                    Separador(Modifier.padding(horizontal = 14.dp))
                    FilaAjuste(
                        titulo = addon.nombre,
                        descripcion = addon.nota ?: "Instalar desde la tienda oficial",
                        onClick = { onInstalar(addon.xpi) },
                    ) {
                        RemoteChip(label = "Instalar", small = true, accent = true, onClick = { onInstalar(addon.xpi) })
                    }
                }
                Separador(Modifier.padding(horizontal = 14.dp))
                FilaAjuste(
                    titulo = "Instalar desde una dirección…",
                    descripcion = "Un archivo .xpi de Mozilla",
                    onClick = { pidiendoUrl = true },
                ) { ToolIconGlyph(ToolIcon.NewTab, FosforoTenue, size = 22.dp) }
            }

            aviso?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = AntenaType.monoSmall, color = FosforoTenue, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }

    confirmando?.let { ext ->
        Dialogo(
            titulo = "Desinstalar",
            mensaje = "¿Quitar «${ext.nombre}» de Coco Surf?",
            positivo = "Desinstalar",
            onPositivo = {
                confirmando = null
                onDesinstalar(ext)
            },
            onCerrar = { confirmando = null },
        )
    }

    if (pidiendoUrl) {
        DialogoUrl(
            onInstalar = { url ->
                pidiendoUrl = false
                onInstalar(url)
            },
            onCerrar = { pidiendoUrl = false },
        )
    }
}

/* ── Chip pequeño (desinstalar, aceptar, cancelar…) ── */
@Composable
private fun Chip(texto: String, onClick: () -> Unit) {
    // Un solo estilo de botón para toda la aplicación.
    RemoteChip(label = texto, small = true, peligro = texto == "Desinstalar", onClick = onClick)
}

/* ── Diálogo de confirmación ── */
@Composable
private fun Dialogo(
    titulo: String,
    mensaje: String,
    positivo: String,
    onPositivo: () -> Unit,
    onCerrar: () -> Unit,
) {
    val primero = remember { FocusRequester() }
    LaunchedEffect(Unit) { primero.requestFocus() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .pointerInput(onCerrar) { detectTapGestures { onCerrar() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(460.dp)
                .cristal(RoundedCornerShape(EsquinaVentana), elevacion = 32.dp)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(26.dp),
        ) {
            Text(titulo, style = AntenaType.panel, color = Fosforo)
            Spacer(Modifier.height(10.dp))
            Text(mensaje, style = AntenaType.body, color = FosforoTenue)
            Spacer(Modifier.height(18.dp))
            Row {
                Box(Modifier.focusRequester(primero)) {
                    Chip(texto = positivo, onClick = onPositivo)
                }
                Spacer(Modifier.width(10.dp))
                Chip(texto = "Cancelar", onClick = onCerrar)
            }
        }
    }
}

/* ── Diálogo «instalar .xpi desde otra URL» (con pegar del portapapeles) ── */
@Composable
private fun DialogoUrl(onInstalar: (String) -> Unit, onCerrar: () -> Unit) {
    val context = LocalContext.current
    var url by remember {
        mutableStateOf("https://addons.mozilla.org/firefox/downloads/latest/darkreader/latest.xpi")
    }
    val campo = remember { FocusRequester() }
    LaunchedEffect(Unit) { campo.requestFocus() }
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .pointerInput(onCerrar) { detectTapGestures { onCerrar() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(620.dp)
                .cristal(RoundedCornerShape(EsquinaVentana), elevacion = 32.dp)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(26.dp),
        ) {
            Text("Instalar .xpi desde URL", style = AntenaType.panel, color = Fosforo)
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = url,
                onValueChange = { url = it },
                singleLine = true,
                textStyle = AntenaType.body.copy(color = Fosforo),
                cursorBrush = SolidColor(Ambar),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(campo)
                    .clip(shape)
                    .background(Noche)
                    .border(1.dp, Azul.copy(alpha = 0.5f), shape)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip(texto = "Instalar") {
                    url.trim().takeIf { it.isNotBlank() }?.let(onInstalar)
                }
                Spacer(Modifier.width(10.dp))
                Chip(texto = "Cancelar", onClick = onCerrar)
                Spacer(Modifier.weight(1f))
                Chip(texto = "📋 Pegar") {
                    pegarPortapapeles(context)?.let { url = it }
                }
            }
        }
    }
}

/** Lee el portapapeles (en TV sirve para traer una URL copiada desde otro sitio). */
internal fun pegarPortapapeles(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotBlank() }
}
