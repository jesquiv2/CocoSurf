package com.antena.tv.browser

import android.content.Context
import com.antena.tv.db.AntenaDatabase
import com.antena.tv.db.DownloadEntry
import com.antena.tv.db.Favorite
import com.antena.tv.db.Folder
import com.antena.tv.db.HistoryEntry
import com.antena.tv.db.SearchEngine
import com.antena.tv.db.Setting
import com.antena.tv.log.Bitacora
import com.antena.tv.log.LogKind
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * Copia de seguridad de Coco Surf en un solo archivo .json: favoritos y carpetas, historial,
 * registro de descargas, motores de búsqueda, ajustes y —si se pide— las contraseñas guardadas.
 * Se guarda en la carpeta de Descargas de la app y se ofrece enviar a otra app (como el reporte).
 * Al importar, lo del archivo se **suma** a lo que ya hay (misma entrada = se sustituye).
 */
object Respaldo {
    const val PREFIJO = "CocoSurf-respaldo-"
    private const val FORMATO = 1

    /** Ajustes que describen el momento de la sesión, no preferencias: no viajan en el respaldo. */
    private val AJUSTES_DE_SESION = setOf("pestanas_guardadas", "pestana_activa")

    class Resumen(
        val favoritos: Int,
        val carpetas: Int,
        val historial: Int,
        val descargas: Int,
        val motores: Int,
        val ajustes: Int,
        val claves: Int,
    ) {
        fun texto(): String = buildList {
            if (favoritos > 0) add("$favoritos favoritos")
            if (carpetas > 0) add("$carpetas carpetas")
            if (historial > 0) add("$historial del historial")
            if (claves > 0) add("$claves contraseñas")
            if (ajustes > 0) add("$ajustes ajustes")
        }.joinToString(" · ").ifEmpty { "nada nuevo" }
    }

    fun carpeta(c: Context): File = c.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: c.filesDir

    /** Respaldos que hay en la carpeta de Descargas de la app, el más reciente primero. */
    fun archivos(c: Context): List<File> =
        carpeta(c).listFiles { f -> f.isFile && f.name.startsWith(PREFIJO) && f.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    /** Escribe el respaldo y lo anota en la lista de descargas. Llamar fuera del hilo principal. */
    fun exportar(c: Context, db: AntenaDatabase, version: String, conClaves: Boolean): File {
        val raiz = JSONObject()
            .put("app", "CocoSurf")
            .put("formato", FORMATO)
            .put("version", version)
            .put("fecha", System.currentTimeMillis())
            .put("dispositivo", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")

        raiz.put("ajustes", JSONArray().also { a ->
            db.settingDao().all().filter { it.key !in AJUSTES_DE_SESION }
                .forEach { a.put(JSONObject().put("k", it.key).put("v", it.value)) }
        })
        raiz.put("motores", JSONArray().also { a ->
            db.engineDao().all().forEach { a.put(JSONObject().put("id", it.id).put("n", it.name).put("t", it.template)) }
        })
        raiz.put("carpetas", JSONArray().also { a ->
            db.folderDao().all().forEach { a.put(JSONObject().put("id", it.id).put("n", it.name)) }
        })
        raiz.put("favoritos", JSONArray().also { a ->
            db.favoriteDao().all().forEach {
                a.put(JSONObject().put("id", it.id).put("n", it.name).put("u", it.url).put("c", it.folderId ?: JSONObject.NULL))
            }
        })
        raiz.put("historial", JSONArray().also { a ->
            db.historyDao().recent().forEach {
                a.put(JSONObject().put("id", it.id).put("u", it.url).put("t", it.title).put("f", it.visitedAt).put("v", it.visits))
            }
        })
        raiz.put("descargas", JSONArray().also { a ->
            // Sólo el registro: los archivos no viajan, y las entradas del DownloadManager no valen en otro aparato.
            db.downloadDao().all().filter { it.dmId < 0 }.forEach {
                a.put(JSONObject().put("id", it.id).put("u", it.url).put("t", it.title).put("f", it.addedAt))
            }
        })
        if (conClaves) {
            raiz.put("contrasenas", JSONArray().also { a ->
                Contrasenas.todas().forEach {
                    a.put(
                        JSONObject().put("g", it.guid).put("o", it.origen).put("fo", it.formOrigen ?: "")
                            .put("r", it.realm ?: "").put("u", it.usuario).put("c", it.clave).put("m", it.modificada),
                    )
                }
            })
        }

        val fecha = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val archivo = File(carpeta(c), "$PREFIJO$fecha.json")
        archivo.writeText(raiz.toString())
        db.downloadDao().insert(
            DownloadEntry(UUID.randomUUID().toString(), -1L, "file://" + archivo.absolutePath, archivo.name, System.currentTimeMillis()),
        )
        Bitacora.add(LogKind.SISTEMA, "Respaldo exportado: ${archivo.name}${if (conClaves) " (con contraseñas)" else ""}")
        return archivo
    }

    /** Vuelca un respaldo en la base. Lanza si el archivo no es de Coco Surf. Fuera del hilo principal. */
    fun importar(db: AntenaDatabase, texto: String): Resumen {
        val raiz = JSONObject(texto)
        require(raiz.optString("app") == "CocoSurf") { "no es un respaldo de Coco Surf" }
        fun lista(n: String): List<JSONObject> {
            val a = raiz.optJSONArray(n) ?: return emptyList()
            return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
        }

        val motores = lista("motores").map { SearchEngine(it.getString("id"), it.getString("n"), it.getString("t")) }
        if (motores.isNotEmpty()) db.engineDao().upsertAll(motores)

        val carpetas = lista("carpetas").map { Folder(it.getString("id"), it.getString("n")) }
        carpetas.forEach { db.folderDao().upsert(it) }

        val favoritos = lista("favoritos").map {
            Favorite(it.getString("id"), it.getString("n"), it.getString("u"), if (it.isNull("c")) null else it.optString("c"))
        }
        favoritos.forEach { db.favoriteDao().upsert(it) }

        val historial = lista("historial").map {
            HistoryEntry(it.getString("id"), it.getString("u"), it.optString("t"), it.optLong("f"), it.optInt("v", 1))
        }
        historial.forEach { db.historyDao().insert(it) }

        val descargas = lista("descargas").map {
            DownloadEntry(it.getString("id"), -1L, it.getString("u"), it.optString("t"), it.optLong("f"))
        }
        descargas.forEach { db.downloadDao().insert(it) }

        val ajustes = lista("ajustes").filter { it.getString("k") !in AJUSTES_DE_SESION }
        ajustes.forEach { db.settingDao().put(Setting(it.getString("k"), it.optString("v"))) }

        val claves = lista("contrasenas").mapNotNull {
            runCatching {
                Clave(
                    it.getString("g"), it.getString("o"),
                    it.optString("fo").ifBlank { null }, it.optString("r").ifBlank { null },
                    it.optString("u"), it.getString("c"), it.optLong("m"),
                )
            }.getOrNull()
        }
        claves.forEach { Contrasenas.guardar(Contrasenas.aEntry(it)) }

        Bitacora.add(LogKind.SISTEMA, "Respaldo importado: ${favoritos.size} favoritos, ${historial.size} del historial, ${claves.size} contraseñas, ${ajustes.size} ajustes")
        return Resumen(favoritos.size, carpetas.size, historial.size, descargas.size, motores.size, ajustes.size, claves.size)
    }
}
