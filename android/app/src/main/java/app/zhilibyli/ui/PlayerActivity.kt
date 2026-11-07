package app.zhilibyli.ui

import android.content.Intent
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.zhilibyli.MainActivity
import app.zhilibyli.PlaybackHub
import app.zhilibyli.R
import app.zhilibyli.data.OfflineRepository
import app.zhilibyli.util.formatDuration
import coil.load
import java.io.File

/**
 * Полноэкранный нативный плеер. Управляет тем же ExoPlayer, что и WebView
 * и офлайн-список, поэтому музыка не сбивается при переходах между экранами.
 */
class PlayerActivity : AppCompatActivity() {

    private lateinit var repo: OfflineRepository
    private lateinit var backdropImage: ImageView
    private lateinit var coverImage: ImageView
    private lateinit var titleText: TextView
    private lateinit var artistText: TextView
    private lateinit var offlineBadge: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var positionText: TextView
    private lateinit var durationText: TextView
    private lateinit var playPauseButton: ImageButton
    private lateinit var queueList: LinearLayout
    private lateinit var queueEmpty: TextView

    private var controller: ExoPlayer? = null
    private var listening = false
    private var seeking = false
    private var tickerRunning = false
    private var artworkKey: String? = null
    /** (количество треков, текущий индекс) — чтобы не пересобирать очередь зря. */
    private var queueSignature: Pair<Int, Int>? = null

    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            val player = controller ?: return
            renderProgress(player)
            if (player.isPlaying) {
                handler.postDelayed(this, 500)
            } else {
                tickerRunning = false
            }
        }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            render(player)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            renderCurrent()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            renderCurrent()
            if (isPlaying) startTicker() else stopTicker()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            renderCurrent()
        }

        override fun onPlayerError(error: PlaybackException) {
            renderCurrent()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        repo = OfflineRepository.get(this)
        backdropImage = findViewById(R.id.playerBackdrop)
        coverImage = findViewById(R.id.coverImage)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val radius = resources.displayMetrics.density * 28f
            backdropImage.setRenderEffect(
                RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP),
            )
        } else {
            // На старых версиях RenderEffect нет: приглушаем обложку сильнее,
            // чтобы она не спорила с текстом.
            backdropImage.alpha = 0.36f
        }

        titleText = findViewById(R.id.titleText)
        artistText = findViewById(R.id.artistText)
        offlineBadge = findViewById(R.id.offlineBadge)
        seekBar = findViewById(R.id.seekBar)
        positionText = findViewById(R.id.positionText)
        durationText = findViewById(R.id.durationText)
        playPauseButton = findViewById(R.id.playPauseButton)
        queueList = findViewById(R.id.queueList)
        queueEmpty = findViewById(R.id.queueEmpty)

        findViewById<ImageButton>(R.id.closeButton).setOnClickListener { finish() }
        findViewById<View>(R.id.openCatalogButton).setOnClickListener { openCatalog() }

        playPauseButton.setOnClickListener {
            PlaybackHub.withController(this@PlayerActivity) { player ->
                controller = player
                if (player.playbackState == Player.STATE_ENDED) {
                    player.seekTo(0)
                    player.playWhenReady = true
                } else {
                    // Инвертируем намерение, а не isPlaying: во время буферизации
                    // isPlaying уже false и старая логика не давала поставить паузу.
                    player.playWhenReady = !player.playWhenReady
                }
                render(player)
                if (player.playWhenReady) startTicker() else stopTicker()
            }
        }

        findViewById<ImageButton>(R.id.prevButton).setOnClickListener {
            PlaybackHub.withController(this@PlayerActivity) { player ->
                controller = player
                if (player.currentPosition > 3_000) {
                    player.seekTo(0)
                } else if (player.hasPreviousMediaItem()) {
                    player.seekToPreviousMediaItem()
                } else {
                    player.seekTo(0)
                }
                render(player)
                startTicker()
            }
        }

        findViewById<ImageButton>(R.id.nextButton).setOnClickListener {
            PlaybackHub.withController(this@PlayerActivity) { player ->
                controller = player
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                } else {
                    player.seekTo(0)
                }
                render(player)
                startTicker()
            }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                positionText.text = formatDuration(progress / 1000)
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {
                seeking = true
            }

            override fun onStopTrackingTouch(bar: SeekBar?) {
                seeking = false
                PlaybackHub.withController(this@PlayerActivity) { player ->
                    val position = (bar?.progress ?: 0).toLong().coerceAtLeast(0L)
                    player.seekTo(position)
                    controller = player
                    renderProgress(player)
                    if (player.isPlaying) startTicker()
                }
            }
        })
    }

    override fun onStart() {
        super.onStart()
        PlaybackHub.withController(this) { player -> attach(player) }
    }

    override fun onStop() {
        controller?.removeListener(listener)
        listening = false
        stopTicker()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        controller?.removeListener(listener)
        controller = null
        super.onDestroy()
    }

    private fun attach(player: ExoPlayer) {
        if (controller !== player) {
            controller?.removeListener(listener)
            controller = player
            artworkKey = null
        }
        if (!listening) {
            player.addListener(listener)
            listening = true
        }
        render(player)
        if (player.isPlaying) startTicker()
    }

    private fun renderCurrent() {
        controller?.let { render(it) }
    }

    private fun render(player: Player) {
        val item = player.currentMediaItem
        val metadata = item?.mediaMetadata
        val trackId = item?.mediaId.orEmpty()

        titleText.text = metadata?.title?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.player_nothing)
        artistText.text = metadata?.artist?.toString().orEmpty()

        val offline = trackId.isNotBlank() && repo.isOffline(trackId)
        offlineBadge.visibility = if (offline) View.VISIBLE else View.GONE

        val stored = trackId.takeIf { it.isNotBlank() }?.let { repo.get(it) }
        val model: Any? = metadata?.artworkUri
            ?: stored?.coverPath?.takeIf { File(it).isFile }
        val key = model?.toString()
        if (key != artworkKey) {
            artworkKey = key
            if (model != null) {
                coverImage.load(model) {
                    placeholder(R.drawable.bg_cover_placeholder)
                    error(R.drawable.bg_cover_placeholder)
                    crossfade(150)
                }
                backdropImage.load(model) {
                    placeholder(R.drawable.bg_player)
                    error(R.drawable.bg_player)
                    crossfade(350)
                }
            } else {
                coverImage.setImageDrawable(null)
                backdropImage.setImageDrawable(null)
            }
        }

        playPauseButton.setImageResource(
            if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) {
                R.drawable.ic_pause
            } else {
                R.drawable.ic_play
            },
        )
        playPauseButton.contentDescription = getString(
            if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) {
                R.string.player_pause
            } else {
                R.string.player_play
            },
        )

        renderProgress(player)
        renderQueue(player)
    }

    /**
     * Очередь под плеером: можно перейти на любой трек, а не только
     * листать «вперёд-назад». Длинные очереди обрезаем — экран не резиновый.
     */
    private fun renderQueue(player: Player) {
        val count = player.mediaItemCount
        val current = player.currentMediaItemIndex
        val signature = count to current
        if (signature == queueSignature) return
        queueSignature = signature

        queueList.removeAllViews()
        queueEmpty.visibility = if (count == 0) View.VISIBLE else View.GONE
        if (count == 0) return

        val inflater = LayoutInflater.from(this)
        val limit = minOf(count, 50)
        for (index in 0 until limit) {
            val item = player.getMediaItemAt(index)
            val row = inflater.inflate(R.layout.item_player_queue, queueList, false)
            val isCurrent = index == current

            row.findViewById<TextView>(R.id.queueIndex).text = (index + 1).toString()
            val titleView = row.findViewById<TextView>(R.id.queueTitle)
            titleView.text = item.mediaMetadata.title?.toString()
                ?.takeIf { it.isNotBlank() }
                ?: getString(R.string.unknown_track)
            titleView.setTypeface(null, if (isCurrent) Typeface.BOLD else Typeface.NORMAL)
            row.findViewById<TextView>(R.id.queueArtist).text =
                item.mediaMetadata.artist?.toString().orEmpty()
            row.alpha = if (isCurrent) 1f else 0.72f

            row.setOnClickListener {
                PlaybackHub.withController(this@PlayerActivity) { player ->
                    controller = player
                    player.seekTo(index, 0L)
                    player.playWhenReady = true
                    render(player)
                    startTicker()
                }
            }
            queueList.addView(row)
        }
    }

    private fun openCatalog() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }

    private fun renderProgress(player: Player) {
        val duration = player.duration.coerceAtLeast(0L)
        if (!seeking) {
            seekBar.max = duration.coerceAtLeast(1L).toInt()
            val position = player.currentPosition.coerceIn(0L, duration.coerceAtLeast(0L))
            seekBar.progress = position.toInt()
            positionText.text = formatDuration((position / 1000).toInt())
        }
        durationText.text = formatDuration((duration / 1000).toInt())
    }

    private fun startTicker() {
        if (tickerRunning) return
        tickerRunning = true
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private fun stopTicker() {
        tickerRunning = false
        handler.removeCallbacks(ticker)
    }
}
