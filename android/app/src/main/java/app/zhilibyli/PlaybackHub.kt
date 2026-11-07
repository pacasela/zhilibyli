package app.zhilibyli

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.zhilibyli.data.OfflineRepository
import app.zhilibyli.data.OfflineTrack
import java.io.File

/**
 * Управление воспроизведением на всё приложение.
 *
 * Все экраны (WebView-мост, полноэкранный плеер, офлайн-библиотека) работают
 * с одним и тем же ExoPlayer из [PlayerCore]. Раньше здесь был MediaController:
 * команды уходили в сервис асинхронно и могли «не доезжать» (пауза, seek,
 * следующий трек не срабатывали). Прямой доступ к плееру эту проблему снимает,
 * а сервис по-прежнему держит MediaSession для уведомления и наушников.
 */
object PlaybackHub {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun get(): ExoPlayer? = PlayerCore.peek()

    /**
     * Запускает действие с плеером. Вызывать можно из любого потока —
     * действие всегда выполнится на главном потоке с уже созданным плеером.
     */
    fun withController(context: Context, action: (ExoPlayer) -> Unit) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            val appContext = context.applicationContext
            mainHandler.post { withController(appContext, action) }
            return
        }
        ensureService(context)
        action(PlayerCore.get(context))
    }

    /** Поднимает сервис, чтобы играло в фоне и было медиа-уведомление. */
    private fun ensureService(context: Context) {
        runCatching {
            context.applicationContext.startService(
                Intent(context.applicationContext, PlayerService::class.java),
            )
        }
    }

    /** Ставит в очередь сохранённые файлы — воспроизведение полностью локальное. */
    fun playOffline(context: Context, tracks: List<OfflineTrack>, startIndex: Int) {
        if (tracks.isEmpty()) return
        val repo = OfflineRepository.get(context)
        withController(context) { player ->
            val items = tracks.mapNotNull { track ->
                // Имя файла берём из метаданных: старые копии могли называться иначе.
                val path = repo.pathOf(track.trackId) ?: return@mapNotNull null
                val file = File(path)
                if (!file.isFile) return@mapNotNull null
                val artwork = artworkFor(context, track.trackId)
                MediaItem.Builder()
                    .setUri(Uri.fromFile(file))
                    .setMediaId(track.trackId)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.displayTitle)
                            .setArtist(track.displayArtist)
                            .setArtworkUri(artwork)
                            .build(),
                    )
                    .build()
            }
            if (items.isEmpty()) return@withController
            // Индекс мог сдвинуться, если часть файлов уже удалена — ищем по trackId.
            val startId = tracks.getOrNull(startIndex)?.trackId
            val safeIndex = items.indexOfFirst { it.mediaId == startId }.takeIf { it >= 0 } ?: 0
            player.setMediaItems(items, safeIndex, 0L)
            player.prepare()
            player.play()
        }
    }

    /**
     * Очередь из WebView: url может быть http(s), file:// или абсолютным путём.
     *
     * Для каждого mediaId, у которого уже есть скачанный файл, принудительно
     * подставляем локальный Uri и локальную обложку. Это главная защита от
     * error_code_io_network_connection_failed: даже если WebView отправил
     * старую http-ссылку, ExoPlayer в офлайне получит только файл.
     */
    fun playQueue(context: Context, items: List<MediaItem>, startIndex: Int): Boolean {
        if (items.isEmpty()) return false
        val online = NetworkMonitor.hasActiveNetwork()
        val startId = items.getOrNull(startIndex)?.mediaId

        val playable = items.mapNotNull { item ->
            localMediaItem(context, item)
                ?: item.takeIf { online || !it.isNetworkSource() }
        }
        if (playable.isEmpty()) return false

        val safeIndex = playable
            .indexOfFirst { it.mediaId == startId }
            .takeIf { it >= 0 }
            ?: 0

        withController(context) { player ->
            player.setMediaItems(playable, safeIndex, 0L)
            player.prepare()
            player.play()
        }
        return true
    }

    /**
     * Возвращает копию MediaItem с локальным файлом, если трек скачан.
     * null — локальной копии нет.
     */
    fun localMediaItem(context: Context, item: MediaItem): MediaItem? {
        val repo = OfflineRepository.get(context)
        val path = repo.pathOf(item.mediaId) ?: return null
        val file = File(path)
        if (!file.isFile || file.length() <= 0L) return null

        val metadata = item.mediaMetadata.buildUpon()
            .setArtworkUri(artworkFor(context, item.mediaId))
            .build()
        return item.buildUpon()
            .setUri(Uri.fromFile(file))
            .setMediaMetadata(metadata)
            .build()
    }

    /**
     * Аварийное восстановление, если ExoPlayer всё-таки успел открыть
     * сетевой источник и получил network error. Возвращает true, если
     * текущий трек удалось перезапустить из локального файла.
     */
    fun retryCurrentFromLocal(context: Context, player: Player): Boolean {
        val current = player.currentMediaItem ?: return false
        val scheme = current.localConfiguration?.uri?.scheme
        if (scheme == "file" || scheme == "content") return false
        val local = localMediaItem(context, current) ?: return false
        val position = player.currentPosition.coerceAtLeast(0L)
        player.setMediaItem(local, position)
        player.prepare()
        player.play()
        return true
    }

    private fun artworkFor(context: Context, trackId: String): Uri? {
        val repo = OfflineRepository.get(context)
        val track = repo.get(trackId)
        val file = track?.coverPath
            ?.let(::File)
            ?.takeIf { it.isFile }
            ?: repo.coverFileFor(trackId).takeIf { it.isFile }
        return file?.let(Uri::fromFile)
    }

    private fun MediaItem.isNetworkSource(): Boolean {
        val scheme = localConfiguration?.uri?.scheme
        return scheme == "http" || scheme == "https"
    }

    /** Пауза + очистка очереди (свайп мини-плеера «стоп»). */
    fun stop() {
        PlayerCore.peek()?.let {
            it.stop()
            it.clearMediaItems()
        }
    }

    /** Полное освобождение плеера (используется только при выходе из приложения). */
    fun release() {
        PlayerCore.release()
    }
}
