package app.zhilibyli.download

/** Запрос на сохранение трека в память устройства. */
data class DownloadRequest(
    val trackId: String,
    val url: String,
    val title: String,
    val artist: String,
    val coverUrl: String?,
    val durationSec: Int,
    val source: String,
    /** Тихая загрузка (авто-кеш по лайку): без уведомления о завершении. */
    val silent: Boolean = false,
    /** Cookie берём из WebView на UI-потоке, чтобы не трогать WebView из сервиса. */
    val cookie: String? = null,
    val coverCookie: String? = null,
)
