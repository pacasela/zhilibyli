package app.zhilibyli.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.zhilibyli.R
import app.zhilibyli.data.DownloadProgress
import app.zhilibyli.data.OfflineTrack
import app.zhilibyli.util.formatBytes
import app.zhilibyli.util.formatDuration
import coil.load
import java.io.File

/**
 * Список сохранённых треков. Никакой сети: обложка берётся из файла,
 * метаданные — из SQLite, поэтому экран работает в самолёте.
 */
class OfflineAdapter(
    private val onOpen: (OfflineTrack) -> Unit,
    private val onDelete: (OfflineTrack) -> Unit,
) : ListAdapter<OfflineTrack, OfflineAdapter.TrackHolder>(Diff) {

    private var downloads: Map<String, DownloadProgress> = emptyMap()

    fun setDownloads(map: Map<String, DownloadProgress>) {
        // Перерисовываем только строки, которые действительно качаются сейчас.
        val relevant = map.filterKeys { id -> currentList.any { it.trackId == id } }
        if (downloads == relevant) return
        downloads = relevant
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_offline_track, parent, false)
        return TrackHolder(view)
    }

    override fun onBindViewHolder(holder: TrackHolder, position: Int) {
        holder.bind(getItem(position), downloads[getItem(position).trackId], onOpen, onDelete)
    }

    class TrackHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cover: ImageView = view.findViewById(R.id.coverImage)
        private val title: TextView = view.findViewById(R.id.titleText)
        private val artist: TextView = view.findViewById(R.id.artistText)
        private val meta: TextView = view.findViewById(R.id.metaText)
        private val delete: ImageButton = view.findViewById(R.id.deleteButton)
        private val progress: ProgressBar = view.findViewById(R.id.itemProgress)

        fun bind(
            item: OfflineTrack,
            download: DownloadProgress?,
            onOpen: (OfflineTrack) -> Unit,
            onDelete: (OfflineTrack) -> Unit,
        ) {
            title.text = item.displayTitle
            artist.text = item.displayArtist

            val parts = ArrayList<String>(2)
            if (item.durationSec > 0) parts.add(formatDuration(item.durationSec))
            parts.add(formatBytes(item.sizeBytes))
            meta.text = parts.joinToString(" · ")

            val coverModel: Any? = item.coverPath
                ?.takeIf { File(it).isFile }
                ?: item.coverUrl?.takeIf { it.isNotBlank() }
            if (coverModel != null) {
                cover.load(coverModel) {
                    placeholder(R.drawable.bg_cover_placeholder)
                    error(R.drawable.bg_cover_placeholder)
                    crossfade(120)
                }
            } else {
                cover.setImageDrawable(null)
            }

            itemView.setOnClickListener { onOpen(item) }
            delete.setOnClickListener { onDelete(item) }

            if (download != null && download.trackId == item.trackId) {
                progress.visibility = View.VISIBLE
                if (download.percent >= 0) {
                    progress.isIndeterminate = false
                    progress.progress = download.percent.coerceIn(0, 100)
                } else {
                    progress.isIndeterminate = true
                }
            } else {
                progress.visibility = View.GONE
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<OfflineTrack>() {
        override fun areItemsTheSame(oldItem: OfflineTrack, newItem: OfflineTrack): Boolean =
            oldItem.trackId == newItem.trackId

        override fun areContentsTheSame(oldItem: OfflineTrack, newItem: OfflineTrack): Boolean =
            oldItem == newItem
    }
}
