package app.zhilibyli

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaStyle
import android.media.PlaybackState
import android.os.Build
import android.os.Parcelable
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.media2.ExoPlayer
import androidx.media2.player.NotificationChannelWrapper
import androidx.media2.player.PlayerNotificationBuilder
import androidx.media2.player.PlayerNotificationChannel
import androidx.media2.player.PlayerNotificationManager
import androidx.media2.player.PlayerNotificationChannelConfig
import androidx.media2.player.PlayerNotificationManager
import androidx.media2.player.PlayerNotificationManager
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Player.Listener
import com.google.android.exoplayer2.Player.ListenerEvent
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.FileDataSource
import com.google.android.exoplayer2.upstream.TransferListener
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util
import java.io.File
import java.util.*
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class PlayerService : FlutterActivity() {

    private var player: ExoPlayer? = null
    private var channelConfig: PlayerNotificationChannelConfig? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Flutter engine
        val flutterEngine = FlutterEngine(this)
        // The plugin registration happens automatically

        // Setup media session
        setupMediaSession()
    }

    private fun setupMediaSession() {
        // Create notification channel for media notifications
        val channelConfig = PlayerNotificationChannelConfig.Builder()
            .setChannelName("Media Channel")
            .setChannelDescription("Media notifications")
            .setImportance(android.app.NotificationManager.IMPORTANCE_HIGH)
            .setShowBadge(true)
            .build()

        player = ExoPlayer.Builder(this)
            .setNotificationChannelConfig(channelConfig)
            .build()

        // Add listener for player state changes
        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: PlaybackState) {
                // Update notification when playback state changes
                updateNotificationState(state)
            }
        })
    }

    private fun updateNotificationState(state: PlaybackState) {
        player?.notificationManager?.setPlaybackState(state)
    }

    fun onMediaSessionCommand(command: Int) {
        when (command) {
            PlaybackState.COMMAND_PLAY -> player?.play()
            PlaybackState.COMMAND_PAUSE -> player?.pause()
            PlaybackState.COMMAND_STOP -> player?.stop()
            PlaybackState.COMMAND_TOGGLE_PLAY_PAUSE -> player?.toggle()
            else -> {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }
}