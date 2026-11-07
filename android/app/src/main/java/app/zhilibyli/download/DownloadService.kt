package app.zhilibyli.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.zhilibyli.NetworkMonitor
import app.zhilibyli.R
import app.zhilibyli.data.DownloadProgress
import app.zhilibyli.data.OfflineRepository
import app.zhilibyli.data.OfflineTrack
import app.zhilibyli.ui.OfflineActivity
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Скачивание офлайн-треков в foreground-сервисе.
 *
 * Файл сначала пишется в .part, и только после успешной проверки
 * переименовывается — оборванная загрузка не попадёт в библиотеку.
 * Cookie берём из WebView, поэтому авторизация (включая VK ID) работает
 * без повторного входа.
 */
class DownloadService : Service() {

    private val queue = LinkedBlockingQueue<DownloadRequest>()
    private val repo by lazy { OfflineRepository.get(this) }
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val workerActive = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    private val batchTotal = AtomicInteger(0)
    private val batchDone = AtomicInteger(0)
    private val batchFailed = AtomicInteger(0)
    private val batchSilent = AtomicInteger(0)
    private var lastNotifyAt = 0L

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelled.set(true)
                queue.clear()
                repo.downloads.value.keys.toList().forEach { repo.clearDownload(it) }
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_ENQUEUE -> {
                val request = intent.toRequest()
                if (request == null || request.url.isBlank() || request.trackId.isBlank()) {
                    // Сервис уже запущен как foreground — обязательно показываем
                    // уведомление и сразу останавливаемся, иначе система убьёт процесс.
                    startForeground(NOTIFICATION_PROGRESS, buildNotification("Ошибка запроса", 0))
                    sendResult(request?.trackId.orEmpty(), false, "Некорректная ссылка на трек", null)
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                cancelled.set(false)
                queue.offer(request)
                batchTotal.incrementAndGet()
                if (request.silent) batchSilent.incrementAndGet()
                startForeground(NOTIFICATION_PROGRESS, buildNotification("Подготовка…", 0))
                repo.updateProgress(
                    DownloadProgress(request.trackId, request.title, 0, 0, 0),
                )
                pump()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cancelled.set(true)
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun pump() {
        if (workerActive.get()) return
        workerActive.set(true)
        worker.execute {
            try {
                while (!cancelled.get()) {
                    val request = queue.poll() ?: break
                    process(request)
                }
            } finally {
                workerActive.set(false)
                if (queue.isNotEmpty() && !cancelled.get()) {
                    pump()
                } else {
                    finishBatch()
                }
            }
        }
    }

    private fun finishBatch() {
        val done = batchDone.getAndSet(0)
        val failed = batchFailed.getAndSet(0)
        val total = batchTotal.getAndSet(0)
        val silent = batchSilent.getAndSet(0)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (total > 0) {
            if ((total - silent) > 0 || done > 0 || failed > 0) {
                val text = when {
                    done > 0 && failed == 0 -> "Сохранено в память: $done"
                    done > 0 -> "Сохранено: $done, ошибок: $failed"
                    failed > 0 && done == 0 -> "Не удалось сохранить треки"
                    done == 0 && failed == 0 -> "Сохранено в память"
                    else -> "Сохранено: $done, ошибок: $failed"
                }
                notify(NOTIFICATION_DONE, buildDoneNotification(text))
            }
        }
        stopSelf()
    }

    private fun process(request: DownloadRequest) {
        val target = repo.fileFor(request.trackId)
        val temp = File(target.parentFile, "${target.name}.part")
        var connection: HttpURLConnection? = null
        var success = false
        try {
            if (NetworkUnavailableGuard.isOffline(this)) {
                throw DownloadException("Нет интернета")
            }

            connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "ZhiliByliApp/1.0")
                setRequestProperty("Accept", "audio/*,*/*;q=0.8")
                request.cookie
                    ?.takeIf { it.isNotBlank() }
                    ?.let { setRequestProperty("Cookie", it) }
            }

            val code = connection.responseCode
            if (code !in 200..299) {
                val message = when (code) {
                    401, 403 -> "Нужно войти в аккаунт, чтобы сохранять музыку"
                    404 -> "Трек временно недоступен"
                    429 -> "Слишком много загрузок — попробуйте позже"
                    else -> "Источник ответил ошибкой $code"
                }
                throw DownloadException(message)
            }

            val contentType = connection.contentType.orEmpty()
            if (contentType.contains("json", ignoreCase = true) ||
                contentType.contains("text/", ignoreCase = true)
            ) {
                throw DownloadException("Сервер вернул не аудио")
            }

            val total = connection.contentLengthLong
            temp.parentFile?.mkdirs()
            if (temp.exists()) temp.delete()

            var written = 0L
            var lastUiAt = 0L
            connection.inputStream.use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (cancelled.get()) throw DownloadException("Скачивание отменено")
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        val now = System.currentTimeMillis()
                        if (now - lastUiAt >= 250) {
                            lastUiAt = now
                            publishProgress(request, written, total)
                        }
                    }
                    output.flush()
                    output.fd.sync()
                }
            }

            if (written <= 0) throw DownloadException("Пустой файл")
            if (cancelled.get()) throw DownloadException("Скачивание отменено")

            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) throw DownloadException("Не удалось сохранить файл")

            val coverPath = downloadCover(request)
                ?: repo.get(request.trackId)?.coverPath?.takeIf { File(it).isFile }

            val track = OfflineTrack(
                trackId = request.trackId,
                title = request.title,
                artist = request.artist,
                coverUrl = request.coverUrl,
                coverPath = coverPath,
                durationSec = request.durationSec,
                fileName = target.name,
                sizeBytes = target.length(),
                source = request.source,
                addedAt = System.currentTimeMillis(),
            )
            repo.onDownloaded(track)
            publishProgress(request, target.length(), target.length())
            success = true
            sendResult(request.trackId, true, null, target.absolutePath)
        } catch (error: Exception) {
            temp.takeIf { it.exists() }?.delete()
            val message = when (error) {
                is DownloadException -> error.message ?: "Ошибка скачивания"
                is java.net.UnknownHostException -> "Нет интернета"
                is java.net.SocketTimeoutException -> "Источник не отвечает"
                else -> error.message ?: "Ошибка скачивания"
            }
            repo.clearDownload(request.trackId)
            sendResult(request.trackId, false, message, null)
        } finally {
            connection?.disconnect()
            batchDone.incrementAndGet()
            if (!success) batchFailed.incrementAndGet()
            publishOverall(request)
        }
    }

    private fun publishProgress(request: DownloadRequest, bytes: Long, total: Long) {
        val percent = if (total > 0) ((bytes * 100) / total).toInt() else -1
        repo.updateProgress(
            DownloadProgress(request.trackId, request.title, percent, bytes, total),
        )
        val now = System.currentTimeMillis()
        if (now - lastNotifyAt >= 500 || percent == 100) {
            lastNotifyAt = now
            notify(NOTIFICATION_PROGRESS, buildNotification(request.title, percent))
        }
        sendProgress(request.trackId, request.title, percent, bytes, total)
    }

    private fun publishOverall(request: DownloadRequest) {
        val total = batchTotal.get().coerceAtLeast(1)
        val done = batchDone.get()
        val percent = (done * 100 / total).coerceIn(0, 100)
        notify(
            NOTIFICATION_PROGRESS,
            buildNotification(
                "Готово $done из $total",
                percent,
            ),
        )
        repo.clearDownload(request.trackId)
    }

    private fun downloadCover(request: DownloadRequest): String? {
        val coverUrl = request.coverUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: return null
        val target = repo.coverFileFor(request.trackId)
        val temp = File(target.parentFile, "${target.name}.part")
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(coverUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "ZhiliByliApp/1.0")
                setRequestProperty("Accept", "image/*")
                (request.coverCookie ?: request.cookie)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { setRequestProperty("Cookie", it) }
            }
            if (connection.responseCode !in 200..299) return null
            val length = connection.contentLengthLong
            if (length == 0L) return null
            temp.parentFile?.mkdirs()
            connection.inputStream.use { input ->
                FileOutputStream(temp).use { output -> input.copyTo(output) }
            }
            if (temp.length() <= 0) {
                temp.delete()
                return null
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.delete()
                return null
            }
            target.absolutePath
        } catch (_: Exception) {
            temp.delete()
            null
        } finally {
            connection?.disconnect()
        }
    }

    // ------------------------------------------------------------------
    //  События для WebView
    // ------------------------------------------------------------------

    private fun sendProgress(
        trackId: String,
        title: String,
        percent: Int,
        bytes: Long,
        total: Long,
    ) {
        sendBroadcast(
            Intent(ACTION_EVENT).setPackage(packageName).apply {
                putExtra(EXTRA_TYPE, "progress")
                putExtra(EXTRA_TRACK_ID, trackId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_PERCENT, percent)
                putExtra(EXTRA_BYTES, bytes)
                putExtra(EXTRA_TOTAL, total)
            },
        )
    }

    private fun sendResult(trackId: String, ok: Boolean, error: String?, path: String?) {
        sendBroadcast(
            Intent(ACTION_EVENT).setPackage(packageName).apply {
                putExtra(EXTRA_TYPE, "result")
                putExtra(EXTRA_TRACK_ID, trackId)
                putExtra(EXTRA_OK, ok)
                putExtra(EXTRA_ERROR, error)
                putExtra(EXTRA_PATH, path)
            },
        )
    }

    // ------------------------------------------------------------------
    //  Уведомления
    // ------------------------------------------------------------------

    private fun buildNotification(title: String, percent: Int): android.app.Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, OfflineActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Офлайн-музыка")
            .setContentText(title)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply {
                if (percent >= 0) setProgress(100, percent.coerceIn(0, 100), false)
                else setProgress(0, 0, true)
            }
            .build()
    }

    private fun buildDoneNotification(text: String): android.app.Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, OfflineActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Офлайн-музыка")
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun notify(id: Int, notification: android.app.Notification) {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // Пользователь не разрешил уведомления — скачивание всё равно работает.
        }
    }

    // ------------------------------------------------------------------
    //  Разбор запроса / companion
    // ------------------------------------------------------------------

    private fun Intent.toRequest(): DownloadRequest? {
        val trackId = getStringExtra(EXTRA_TRACK_ID) ?: return null
        val url = getStringExtra(EXTRA_URL) ?: return null
        return DownloadRequest(
            trackId = trackId,
            url = url,
            title = getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Сохранённый трек" },
            artist = getStringExtra(EXTRA_ARTIST).orEmpty(),
            coverUrl = getStringExtra(EXTRA_COVER),
            durationSec = getIntExtra(EXTRA_DURATION, 0),
            source = getStringExtra(EXTRA_SOURCE).orEmpty().ifBlank { "manual" },
            silent = getBooleanExtra(EXTRA_SILENT, false),
            cookie = getStringExtra(EXTRA_COOKIE),
            coverCookie = getStringExtra(EXTRA_COVER_COOKIE),
        )
    }

    companion object {
        const val ACTION_ENQUEUE = "app.zhilibyli.download.ENQUEUE"
        const val ACTION_CANCEL = "app.zhilibyli.download.CANCEL"
        const val ACTION_EVENT = "app.zhilibyli.download.EVENT"

        const val EXTRA_TRACK_ID = "track_id"
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_COVER = "cover"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_SILENT = "silent"
        const val EXTRA_COOKIE = "cookie"
        const val EXTRA_COVER_COOKIE = "cover_cookie"

        const val EXTRA_TYPE = "type"
        const val EXTRA_PERCENT = "percent"
        const val EXTRA_BYTES = "bytes"
        const val EXTRA_TOTAL = "total"
        const val EXTRA_OK = "ok"
        const val EXTRA_ERROR = "error"
        const val EXTRA_PATH = "path"

        private const val CHANNEL_DOWNLOADS = "offline_downloads"
        private const val NOTIFICATION_PROGRESS = 1001
        private const val NOTIFICATION_DONE = 1002

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_DOWNLOADS,
                "Скачивание музыки",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Сохранение треков для прослушивания без интернета"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        fun enqueue(context: Context, request: DownloadRequest) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_ENQUEUE
                putExtra(EXTRA_TRACK_ID, request.trackId)
                putExtra(EXTRA_URL, request.url)
                putExtra(EXTRA_TITLE, request.title)
                putExtra(EXTRA_ARTIST, request.artist)
                putExtra(EXTRA_COVER, request.coverUrl)
                putExtra(EXTRA_DURATION, request.durationSec)
                putExtra(EXTRA_SOURCE, request.source)
                putExtra(EXTRA_SILENT, request.silent)
                putExtra(EXTRA_COOKIE, request.cookie)
                putExtra(EXTRA_COVER_COOKIE, request.coverCookie)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancelAll(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL
            }
            runCatching { context.startService(intent) }
        }
    }
}

private class DownloadException(message: String) : Exception(message)

/** Отдельный объект, чтобы сервис не зависел от Activity. */
private object NetworkUnavailableGuard {
    fun isOffline(context: Context): Boolean = !NetworkMonitor.isOnline()
}
