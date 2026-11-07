package app.zhilibyli.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Небольшая SQLite-база для офлайн-библиотеки.
 * Room здесь не нужен: одна таблица, никаких миграций кроме version.
 */
class OfflineDb(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                track_id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL DEFAULT '',
                artist TEXT NOT NULL DEFAULT '',
                cover_url TEXT,
                cover_path TEXT,
                duration_sec INTEGER NOT NULL DEFAULT 0,
                file_name TEXT NOT NULL,
                size_bytes INTEGER NOT NULL DEFAULT 0,
                source TEXT NOT NULL DEFAULT 'manual',
                added_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_${TABLE}_added ON $TABLE (added_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Метаданные можно восстановить сканированием файлов — терять аудио нельзя.
        if (oldVersion < 2) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE")
            onCreate(db)
        }
    }

    @Synchronized
    fun all(): List<OfflineTrack> {
        val result = ArrayList<OfflineTrack>()
        readableDatabase.query(
            TABLE,
            null,
            null,
            null,
            null,
            null,
            "added_at DESC",
        ).use { cursor ->
            while (cursor.moveToNext()) result.add(cursor.toTrack())
        }
        return result
    }

    @Synchronized
    fun get(trackId: String): OfflineTrack? {
        readableDatabase.query(
            TABLE,
            null,
            "track_id = ?",
            arrayOf(trackId),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toTrack() else null
        }
    }

    @Synchronized
    fun upsert(track: OfflineTrack) {
        writableDatabase.insertWithOnConflict(
            TABLE,
            null,
            track.toValues(),
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    @Synchronized
    fun delete(trackId: String) {
        writableDatabase.delete(TABLE, "track_id = ?", arrayOf(trackId))
    }

    @Synchronized
    fun clear() {
        writableDatabase.delete(TABLE, null, null)
    }

    private fun Cursor.toTrack(): OfflineTrack = OfflineTrack(
        trackId = getString(getColumnIndexOrThrow("track_id")),
        title = getString(getColumnIndexOrThrow("title")),
        artist = getString(getColumnIndexOrThrow("artist")),
        coverUrl = getStringOrNull("cover_url"),
        coverPath = getStringOrNull("cover_path"),
        durationSec = getInt(getColumnIndexOrThrow("duration_sec")),
        fileName = getString(getColumnIndexOrThrow("file_name")),
        sizeBytes = getLong(getColumnIndexOrThrow("size_bytes")),
        source = getString(getColumnIndexOrThrow("source")),
        addedAt = getLong(getColumnIndexOrThrow("added_at")),
    )

    private fun Cursor.getStringOrNull(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    private fun OfflineTrack.toValues(): ContentValues = ContentValues().apply {
        put("track_id", trackId)
        put("title", title)
        put("artist", artist)
        put("cover_url", coverUrl)
        put("cover_path", coverPath)
        put("duration_sec", durationSec)
        put("file_name", fileName)
        put("size_bytes", sizeBytes)
        put("source", source)
        put("added_at", addedAt)
    }

    companion object {
        private const val DB_NAME = "zhilibyli_offline.db"
        private const val VERSION = 1
        private const val TABLE = "offline_tracks"
    }
}
