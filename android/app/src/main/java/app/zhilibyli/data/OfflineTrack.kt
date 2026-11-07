package app.zhilibyli.data

/**
 * Трек, который уже лежит в памяти телефона и играет без интернета.
 * Метаданные храним отдельно от файла, чтобы офлайн-библиотеку можно было
 * показать без сети — ровно так работает «Моя волна» в Яндекс Музыке.
 */
data class OfflineTrack(
    val trackId: String,
    val title: String,
    val artist: String,
    val coverUrl: String?,
    val coverPath: String?,
    val durationSec: Int,
    val fileName: String,
    val sizeBytes: Long,
    /** manual — скачал человек, like — кеш избранного, imported — найден на диске. */
    val source: String,
    val addedAt: Long,
) {
    val displayTitle: String
        get() = title.ifBlank { "Сохранённый трек" }

    val displayArtist: String
        get() = artist.ifBlank { "Неизвестный исполнитель" }

    val durationMs: Long
        get() = durationSec.coerceAtLeast(0) * 1000L
}

data class DownloadProgress(
    val trackId: String,
    val title: String,
    /** -1 — размер неизвестен, показываем бесконечный прогресс. */
    val percent: Int,
    val bytes: Long,
    val total: Long,
)
