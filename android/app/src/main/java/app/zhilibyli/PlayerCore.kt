package app.zhilibyli

import android.content.Context
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer

/**
 * Единственный ExoPlayer на процесс.
 *
 * Раньше управление шло через асинхронный MediaController: кнопки паузы,
 * промотки и переключения треков могли «теряться», если контроллер ещё
 * не подключился или уже отвалился от сессии. Теперь и сервис, и экраны
 * (WebView-мост, полноэкранный плеер, офлайн-список) держат один и тот же
 * экземпляр плеера напрямую — команды применяются синхронно и всегда
 * попадают в реально играющий плеер. MediaSession остаётся только ради
 * системного уведомления, экрана блокировки и наушников.
 */
object PlayerCore {

    @Volatile
    private var player: ExoPlayer? = null

    /** Только главный поток: ExoPlayer не любит обращения из разных потоков. */
    fun get(context: Context): ExoPlayer {
        player?.let { return it }
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "PlayerCore.get() must be called from the main thread"
        }

        val created = ExoPlayer.Builder(context.applicationContext)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player = created
        return created
    }

    fun peek(): ExoPlayer? = player

    /**
     * Плеер намеренно живёт весь процесс: сервис может быть уничтожен
     * системой при паузе, а экраны продолжают обращаться к тому же плееру.
     */
    fun release() {
        player?.release()
        player = null
    }
}
