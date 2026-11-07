package app.zhilibyli.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.zhilibyli.MainActivity
import app.zhilibyli.PlaybackHub
import app.zhilibyli.R
import app.zhilibyli.data.DownloadProgress
import app.zhilibyli.data.OfflineRepository
import app.zhilibyli.data.OfflineTrack
import app.zhilibyli.util.formatBytes
import app.zhilibyli.util.plural
import kotlinx.coroutines.launch

/**
 * Нативная офлайн-библиотека: работает без сети и без WebView.
 * Треки читаются из filesDir/tracks, метаданные — из SQLite.
 */
class OfflineActivity : AppCompatActivity() {

    private lateinit var repo: OfflineRepository
    private lateinit var adapter: OfflineAdapter
    private lateinit var list: RecyclerView
    private lateinit var emptyView: View
    private lateinit var emptyTitle: TextView
    private lateinit var emptyText: TextView
    private lateinit var statsText: TextView
    private lateinit var storageText: TextView
    private lateinit var progressCard: View
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var playAll: TextView
    private lateinit var searchInput: EditText

    private var tracks: List<OfflineTrack> = emptyList()
    private var visible: List<OfflineTrack> = emptyList()
    private var query: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_offline)

        repo = OfflineRepository.get(this)

        list = findViewById(R.id.list)
        emptyView = findViewById(R.id.emptyView)
        emptyTitle = findViewById(R.id.emptyTitle)
        emptyText = findViewById(R.id.emptyText)
        statsText = findViewById(R.id.statsText)
        storageText = findViewById(R.id.storageText)
        progressCard = findViewById(R.id.progressCard)
        progressBar = findViewById(R.id.progressBar)
        progressText = findViewById(R.id.progressText)
        playAll = findViewById(R.id.playAllButton)
        searchInput = findViewById(R.id.searchInput)

        adapter = OfflineAdapter(
            onOpen = { track -> playTrack(track) },
            onDelete = { track -> confirmDelete(track) },
        )
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }
        findViewById<View>(R.id.onlineButton).setOnClickListener { openCatalog() }
        playAll.setOnClickListener { playFrom(0) }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim().orEmpty()
                applyFilter()
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    repo.tracks.collect { items ->
                        tracks = items
                        applyFilter()
                    }
                }
                launch {
                    repo.downloads.collect { renderDownloads(it) }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        repo.refreshAsync()
    }

    private fun applyFilter() {
        visible = if (query.isBlank()) {
            tracks
        } else {
            tracks.filter { track ->
                track.displayTitle.contains(query, ignoreCase = true) ||
                    track.displayArtist.contains(query, ignoreCase = true)
            }
        }
        adapter.submitList(visible)
        updateSummary()
    }

    private fun updateSummary() {
        val total = tracks.size
        val bytes = repo.totalBytes(tracks)

        if (total > 0) {
            statsText.text = getString(
                R.string.offline_count_format,
                total,
                plural(total, "трек", "трека", "треков"),
                formatBytes(bytes),
            )
            storageText.text = getString(R.string.offline_storage_used, formatBytes(bytes))
            storageText.visibility = View.VISIBLE
        } else {
            statsText.text = getString(R.string.offline_empty_short)
            storageText.text = getString(R.string.offline_storage_note)
            storageText.visibility = View.VISIBLE
        }

        val hasVisible = visible.isNotEmpty()
        list.visibility = if (hasVisible) View.VISIBLE else View.GONE
        emptyView.visibility = if (hasVisible) View.GONE else View.VISIBLE
        playAll.isEnabled = hasVisible
        playAll.alpha = if (hasVisible) 1f else 0.45f

        if (total == 0) {
            emptyTitle.setText(R.string.offline_empty_title)
            emptyText.setText(R.string.offline_empty_text)
        } else {
            emptyTitle.setText(R.string.offline_not_found_title)
            emptyText.setText(R.string.offline_not_found_text)
        }
    }

    private fun renderDownloads(map: Map<String, DownloadProgress>) {
        adapter.setDownloads(map)

        val active = map.values.firstOrNull()
        if (active == null) {
            progressCard.visibility = View.GONE
            return
        }

        progressCard.visibility = View.VISIBLE
        if (active.percent >= 0) {
            progressBar.isIndeterminate = false
            progressBar.progress = active.percent.coerceIn(0, 100)
        } else {
            progressBar.isIndeterminate = true
        }
        val title = active.title.ifBlank { getString(R.string.offline_download_title) }
        progressText.text = if (active.percent >= 0) {
            "$title · ${active.percent}%"
        } else {
            "$title · ${getString(R.string.offline_downloading)}"
        }
    }

    private fun playTrack(track: OfflineTrack) {
        val index = visible.indexOfFirst { it.trackId == track.trackId }
        playFrom(if (index >= 0) index else 0)
    }

    private fun playFrom(index: Int) {
        if (visible.isEmpty()) return
        PlaybackHub.playOffline(this, visible, index)
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    private fun confirmDelete(track: OfflineTrack) {
        AlertDialog.Builder(this)
            .setTitle(R.string.offline_delete_title)
            .setMessage(R.string.offline_delete_message)
            .setNegativeButton(R.string.offline_cancel, null)
            .setPositiveButton(R.string.offline_delete_confirm) { _, _ ->
                repo.remove(track.trackId)
            }
            .show()
    }

    private fun openCatalog() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}
