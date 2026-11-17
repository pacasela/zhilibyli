package app.zhilibyli

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.zhilibyli.ui.PlayerActivity

/**
 * Медиа-сервис — плеер в фоне + уведомление в шторке с кнопками prev/play-pause/next.
 */
class PlayerService : MediaSessionService() {

    private var session: MediaSession? = null

    companion object {
        private const val CHANNEL_ID = "zhilibyli_player"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        val player = PlayerCore.get(this)

        // Notification channel (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            (getSystemService(NotificationManager::class.java) as NotificationManager)
                .createNotificationChannel(channel)
        }

        // MediaSession — привязан к Player, автоматически показывает MediaNotification
        session = MediaSession.Builder(this, player)
            .setSessionActivity(PendingIntent.getActivity(
                this, 0,
                Intent(this, PlayerActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            ))
            .build()

        // Добавляем сессию в сервис — MediaSessionService сам создаёт медиа-уведомление
        addSession(session!!)
    }

    override fun onDestroy() {
        session?.run { release() }
        session = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
}
