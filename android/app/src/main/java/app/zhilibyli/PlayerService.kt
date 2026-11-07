package app.zhilibyli

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Фоновый сервис воспроизведения (Media3 MediaSessionService):
 * держит плеер живым, показывает системное медиа-уведомление
 * и продолжает играть при свёрнутом приложении и без интернета
 * (файлы из filesDir/tracks).
 *
 * Сам ExoPlayer создаётся в [PlayerCore] и общий для всего процесса,
 * поэтому кнопки в интерфейсе управляют ровно тем плеером, который звучит.
 */
class PlayerService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val player: ExoPlayer = PlayerCore.get(this)

        // Тап по уведомлению возвращает в основное приложение: там привычный
        // плеер с очередью и навигацией. Раньше открывался отдельный
        // PlayerActivity — урезанный экран без навигации.
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        // Плеер не освобождаем: он принадлежит процессу и может продолжать
        // использоваться экранами после пересоздания сервиса.
        session?.release()
        session = null
        super.onDestroy()
    }
}
