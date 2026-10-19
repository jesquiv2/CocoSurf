package com.antena.tv.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antena.tv.cursor.zonaScroll
import com.antena.tv.ui.Ambar
import com.antena.tv.ui.AntenaType
import com.antena.tv.ui.Azul
import com.antena.tv.ui.EsquinaTarjeta
import com.antena.tv.ui.Fosforo
import com.antena.tv.ui.FosforoTenue
import com.antena.tv.ui.Noche
import com.antena.tv.ui.Separador
import com.antena.tv.ui.cristal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Tarjeta flotante: «¿Guardar la contraseña?» tras iniciar sesión en un sitio. */
@Composable
fun ClaveGuardarCard(p: PeticionGuardar, modifier: Modifier = Modifier) {
    val sitio = hostOf(p.entry.origin ?: "") ?: p.entry.origin ?: ""
    Column(
        modifier
            .widthIn(max = 520.dp)
            .cristal(RoundedCornerShape(EsquinaTarjeta), elevacion = 24.dp)
            .padding(18.dp),
    ) {
        Text(
            if (p.actualiza) "¿Actualizar la contraseña?" else "¿Guardar la contraseña?",
            style = AntenaType.panel,
            color = Fosforo,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${p.entry.username?.ifBlank { null } ?: "(sin usuario)"} · $sitio",
            style = AntenaType.body,
            color = FosforoTenue,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RemoteChip(label = if (p.actualiza) "Actualizar" else "Guardar", accent = true, onClick = { p.responder(true) })
            RemoteChip(label = "Ahora no", onClick = { p.responder(false) })
        }
    }
}

/** Tarjeta flotante: elegir con qué cuenta entrar (contraseñas guardadas del sitio). */
@Composable
fun ClaveElegirCard(p: PeticionElegir, modifier: Modifier = Modifier) {
    Column(
        modifier
            .widthIn(max = 520.dp)
            .cristal(RoundedCornerShape(EsquinaTarjeta), elevacion = 24.dp)
            .padding(18.dp),
    ) {
        Text("Entrar con una contraseña guardada", style = AntenaType.panel, color = Fosforo)
        Spacer(Modifier.height(10.dp))
        p.opciones.forEach { e ->
            OptionRow(
                label = "${e.username?.ifBlank { null } ?: "(sin usuario)"}  ·  ${hostOf(e.origin ?: "") ?: ""}",
                selected = false,
                onClick = { p.responder(e) },
            )
        }
        Spacer(Modifier.height(10.dp))
        RemoteChip(label = "Cerrar", onClick = { p.responder(null) })
    }
}

