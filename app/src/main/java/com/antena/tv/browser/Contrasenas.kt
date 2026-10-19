package com.antena.tv.browser

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.mutableStateListOf
import com.antena.tv.log.Bitacora
import com.antena.tv.log.LogKind
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult

/** Una contraseña guardada. */
data class Clave(
    val guid: String,
    val origen: String,
    val formOrigen: String?,
    val realm: String?,
    val usuario: String,
    val clave: String,
    val modificada: Long,
) {
    /** Nombre legible del sitio: «www.facebook.com». */
    val sitio: String get() = hostOf(origen) ?: origen
}

/** Gecko pregunta si guarda (o actualiza) una contraseña. */
class PeticionGuardar(val entry: Autocomplete.LoginEntry, val actualiza: Boolean, val responder: (Boolean) -> Unit)

/** Gecko ofrece elegir entre varias contraseñas guardadas del sitio. */
class PeticionElegir(val opciones: List<Autocomplete.LoginEntry>, val responder: (Autocomplete.LoginEntry?) -> Unit)

/**
 * Gestor de contraseñas propio: se guardan en el aparato, cifradas con AES‑GCM (la clave
 * vive en el Keystore de Android y no sale de él). Gecko lo usa para sugerir y rellenar
 * al entrar en un sitio, y para ofrecer guardar cuando se inicia sesión.
 */
object Contrasenas {
    /** Lista para la pantalla de administración (ya descifrada, sólo en memoria). */
    val lista = mutableStateListOf<Clave>()

    private var archivo: File? = null
    private var iniciado = false
    private val h = Handler(Looper.getMainLooper())
    private const val ALIAS = "cocosurf_claves"

    @Synchronized
    fun iniciar(c: Context) {
        if (iniciado) return
        iniciado = true
        archivo = File(c.applicationContext.filesDir, "contrasenas.json")
        val cargadas = runCatching {
            val f = archivo ?: return@runCatching emptyList<Clave>()
            if (!f.exists()) return@runCatching emptyList<Clave>()
            val arr = JSONArray(f.readText())
            (0 until arr.length()).mapNotNull { i ->
                runCatching {
                    val o = arr.getJSONObject(i)
                    Clave(
                        guid = o.getString("guid"),
                        origen = o.getString("origen"),
                        formOrigen = o.optString("form").ifBlank { null },
                        realm = o.optString("realm").ifBlank { null },
                        usuario = o.optString("usuario"),
                        clave = descifrar(o.getString("clave")),
                        modificada = o.optLong("mod"),
                    )
                }.getOrNull()
            }
        }.getOrElse {
            Bitacora.add(LogKind.ERROR, "No se pudieron leer las contraseñas guardadas: ${it.message}")
            emptyList()
        }
        h.post { lista.clear(); lista.addAll(cargadas.sortedBy { it.sitio }) }
        copia = cargadas.toMutableList()
    }

    /** Copia de trabajo segura entre hilos; `lista` es sólo el reflejo para la pantalla. */
    private var copia = mutableListOf<Clave>()

    @Synchronized
    private fun persistir() {
        val f = archivo ?: return
        runCatching {
            val arr = JSONArray()
            copia.forEach { k ->
                arr.put(
                    JSONObject()
                        .put("guid", k.guid).put("origen", k.origen)
                        .put("form", k.formOrigen ?: "").put("realm", k.realm ?: "")
                        .put("usuario", k.usuario).put("clave", cifrar(k.clave))
                        .put("mod", k.modificada),
                )
            }
            f.writeText(arr.toString())
        }.onFailure { Bitacora.add(LogKind.ERROR, "No se pudieron guardar las contraseñas: ${it.message}") }
        val orden = copia.sortedBy { it.sitio }
        h.post { lista.clear(); lista.addAll(orden) }
    }

    // ── Operaciones ───────────────────────────────────────────────────────

    @Synchronized
    fun todas(): List<Clave> = copia.toList()

    @Synchronized
    fun paraSitio(dominio: String): List<Clave> {
        val host = hostOf(dominio) ?: dominio
        return copia.filter { it.sitio == host || host.endsWith("." + it.sitio) || it.sitio.endsWith("." + host) }
    }

    /** ¿Guardar esto sería una novedad o cambia una contraseña ya guardada? */
    @Synchronized
    fun existente(e: Autocomplete.LoginEntry): Clave? =
        copia.firstOrNull { (it.guid == e.guid && !e.guid.isNullOrBlank()) || (it.origen == e.origin && it.usuario == (e.username ?: "")) }

    @Synchronized
    fun guardar(e: Autocomplete.LoginEntry) {
        val previa = existente(e)
        val nueva = Clave(
            guid = previa?.guid ?: e.guid?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            origen = e.origin ?: return,
            formOrigen = e.formActionOrigin,
            realm = e.httpRealm,
            usuario = e.username ?: "",
            clave = e.password ?: "",
            modificada = System.currentTimeMillis(),
        )
        if (previa != null) copia[copia.indexOf(previa)] = nueva else copia.add(nueva)
        persistir()
        Bitacora.add(LogKind.SISTEMA, "Contraseña ${if (previa != null) "actualizada" else "guardada"}: ${nueva.sitio} (${nueva.usuario})")
    }

