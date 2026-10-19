package com.antena.tv.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

/**
 * Base de datos de Antena. v3: motores, ajustes, favoritos con carpetas,
 * historial de navegación (con uso por dirección) y registro de descargas.
 */
@Entity(tableName = "search_engines")
data class SearchEngine(
    @PrimaryKey val id: String,
    val name: String,
    /** Plantilla de búsqueda: %s se sustituye por la consulta codificada. */
    val template: String,
)

@Entity(tableName = "settings")
data class Setting(
    @PrimaryKey val key: String,
    val value: String,
)

@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey val id: String,
    val name: String,
)

@Entity(tableName = "favorites")
data class Favorite(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    /** null = sin carpeta. */
    val folderId: String?,
)

@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val visitedAt: Long,
    /**
     * Cuántas veces se ha visitado esta dirección. Es el uso real de la URL, no
     * filas repetidas: una misma dirección se actualiza en su sitio (v3), así
     * que la lista de sugerencias puede ordenar por «más utilizadas» sin que el
     * historial se llene de duplicados. Las filas anteriores a v3 valen 1 y las
     * que ya estaban repetidas se suman al agrupar por URL.
     */
    val visits: Int = 1,
)

@Entity(tableName = "downloads")
data class DownloadEntry(
    @PrimaryKey val id: String,
    /** id en el DownloadManager del sistema (para abrir/borrar el archivo). */
    val dmId: Long,
    val url: String,
    val title: String,
    val addedAt: Long,
)

@Dao
interface EngineDao {
    @Query("SELECT * FROM search_engines ORDER BY rowid")
    fun all(): List<SearchEngine>

    @Query("SELECT COUNT(*) FROM search_engines")
    fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(engines: List<SearchEngine>)
}

@Dao
interface SettingDao {
    @Query("SELECT value FROM settings WHERE key = :key")
    fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun put(setting: Setting)
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY name")
    fun all(): List<Folder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(folder: Folder)

    @Query("DELETE FROM folders WHERE id = :id")
    fun delete(id: String)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY name")
    fun all(): List<Favorite>

    @Query("SELECT COUNT(*) FROM favorites")
    fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(favorite: Favorite)

    @Query("DELETE FROM favorites WHERE id = :id")
    fun delete(id: String)

    /** Al borrar una carpeta, sus favoritos quedan sin carpeta. */
    @Query("UPDATE favorites SET folderId = NULL WHERE folderId = :folderId")
    fun detachFolder(folderId: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT 500")
    fun recent(): List<HistoryEntry>

    /** ¿Ya hay una fila para esta dirección? (evita duplicarla al visitarla). */
    @Query("SELECT * FROM history WHERE url = :url ORDER BY visitedAt DESC LIMIT 1")
    fun byUrl(url: String): HistoryEntry?

    /** Dirección ya conocida: se le suma un uso y se refresca título y fecha. */
    @Query("UPDATE history SET visitedAt = :at, title = :title, visits = visits + 1 WHERE id = :id")
    fun touch(id: String, at: Long, title: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entry: HistoryEntry)

    @Query("DELETE FROM history WHERE id = :id")
    fun delete(id: String)

    @Query("DELETE FROM history")
    fun clear()

    /** «Borrar datos de navegación»: lo visitado desde esa fecha (0 = todo). */
    @Query("DELETE FROM history WHERE visitedAt >= :desde")
    fun borrarDesde(desde: Long)
}

/**
 * Registra una visita **sin duplicar**: si la dirección ya está en el historial
 * se actualiza su uso (fecha, título y contador de visitas) en la fila que ya
 * existe; si es nueva, se añade. Sustituye al alta a ciegas (una fila por carga)
 * que dejaba la base llena de direcciones repetidas. Llamar fuera del hilo
 * principal.
 */
fun HistoryDao.registrar(url: String, title: String, at: Long = System.currentTimeMillis()) {
    val previa = byUrl(url)
    if (previa == null) {
        insert(HistoryEntry(UUID.randomUUID().toString(), url, title, at))
    } else {
        touch(previa.id, at, title.ifBlank { previa.title })
    }
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY addedAt DESC")
    fun all(): List<DownloadEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entry: DownloadEntry)

    @Query("DELETE FROM downloads WHERE id = :id")
    fun delete(id: String)

    /** Quita sólo el registro de la lista (los archivos no se tocan) desde esa fecha. */
    @Query("DELETE FROM downloads WHERE addedAt >= :desde")
    fun borrarDesde(desde: Long)
}

/**
 * v2 → v3: el historial aprende a contar el uso. Sólo se **añade** una columna
 * (`ALTER TABLE`), así que el historial que ya tiene el usuario se conserva tal
 * cual: nada de migración destructiva. Las filas existentes valen 1 visita.
 */
private val MIGRACION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE history ADD COLUMN visits INTEGER NOT NULL DEFAULT 1")
    }
}

@Database(
    entities = [
        SearchEngine::class, Setting::class, Folder::class, Favorite::class,
        HistoryEntry::class, DownloadEntry::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AntenaDatabase : RoomDatabase() {
    abstract fun engineDao(): EngineDao
    abstract fun settingDao(): SettingDao
    abstract fun folderDao(): FolderDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        const val KEY_SEARCH_ENGINE = "search_engine"
        const val DEFAULT_SEARCH_ENGINE = "google"
        const val KEY_HOMEPAGE = "homepage"

        val DEFAULT_ENGINES = listOf(
            SearchEngine("google", "Google", "https://www.google.com/search?q=%s"),
            SearchEngine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s"),
            SearchEngine("bing", "Bing", "https://www.bing.com/search?q=%s"),
        )

        /** Semilla inicial de favoritos para que el dashboard no nazca vacío. */
        val DEFAULT_FAVORITES = listOf(
            Favorite("f-wiki", "Wikipedia", "https://es.wikipedia.org", null),
            Favorite("f-yt", "YouTube", "https://www.youtube.com", null),
            Favorite("f-reddit", "Reddit", "https://www.reddit.com", null),
            Favorite("f-gh", "GitHub", "https://github.com", null),
            Favorite("f-hn", "Hacker News", "https://news.ycombinator.com", null),
            Favorite("f-mdn", "MDN", "https://developer.mozilla.org", null),
        )

        @Volatile
        private var instance: AntenaDatabase? = null

        fun get(context: Context): AntenaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AntenaDatabase::class.java,
                    "antena.db",
                ).addMigrations(MIGRACION_2_3).fallbackToDestructiveMigration().build()
                    .also { instance = it }
            }

        /** Siembra motores y favoritos la primera vez. Llamar fuera del hilo principal. */
        fun seed(db: AntenaDatabase) {
            if (db.engineDao().count() == 0) {
                db.engineDao().upsertAll(DEFAULT_ENGINES)
            }
            if (db.favoriteDao().count() == 0) {
                DEFAULT_FAVORITES.forEach { db.favoriteDao().upsert(it) }
            }
        }
    }
}
