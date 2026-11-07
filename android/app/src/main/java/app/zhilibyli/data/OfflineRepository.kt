package app.zhilibyli.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Единое хранилище офлайн-музыки: файлы + SQLite-метаданные.
 *
 * Аудио лежит в filesDir/tracks, обложки — в filesDir/covers.
 * Это приватное хранилище приложения: файлы переживают очистку кеша,
 * не видны другим приложениям и доступны без интернета.
 */
class OfflineRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val db = OfflineDb(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _tracks = MutableStateFlow<List<OfflineTrack>>(emptyList())
    val tracks: StateFlow<List<OfflineTrack>> = _tracks.asStateFlow()

    private val _downloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadProgress>> = _downloads.asStateFlow()

    init {
        // Синхронно подхватываем файлы до того, как WebView спросит список:
        // иначе первый listOffline() мог вернуть пустоту.
        syncWithDisk()
        refreshNow()
    }

    // ------------------------------------------------------------------
    //  Файлы
    // ------------------------------------------------------------------

    fun tracksDir(): File = File(appContext.filesDir, "tracks").apply { if (!exists()) mkdirs() }

    fun coversDir(): File = File(appContext.filesDir, "covers").apply { if (!exists()) mkdirs() }

    fun fileFor(trackId: String): File = File(tracksDir(), "${safeName(trackId)}.mp3")

    /** Файл по метаданным: имя могло быть задано старым приложением. */
    fun fileFor(item: OfflineTrack): File = File(tracksDir(), item.fileName)

    fun coverFileFor(trackId: String): File = File(coversDir(), "${safeName(trackId)}.cover")

    fun pathOf(trackId: String): String? {
        if (trackId.isBlank()) return null
        val item = _tracks.value.firstOrNull { it.trackId == trackId }
        val file = if (item != null) fileFor(item) else fileFor(trackId)
        return if (file.isFile && file.length() > 0) file.absolutePath else null
    }

    fun isOffline(trackId: String): Boolean = pathOf(trackId) != null

    // ------------------------------------------------------------------
    //  Чтение
    // ------------------------------------------------------------------

    fun snapshot(): List<OfflineTrack> = _tracks.value

    fun totalBytes(list: List<OfflineTrack> = _tracks.value): Long = list.sumOf { it.sizeBytes }

    fun get(trackId: String): OfflineTrack? =
        _tracks.value.firstOrNull { it.trackId == trackId } ?: db.get(trackId)

    // ------------------------------------------------------------------
    //  Запись
    // ------------------------------------------------------------------

    fun upsert(track: OfflineTrack) {
        db.upsert(track)
        refreshNow()
    }

    /** Сохраняем только что скачанный трек. */
    fun onDownloaded(track: OfflineTrack) {
        db.upsert(track)
        refreshNow()
        clearDownload(track.trackId)
    }

    fun remove(trackId: String): Boolean {
        val item = db.get(trackId)
        val candidates = listOfNotNull(
            item?.let { File(tracksDir(), it.fileName) },
            fileFor(trackId),
            item?.coverPath?.let(::File),
            coverFileFor(trackId),
        )
        var deleted = false
        candidates.distinct().forEach { file ->
            if (file.exists() && file.delete()) deleted = true
        }
        db.delete(trackId)
        clearDownload(trackId)
        refreshNow()
        return deleted || item != null
    }

    fun removeAll() {
        _tracks.value.forEach { item ->
            File(tracksDir(), item.fileName).takeIf { it.exists() }?.delete()
            item.coverPath?.let { File(it).takeIf { file -> file.exists() }?.delete() }
        }
        tracksDir().listFiles()?.forEach { it.delete() }
        coversDir().listFiles()?.forEach { it.delete() }
        db.clear()
        _downloads.value = emptyMap()
        refreshNow()
    }

    fun refreshNow() {
        _tracks.value = db.all()
    }

    fun refreshAsync() {
        scope.launch { refreshNow() }
    }

    // ------------------------------------------------------------------
    //  Прогресс скачивания (для экрана офлайна)
    // ------------------------------------------------------------------

    fun updateProgress(progress: DownloadProgress) {
        _downloads.update { it + (progress.trackId to progress) }
    }

    fun clearDownload(trackId: String) {
        _downloads.update { it - trackId }
    }

    /**
     * Подхватываем файлы, скачанные предыдущими версиями приложения
     * (они лежали в filesDir/tracks без метаданных). Ничего не теряем.
     */
    private fun syncWithDisk() {
        val dir = tracksDir()
        val known = db.all().associateBy { it.trackId }
        var changed = false

        val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".mp3", ignoreCase = true) }
            ?: emptyArray()
        for (file in files) {
            val trackId = file.nameWithoutExtension
            if (trackId.isBlank() || file.length() <= 0) continue
            val existing = known[trackId]
            if (existing == null) {
                db.upsert(
                    OfflineTrack(
                        trackId = trackId,
                        title = "Сохранённый трек",
                        artist = "",
                        coverUrl = null,
                        coverPath = null,
                        durationSec = 0,
                        fileName = file.name,
                        sizeBytes = file.length(),
                        source = "imported",
                        addedAt = file.lastModified(),
                    ),
                )
                changed = true
            } else if (existing.fileName != file.name || existing.sizeBytes != file.length()) {
                db.upsert(existing.copy(fileName = file.name, sizeBytes = file.length()))
                changed = true
            }
        }

        for (item in known.values) {
            val file = File(dir, item.fileName)
            if (!file.isFile || file.length() == 0L) {
                db.delete(item.trackId)
                changed = true
            }
        }

        // Недокачанные файлы после падения приложения не нужны.
        dir.listFiles { file -> file.name.endsWith(".part") }?.forEach { it.delete() }

        if (changed) _tracks.value = db.all()
    }

    companion object {
        @Volatile
        private var instance: OfflineRepository? = null

        fun init(context: Context) {
            get(context)
        }

        fun get(context: Context): OfflineRepository =
            instance ?: synchronized(this) {
                instance ?: OfflineRepository(context).also { instance = it }
            }

        fun safeName(raw: String): String =
            raw.replace(Regex("[^A-Za-z0-9_.-]"), "_").ifBlank { "track" }
    }
}
