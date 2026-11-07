package app.zhilibyli

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import app.zhilibyli.data.OfflineRepository
import app.zhilibyli.data.OfflineTrack
import app.zhilibyli.download.DownloadRequest
import app.zhilibyli.download.DownloadService
import app.zhilibyli.ui.OfflineActivity
import app.zhilibyli.update.UpdateManager
import app.zhilibyli.ui.PlayerActivity
import app.zhilibyli.util.formatBytes
import app.zhilibyli.util.plural
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Нативная оболочка каталога + JS-мосты к нативным функциям.
 *
 * Онлайн-каталог остаётся в WebView, но всё, что касается офлайн-музыки
 * (скачивание, метаданные, библиотека, плеер), реализовано на Kotlin.
 * При потере сети приложение не показывает «нет соединения», а открывает
 * нативный офлайн-плеер с сохранёнными файлами.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var networkBanner: View
    private lateinit var bannerText: TextView
    private lateinit var offlineGate: View
    private lateinit var gateHint: TextView
    private lateinit var gateOpen: TextView

    private val repo by lazy { OfflineRepository.get(this) }
    private val mainHandler = Handler(Looper.getMainLooper())

    private var siteLoaded = false
    private var autoOpenedOffline = false
    private var playerListenerAttached = false
    private var playerListenerTarget: Player? = null
    private var tickerRunning = false
    private var cachedTracks: List<OfflineTrack> = emptyList()
    private lateinit var updateManager: UpdateManager

    private val voiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!text.isNullOrEmpty()) emitWindowEvent("onNativeVoiceResult", JSONObject.quote(text))
            else emitWindowEvent("onNativeVoiceError", "")
        } else {
            emitWindowEvent("onNativeVoiceError", "")
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            val controller = PlaybackHub.get()
            if (controller == null || !controller.isPlaying) {
                tickerRunning = false
                return
            }
            emitPlayerEvent(timeEvent(controller))
            mainHandler.postDelayed(this, 500)
        }
    }

    private val playerEvents = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            emitPlayerEvent(stateEvent())
            if (state == Player.STATE_READY) startTicker()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            emitPlayerEvent(stateEvent())
            if (isPlaying) startTicker()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            emitPlayerEvent(itemEvent())
        }

        override fun onPlayerError(error: PlaybackException) {
            // Последний рубеж: если WebView всё-таки прислал сетевой URL
            // для скачанного трека, перезапускаем его из локального файла.
            val controller = PlaybackHub.get()
            if (controller != null &&
                PlaybackHub.retryCurrentFromLocal(this@MainActivity, controller)
            ) {
                emitPlayerEvent(stateEvent())
                return
            }
            emitPlayerEvent(
                JSONObject().apply {
                    put("type", "error")
                    put("message", playerErrorMessage(error))
                },
            )
        }
    }

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.getStringExtra(DownloadService.EXTRA_TYPE)) {
                "progress" -> {
                    val payload = JSONObject().apply {
                        put("trackId", intent.getStringExtra(DownloadService.EXTRA_TRACK_ID).orEmpty())
                        put("title", intent.getStringExtra(DownloadService.EXTRA_TITLE).orEmpty())
                        put("percent", intent.getIntExtra(DownloadService.EXTRA_PERCENT, -1))
                        put("bytes", intent.getLongExtra(DownloadService.EXTRA_BYTES, 0))
                        put("total", intent.getLongExtra(DownloadService.EXTRA_TOTAL, 0))
                    }
                    emitWindowEvent("onNativeOfflineProgress", payload.toString())
                }

                "result" -> {
                    val payload = JSONObject().apply {
                        put("trackId", intent.getStringExtra(DownloadService.EXTRA_TRACK_ID).orEmpty())
                        put("ok", intent.getBooleanExtra(DownloadService.EXTRA_OK, false))
                        put("error", intent.getStringExtra(DownloadService.EXTRA_ERROR).orEmpty())
                        put("path", intent.getStringExtra(DownloadService.EXTRA_PATH).orEmpty())
                    }
                    emitWindowEvent("onNativeOfflineResult", payload.toString())
                }
            }
        }
    }

    // ------------------------------------------------------------------
    //  Жизненный цикл
    // ------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { CookieManager.getInstance().setAcceptCookie(true) }
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        networkBanner = findViewById(R.id.networkBanner)
        bannerText = findViewById(R.id.bannerText)
        offlineGate = findViewById(R.id.offlineGate)
        gateHint = findViewById(R.id.gateHint)
        gateOpen = findViewById(R.id.gateOpen)

        setupWebView()
        setupOfflineUi()
        requestNotificationPermission()
        updateManager = UpdateManager(this)

        ContextCompat.registerReceiver(
            this,
            downloadReceiver,
            IntentFilter(DownloadService.ACTION_EVENT),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        siteLoaded = savedInstanceState?.getBoolean(KEY_SITE_LOADED) ?: false
        autoOpenedOffline = savedInstanceState?.getBoolean(KEY_AUTO_OPENED) ?: false
        if (siteLoaded) {
            savedInstanceState?.let { webView.restoreState(it) }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    NetworkMonitor.online.collect { online -> onNetworkChanged(online) }
                }
                launch {
                    repo.tracks.collect { tracks ->
                        cachedTracks = tracks
                        updateOfflineSummary()
                        if (NetworkMonitor.isOffline()) maybeOpenOffline()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::updateManager.isInitialized) updateManager.check()
        PlaybackHub.withController(this) { player ->
            if (isFinishing || isDestroyed) return@withController
            if (playerListenerTarget !== player) {
                playerListenerTarget?.removeListener(playerEvents)
                player.addListener(playerEvents)
                playerListenerTarget = player
                playerListenerAttached = true
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::webView.isInitialized) webView.saveState(outState)
        outState.putBoolean(KEY_SITE_LOADED, siteLoaded)
        outState.putBoolean(KEY_AUTO_OPENED, autoOpenedOffline)
    }

    override fun onStop() {
        super.onStop()
        runCatching { CookieManager.getInstance().flush() }
    }

    override fun onDestroy() {
        if (::updateManager.isInitialized) updateManager.destroy()
        mainHandler.removeCallbacks(ticker)
        playerListenerTarget?.removeListener(playerEvents)
        playerListenerTarget = null
        playerListenerAttached = false
        runCatching { unregisterReceiver(downloadReceiver) }
        runCatching { CookieManager.getInstance().flush() }
        webView.stopLoading()
        (webView.parent as? android.view.ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (::updateManager.isInitialized) updateManager.retryPendingInstall()
    }

    override fun onPause() {
        super.onPause()
        runCatching { CookieManager.getInstance().flush() }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (offlineGate.visibility == View.VISIBLE) {
            moveTaskToBack(true)
            return
        }
        if (siteLoaded && webView.canGoBack()) {
            webView.goBack()
            return
        }
        super.onBackPressed()
    }

    // ------------------------------------------------------------------
    //  WebView
    // ------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
            flush()
        }

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = false
        settings.allowContentAccess = true
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.userAgentString = settings.userAgentString + " ZhiliByliApp/1.0"

        webView.addJavascriptInterface(VoiceBridge(), "ZhiliNativeVoice")
        webView.addJavascriptInterface(PlayerBridge(), "ZhiliNativePlayer")
        webView.addJavascriptInterface(FilesBridge(), "ZhiliNativeFiles")
        webView.addJavascriptInterface(AppBridge(), "ZhiliNativeApp")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean = request?.url?.let { handleUrl(it) } ?: false

            @Deprecated("Deprecated in Java")
            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                url?.let { handleUrl(Uri.parse(it)) } ?: false

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                if (request?.isForMainFrame != true) return
                val code = error?.errorCode
                val networkFailure = code == WebViewClient.ERROR_HOST_LOOKUP ||
                    code == WebViewClient.ERROR_CONNECT ||
                    code == WebViewClient.ERROR_TIMEOUT ||
                    code == WebViewClient.ERROR_IO
                if (!NetworkMonitor.hasActiveNetwork() || networkFailure) {
                    siteLoaded = false
                    showOfflineGate()
                    maybeOpenOffline()
                }
            }
        }
    }

    private fun setupOfflineUi() {
        findViewById<View>(R.id.gateOpenButton).setOnClickListener { openOffline() }
        findViewById<View>(R.id.gateRetry).setOnClickListener {
            if (NetworkMonitor.hasActiveNetwork()) loadSite()
            else toast(getString(R.string.offline_still_no_network))
        }
        networkBanner.setOnClickListener { openOffline() }
        findViewById<View>(R.id.bannerAction).setOnClickListener { openOffline() }
    }

    private fun loadSite() {
        siteLoaded = true
        offlineGate.visibility = View.GONE
        networkBanner.visibility = View.GONE
        webView.loadUrl(START_URL)
    }

    private fun onNetworkChanged(online: Boolean) {
        emitWindowEvent("onNativeNetworkChange", if (NetworkMonitor.hasActiveNetwork()) "true" else "false")

        if (online || NetworkMonitor.hasActiveNetwork()) {
            autoOpenedOffline = false
            networkBanner.visibility = View.GONE
            offlineGate.visibility = View.GONE
            if (!siteLoaded) loadSite()
        } else {
            // Полностью закрываем онлайн-часть: каталог, поиск и стриминг
            // не должны оставаться доступными без валидного интернета.
            showOfflineGate()
            maybeOpenOffline()
        }
        updateOfflineSummary()
    }

    private fun showOfflineGate() {
        networkBanner.visibility = View.GONE
        offlineGate.visibility = View.VISIBLE
    }

    /**
     * При потере сети один раз открываем нативную офлайн-библиотеку.
     * Если треков нет — остаёмся на заглушке с объяснением.
     */
    private fun maybeOpenOffline() {
        if (cachedTracks.isEmpty() || autoOpenedOffline) return
        autoOpenedOffline = true
        openOffline()
    }

    private fun updateOfflineSummary() {
        val count = cachedTracks.size
        val bytes = repo.totalBytes(cachedTracks)
        val countLabel = if (count > 0) {
            "$count ${plural(count, "трек", "трека", "треков")} · ${formatBytes(bytes)}"
        } else {
            getString(R.string.offline_empty_short)
        }

        bannerText.text = getString(R.string.offline_banner_text, countLabel)
        gateHint.text = if (count > 0) {
            getString(R.string.offline_gate_saved, countLabel)
        } else {
            getString(R.string.offline_gate_empty)
        }
        gateOpen.text = if (count > 0) {
            getString(R.string.offline_open_count, count)
        } else {
            getString(R.string.offline_open)
        }
    }

    private fun openOffline() {
        startActivity(Intent(this, OfflineActivity::class.java))
    }

    private fun handleUrl(uri: Uri): Boolean {
        val host = uri.host
        if (host == null || ALLOWED_HOSTS.contains(host)) return false
        val scheme = uri.scheme
        if (scheme == "http" || scheme == "https") {
            try {
                webView.loadUrl(uri.toString())
                return true
            } catch (_: Exception) {
            }
        }
        return try {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            true
        } catch (_: Exception) {
            toast(getString(R.string.no_app_for_link))
            true
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------------------
    //  События в JavaScript
    // ------------------------------------------------------------------

    private fun emitWindowEvent(function: String, rawArgument: String) {
        if (!::webView.isInitialized) return
        val argument = rawArgument.ifBlank { "null" }
        webView.post {
            webView.evaluateJavascript(
                "window.$function && window.$function($argument);",
                null,
            )
        }
    }

    private fun emitPlayerEvent(event: JSONObject) {
        emitWindowEvent("onNativePlayerEvent", event.toString())
    }

    private fun startTicker() {
        if (tickerRunning) return
        tickerRunning = true
        mainHandler.postDelayed(ticker, 500)
    }

    private fun stateEvent(): JSONObject = JSONObject().apply {
        val controller = PlaybackHub.get()
        put("type", "state")
        put("playing", controller?.isPlaying ?: false)
        put("state", controller?.playbackState ?: Player.STATE_IDLE)
    }

    private fun timeEvent(controller: Player): JSONObject = JSONObject().apply {
        put("type", "time")
        put("position", controller.currentPosition / 1000.0)
        put("duration", controller.duration / 1000.0)
    }

    private fun itemEvent(): JSONObject = JSONObject().apply {
        val controller = PlaybackHub.get()
        put("type", "item")
        put("index", controller?.currentMediaItemIndex ?: 0)
        put("mediaId", controller?.currentMediaItem?.mediaId.orEmpty())
        put("playing", controller?.isPlaying ?: false)
    }

    // ------------------------------------------------------------------
    //  Мост: голосовой ввод
    // ------------------------------------------------------------------

    inner class VoiceBridge {
        @JavascriptInterface
        fun start() {
            runOnUiThread {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                    putExtra(
                        RecognizerIntent.EXTRA_PROMPT,
                        getString(R.string.voice_prompt),
                    )
                }
                try {
                    voiceLauncher.launch(intent)
                } catch (_: Exception) {
                    emitWindowEvent("onNativeVoiceError", "")
                }
            }
        }
    }

    // ------------------------------------------------------------------
    //  Мост: нативный плеер
    // ------------------------------------------------------------------

    inner class PlayerBridge {

        @JavascriptInterface
        fun playQueue(json: String, startIndex: Int) {
            runOnUiThread {
                runCatching {
                    val array = JSONArray(json)
                    val requestedStartId = array.optJSONObject(startIndex)
                        ?.optString("id")
                        .orEmpty()
                    val items = ArrayList<MediaItem>()
                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        val trackId = item.optString("id", i.toString())
                        val rawUrl = item.optString("url")
                        if (rawUrl.isBlank() && repo.pathOf(trackId) == null) continue

                        val metadata = MediaMetadata.Builder()
                            .setTitle(item.optString("title", getString(R.string.unknown_track)))
                            .setArtist(item.optString("artist", ""))

                        // Для скачанного трека источник всегда файл, а не URL
                        // из устаревшей очереди WebView.
                        val localPath = repo.pathOf(trackId)
                        val uri = localPath?.let { Uri.fromFile(File(it)) }
                            ?: mediaUri(rawUrl)
                        val cover = if (localPath != null) {
                            repo.get(trackId)?.coverPath
                                ?.takeIf { File(it).isFile }
                                ?.let { Uri.fromFile(File(it)) }
                        } else {
                            artworkUri(item.optString("cover"))
                        }
                        cover?.let(metadata::setArtworkUri)

                        items.add(
                            MediaItem.Builder()
                                .setUri(uri)
                                .setMediaId(trackId)
                                .setMediaMetadata(metadata.build())
                                .build(),
                        )
                    }

                    val safeStart = items
                        .indexOfFirst { it.mediaId == requestedStartId }
                        .takeIf { it >= 0 }
                        ?: startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
                    val startItem = items.getOrNull(safeStart)
                    val hasLocalStart = startItem?.mediaId
                        ?.let { repo.pathOf(it) != null }
                        ?: false
                    if (!NetworkMonitor.hasActiveNetwork() && !hasLocalStart) {
                        emitPlayerError(getString(R.string.offline_player_error))
                        return@runOnUiThread
                    }

                    if (items.isNotEmpty() &&
                        PlaybackHub.playQueue(this@MainActivity, items, safeStart)
                    ) {
                        startTicker()
                    } else {
                        emitPlayerError(
                            getString(
                                if (!NetworkMonitor.hasActiveNetwork()) {
                                    R.string.offline_player_error
                                } else {
                                    R.string.player_unable
                                },
                            ),
                        )
                    }
                }.onFailure { emitPlayerError(it.message) }
            }
        }

        @JavascriptInterface
        fun play() {
            runOnUiThread {
                PlaybackHub.withController(this@MainActivity) { controller ->
                    controller.playWhenReady = true
                    startTicker()
                    emitPlayerEvent(stateEvent())
                }
            }
        }

        @JavascriptInterface
        fun pause() {
            runOnUiThread {
                PlaybackHub.withController(this@MainActivity) { controller ->
                    controller.playWhenReady = false
                    emitPlayerEvent(stateEvent())
                }
            }
        }

        @JavascriptInterface
        fun stop() {
            runOnUiThread { PlaybackHub.stop() }
        }

        @JavascriptInterface
        fun seekTo(seconds: Double) {
            runOnUiThread {
                PlaybackHub.withController(this@MainActivity) { controller ->
                    controller.seekTo((seconds * 1000).toLong().coerceAtLeast(0))
                    emitPlayerEvent(timeEvent(controller))
                }
            }
        }

        @JavascriptInterface
        fun next() {
            runOnUiThread {
                PlaybackHub.withController(this@MainActivity) { controller ->
                    if (controller.hasNextMediaItem()) controller.seekToNextMediaItem()
                    emitPlayerEvent(itemEvent())
                }
            }
        }

        @JavascriptInterface
        fun prev() {
            runOnUiThread {
                PlaybackHub.withController(this@MainActivity) { controller ->
                    if (controller.hasPreviousMediaItem()) controller.seekToPreviousMediaItem()
                    else controller.seekTo(0)
                    emitPlayerEvent(itemEvent())
                }
            }
        }

        @JavascriptInterface
        fun setVolume(value: Double) {
            runOnUiThread {
                PlaybackHub.withController(this@MainActivity) { controller ->
                    controller.volume = value.toFloat().coerceIn(0f, 1f)
                }
            }
        }

        /** 0 — выключен, 1 — один трек, 2 — вся очередь (константы Media3). */
        @JavascriptInterface
        fun setRepeatMode(mode: Int) {
            runOnUiThread {
                if (mode in 0..2) PlaybackHub.get()?.repeatMode = mode
            }
        }

        @JavascriptInterface
        fun setShuffleMode(enabled: Boolean) {
            runOnUiThread {
                PlaybackHub.get()?.shuffleModeEnabled = enabled
            }
        }

        @JavascriptInterface
        fun removeItem(index: Int) {
            runOnUiThread {
                val player = PlaybackHub.get() ?: return@runOnUiThread
                if (index in 0 until player.mediaItemCount) player.removeMediaItem(index)
            }
        }
    }

    private fun emitPlayerError(message: String?) {
        emitPlayerEvent(
            JSONObject().apply {
                put("type", "error")
                put("message", message.orEmpty())
            },
        )
    }

    /** Не показываем пользователю технический код Media3. */
    private fun playerErrorMessage(error: PlaybackException): String {
        val networkError =
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        return if (networkError || NetworkMonitor.isOffline()) {
            getString(R.string.offline_player_error)
        } else {
            error.errorCodeName
        }
    }

    private fun mediaUri(raw: String): Uri = when {
        raw.startsWith("http://") || raw.startsWith("https://") ||
            raw.startsWith("file://") || raw.startsWith("content://") -> Uri.parse(raw)
        raw.startsWith("/") -> Uri.fromFile(File(raw))
        else -> Uri.parse(raw)
    }

    private fun artworkUri(raw: String): Uri? {
        if (raw.isBlank()) return null
        return mediaUri(raw)
    }

    // ------------------------------------------------------------------
    //  Мост: офлайн-файлы
    // ------------------------------------------------------------------

    inner class FilesBridge {

        @JavascriptInterface
        fun download(trackId: String, url: String) {
            enqueueDownload(trackId, url, "")
        }

        @JavascriptInterface
        fun downloadTrack(trackId: String, url: String, metaJson: String) {
            enqueueDownload(trackId, url, metaJson)
        }

        @JavascriptInterface
        fun isOffline(trackId: String): Boolean = repo.isOffline(trackId)

        @JavascriptInterface
        fun pathOf(trackId: String): String = repo.pathOf(trackId).orEmpty()

        @JavascriptInterface
        fun remove(trackId: String): Boolean = repo.remove(trackId)

        /** Имена файлов — совместимость со старым мостом. */
        @JavascriptInterface
        fun allOffline(): String {
            val array = JSONArray()
            repo.snapshot().forEach { array.put(it.fileName) }
            return array.toString()
        }

        /** Полные метаданные для офлайн-вкладки сайта. */
        @JavascriptInterface
        fun listOffline(): String {
            val array = JSONArray()
            repo.snapshot().forEach { array.put(trackJson(it)) }
            return array.toString()
        }

        @JavascriptInterface
        fun stats(): String = JSONObject().apply {
            val tracks = repo.snapshot()
            put("count", tracks.size)
            put("bytes", repo.totalBytes(tracks))
        }.toString()

        @JavascriptInterface
        fun openOffline() {
            runOnUiThread { openOffline() }
        }

        @JavascriptInterface
        fun playOffline(trackId: String) {
            runOnUiThread {
                val tracks = repo.snapshot()
                val index = tracks.indexOfFirst { it.trackId == trackId }
                if (index < 0) {
                    openOffline()
                    return@runOnUiThread
                }
                val controller = PlaybackHub.get()
                if (controller?.currentMediaItem?.mediaId == trackId) {
                    startActivity(Intent(this@MainActivity, PlayerActivity::class.java))
                    return@runOnUiThread
                }
                PlaybackHub.playOffline(this@MainActivity, tracks, index)
                startActivity(Intent(this@MainActivity, PlayerActivity::class.java))
            }
        }
    }

    private fun enqueueDownload(trackId: String, url: String, metaJson: String) {
        if (trackId.isBlank() || url.isBlank()) {
            emitWindowEvent(
                "onNativeOfflineResult",
                JSONObject().apply {
                    put("trackId", trackId)
                    put("ok", false)
                    put("error", "Нет ссылки на трек")
                }.toString(),
            )
            return
        }
        val meta = metaJson.takeIf { it.isNotBlank() }
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
        val title = meta?.optString("title").orEmpty().ifBlank {
            getString(R.string.unknown_track)
        }
        val coverUrl = meta?.optString("coverUrl")?.takeIf { it.isNotBlank() && it != "null" }

        // CookieManager читаем на UI-потоке: сервис качает уже в фоне.
        val cookie = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
        val coverCookie = coverUrl
            ?.let { runCatching { CookieManager.getInstance().getCookie(it) }.getOrNull() }

        DownloadService.enqueue(
            this,
            DownloadRequest(
                trackId = trackId,
                url = url,
                title = title,
                artist = meta?.optString("artist").orEmpty(),
                coverUrl = coverUrl,
                durationSec = meta?.optInt("durationSec", 0) ?: 0,
                source = meta?.optString("source").orEmpty().ifBlank { "manual" },
                silent = (meta?.optString("source").orEmpty() == "like" || meta?.optString("source").orEmpty() == "manual" || meta?.optBoolean("silent", false) == true),
                cookie = cookie?.takeIf { it.isNotBlank() },
                coverCookie = coverCookie?.takeIf { it.isNotBlank() },
            ),
        )
    }

    private fun trackJson(track: OfflineTrack): JSONObject = JSONObject().apply {
        put("trackId", track.trackId)
        put("title", track.displayTitle)
        put("artist", track.displayArtist)
        put("coverUrl", track.coverUrl ?: JSONObject.NULL)
        put("coverPath", track.coverPath ?: JSONObject.NULL)
        put("durationSec", track.durationSec)
        put("size", track.sizeBytes)
        put("addedAt", track.addedAt)
        put("source", track.source)
        put("path", repo.pathOf(track.trackId).orEmpty())
    }

    // ------------------------------------------------------------------
    //  Мост: общая информация о приложении
    // ------------------------------------------------------------------

    inner class AppBridge {
        @JavascriptInterface
        fun version(): String = BuildConfig.VERSION_NAME

        @JavascriptInterface
        fun isOnline(): Boolean = NetworkMonitor.isOnline()

        @JavascriptInterface
        fun hasNetwork(): Boolean = NetworkMonitor.hasActiveNetwork()

        @JavascriptInterface
        fun openOffline() {
            runOnUiThread { openOffline() }
        }

        @JavascriptInterface
        fun platform(): String = "android"

        @JavascriptInterface
        fun openPlayer() {
            runOnUiThread {
                startActivity(Intent(this@MainActivity, PlayerActivity::class.java))
            }
        }
    }

    companion object {
        private const val START_URL = "https://zhilibyli.online/"
        private const val REQUEST_NOTIFICATIONS = 101
        private const val KEY_SITE_LOADED = "site_loaded"
        private const val KEY_AUTO_OPENED = "auto_opened_offline"

        private val ALLOWED_HOSTS = setOf(
            "zhilibyli.online", "www.zhilibyli.online",
            "id.vk.com", "login.vk.com", "oauth.vk.com", "vk.com", "m.vk.com",
            "ok.ru", "m.ok.ru", "connect.ok.ru", "my.ok.ru",
            "mail.ru", "www.mail.ru", "connect.mail.ru", "account.mail.ru", "oauth.mail.ru",
        )
    }
}