    @Synchronized
    fun editar(guid: String, usuario: String, clave: String) {
        val i = copia.indexOfFirst { it.guid == guid }
        if (i < 0) return
        copia[i] = copia[i].copy(usuario = usuario, clave = clave, modificada = System.currentTimeMillis())
        persistir()
    }

    @Synchronized
    fun borrar(guid: String) {
        copia.removeAll { it.guid == guid }
        persistir()
    }

    @Synchronized
    fun borrarTodas() {
        copia.clear()
        persistir()
    }

    /**
     * Importa un CSV exportado de Chrome, Edge, Firefox o Bitwarden (columnas url/usuario/clave).
     * Devuelve cuántas contraseñas nuevas o actualizadas entraron.
     */
    fun importarCsv(texto: String): Int {
        val filas = parseCsv(texto)
        if (filas.size < 2) return 0
        val cab = filas[0].map { it.trim().lowercase().removePrefix("\uFEFF") }
        fun col(vararg n: String) = cab.indexOfFirst { it in n }
        val iUrl = col("url", "login_uri", "origin", "website")
        val iUser = col("username", "login_username", "user", "usuario")
        val iPass = col("password", "login_password", "contraseña", "clave")
        if (iUrl < 0 || iPass < 0) return 0
        var n = 0
        for (f in filas.drop(1)) {
            val url = f.getOrNull(iUrl)?.trim().orEmpty()
            val pass = f.getOrNull(iPass).orEmpty()
            if (url.isBlank() || pass.isEmpty()) continue
            val m = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*://[^/?#]+)").find(url)
                ?: Regex("^([^/?#]+)").find(url)?.let { return@let null }
            val origen = m?.groupValues?.get(1) ?: "https://" + url.substringBefore('/')
            val e = Autocomplete.LoginEntry.Builder().origin(origen).formActionOrigin(origen)
                .username(f.getOrNull(iUser).orEmpty().trim()).password(pass).build()
            guardar(e)
            n++
        }
        return n
    }

    /** Lector CSV mínimo (comillas dobles, comas y saltos de línea dentro de comillas). */
    private fun parseCsv(t: String): List<List<String>> {
        val filas = mutableListOf<List<String>>()
        var fila = mutableListOf<String>()
        val sb = StringBuilder()
        var comillas = false
        var i = 0
        while (i < t.length) {
            val ch = t[i]
            when {
                comillas && ch == '"' && t.getOrNull(i + 1) == '"' -> { sb.append('"'); i++ }
                ch == '"' -> comillas = !comillas
                !comillas && ch == ',' -> { fila.add(sb.toString()); sb.clear() }
                !comillas && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && t.getOrNull(i + 1) == '\n') i++
                    fila.add(sb.toString()); sb.clear()
                    if (fila.any { it.isNotEmpty() }) filas.add(fila)
                    fila = mutableListOf()
                }
                else -> sb.append(ch)
            }
            i++
        }
        if (sb.isNotEmpty() || fila.isNotEmpty()) { fila.add(sb.toString()); if (fila.any { it.isNotEmpty() }) filas.add(fila) }
        return filas
    }

    fun aEntry(k: Clave): Autocomplete.LoginEntry = Autocomplete.LoginEntry.Builder()
        .guid(k.guid).origin(k.origen).formActionOrigin(k.formOrigen).httpRealm(k.realm)
        .username(k.usuario).password(k.clave).build()

    // ── Cifrado ───────────────────────────────────────────────────────────

    private fun clave(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    /** Si el Keystore de este aparato falla, se usa una clave local (privada de la app). */
    private fun claveRespaldo(): SecretKey {
        val f = File(archivo?.parentFile, "claves.k")
        if (!f.exists()) f.writeBytes(ByteArray(32).also { java.security.SecureRandom().nextBytes(it) })
        return SecretKeySpec(f.readBytes(), "AES")
    }

    private fun cifrar(texto: String): String {
        val (marca, k) = runCatching { "K" to clave() }.getOrElse { "L" to claveRespaldo() }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, k) }
        val datos = c.doFinal(texto.toByteArray())
        return marca + Base64.encodeToString(c.iv + datos, Base64.NO_WRAP)
    }

    private fun descifrar(texto: String): String {
        val k = if (texto.startsWith("K")) clave() else claveRespaldo()
        val b = Base64.decode(texto.substring(1), Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, b, 0, 12)) }
        return String(c.doFinal(b, 12, b.size - 12))
    }

    // ── Conexión con Gecko ────────────────────────────────────────────────

    val almacen = object : Autocomplete.StorageDelegate {
        override fun onLoginFetch(domain: String): GeckoResult<Array<Autocomplete.LoginEntry>> =
            GeckoResult.fromValue(paraSitio(domain).map { aEntry(it) }.toTypedArray())

        override fun onLoginFetch(): GeckoResult<Array<Autocomplete.LoginEntry>> =
            GeckoResult.fromValue(todas().map { aEntry(it) }.toTypedArray())

        override fun onLoginSave(login: Autocomplete.LoginEntry) {
            guardar(login)
        }

        override fun onLoginUsed(login: Autocomplete.LoginEntry, usedFields: Int) = Unit
    }
}