/** Administración: ver, cambiar y eliminar las contraseñas guardadas. */
@Composable
fun ClavesPage(onBack: () -> Unit, backLabel: String = "✕ Cerrar") {
    val lista = Contrasenas.lista
    var editando by remember { mutableStateOf<String?>(null) }
    var visibles by remember { mutableStateOf(setOf<String>()) }
    var confirmarTodas by remember { mutableStateOf(false) }
    val fecha = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val estado = rememberScrollState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var aviso by remember { mutableStateOf<String?>(null) }
    val selector = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            aviso = runCatching {
                val texto = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                val n = Contrasenas.importarCsv(texto)
                if (n > 0) "Se importaron $n contraseñas. Borra el archivo CSV: no está cifrado." else "No encontré contraseñas en ese archivo."
            }.getOrElse { "No se pudo leer el archivo." }
        }
    }

    Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 30.dp)) {
        Column(Modifier.widthIn(max = 820.dp).fillMaxWidth().align(Alignment.CenterHorizontally)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Contraseñas", style = AntenaType.section, color = Fosforo, modifier = Modifier.weight(1f))
                if (lista.isNotEmpty()) {
                    RemoteChip(
                        label = if (confirmarTodas) "Confirmar: borrar todas" else "Borrar todas",
                        small = true,
                        accent = confirmarTodas,
                        onClick = {
                            if (confirmarTodas) { Contrasenas.borrarTodas(); confirmarTodas = false } else confirmarTodas = true
                        },
                    )
                    Spacer(Modifier.width(10.dp))
                }
                RemoteChip(label = "Importar CSV…", small = true, onClick = {
                    runCatching { selector.launch(arrayOf("text/*", "application/vnd.ms-excel", "application/octet-stream")) }
                        .onFailure { aviso = "Este aparato no tiene selector de archivos." }
                })
                Spacer(Modifier.width(10.dp))
                RemoteChip(label = backLabel, small = true, onClick = onBack)
            }
            aviso?.let { Text(it, style = AntenaType.meta, color = Ambar, modifier = Modifier.padding(top = 6.dp)) }
            Spacer(Modifier.height(6.dp))
            Text(
                "Se guardan en este aparato, cifradas. Cuando inicies sesión en un sitio, Coco Surf " +
                    "te ofrecerá guardarla y, al volver, te la sugerirá. Para traer las de Chrome, Edge o Google: " +
                    "expórtalas a un archivo CSV (Configuración → Contraseñas → Exportar) e impórtalo aquí.",
                style = AntenaType.meta,
                color = FosforoTenue,
            )
            Spacer(Modifier.height(14.dp))

            if (lista.isEmpty()) {
                Text("Todavía no hay contraseñas guardadas.", style = AntenaType.body, color = FosforoTenue)
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(estado).zonaScroll(estado)) {
                lista.forEach { k ->
                    key(k.guid) {
                        FilaClave(
                            k = k,
                            visible = k.guid in visibles,
                            editando = editando == k.guid,
                            fecha = fecha.format(Date(k.modificada)),
                            onVer = { visibles = if (k.guid in visibles) visibles - k.guid else visibles + k.guid },
                            onEditar = { editando = if (editando == k.guid) null else k.guid },
                            onGuardar = { u, c -> Contrasenas.editar(k.guid, u, c); editando = null },
                            onBorrar = { Contrasenas.borrar(k.guid) },
                        )
                        Separador(Modifier.padding(vertical = 4.dp))
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun FilaClave(
    k: Clave,
    visible: Boolean,
    editando: Boolean,
    fecha: String,
    onVer: () -> Unit,
    onEditar: () -> Unit,
    onGuardar: (String, String) -> Unit,
    onBorrar: () -> Unit,
) {
    var usuario by remember(k.guid, editando) { mutableStateOf(k.usuario) }
    var clave by remember(k.guid, editando) { mutableStateOf(k.clave) }
    var borrar by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(k.sitio, style = AntenaType.body, color = Fosforo, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${k.usuario.ifBlank { "(sin usuario)" }}  ·  ${if (visible) k.clave else "••••••••"}  ·  $fecha",
                    style = AntenaType.meta,
                    color = FosforoTenue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RemoteChip(label = if (visible) "Ocultar" else "Ver", small = true, onClick = onVer)
            Spacer(Modifier.width(6.dp))
            RemoteChip(label = if (editando) "Cancelar" else "Cambiar", small = true, onClick = onEditar)
            Spacer(Modifier.width(6.dp))
            RemoteChip(
                label = if (borrar) "¿Seguro?" else "Eliminar",
                small = true,
                accent = borrar,
                onClick = { if (borrar) onBorrar() else borrar = true },
            )
        }
        if (editando) {
            Spacer(Modifier.height(8.dp))
            CampoClave("Usuario", usuario) { usuario = it }
            Spacer(Modifier.height(8.dp))
            CampoClave("Contraseña", clave) { clave = it }
            Spacer(Modifier.height(10.dp))
            RemoteChip(label = "Guardar cambios", small = true, accent = true, onClick = { onGuardar(usuario.trim(), clave) })
        }
    }
}

@Composable
private fun CampoClave(etiqueta: String, valor: String, alCambiar: (String) -> Unit) {
    Text(etiqueta, style = AntenaType.meta, color = FosforoTenue)
    Spacer(Modifier.height(4.dp))
    BasicTextField(
        value = valor,
        onValueChange = alCambiar,
        textStyle = AntenaType.body.copy(color = Fosforo),
        cursorBrush = SolidColor(Ambar),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Noche)
            .border(1.dp, Azul.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}
