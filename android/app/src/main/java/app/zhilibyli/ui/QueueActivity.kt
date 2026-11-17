package app.zhilibyli.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.zhilibyli.PlaybackHub
import app.zhilibyli.R
import app.zhilibyli.ui.PlayerActivity
import coil.load

class QueueActivity : AppCompatActivity() {

    private lateinit var miniPlayer: LinearLayout
    private lateinit var miniCover: ImageView
    private lateinit var miniTitle: TextView
    private lateinit var miniArtist: TextView
    private lateinit var miniPlayPause: ImageButton
    private lateinit var miniNext: ImageButton
    private lateinit var tabText: TextView
    private lateinit var tabQueue: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView

    private var queueAdapter: QueueAdapter? = null
    private var isShowingQueue = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_queue)

        initViews()
        setupTabs()
        setupRecyclerView()
        updateMiniPlayer()
    }

    private fun initViews() {
        miniPlayer = findViewById(R.id.mini_player)
        miniCover = findViewById(R.id.mini_cover)
        miniTitle = findViewById(R.id.mini_title)
        miniArtist = findViewById(R.id.mini_artist)
        miniPlayPause = findViewById(R.id.mini_play_pause)
        miniNext = findViewById(R.id.mini_next)
        tabText = findViewById(R.id.tab_text)
        tabQueue = findViewById(R.id.tab_queue)
        recyclerView = findViewById(R.id.queue_recycler)
        emptyView = findViewById(R.id.empty_view)

        miniPlayPause.setOnClickListener { PlaybackHub.togglePlayPause() }
        miniNext.setOnClickListener { PlaybackHub.next() }
        miniPlayer.setOnClickListener { openFullPlayer() }
    }

    private fun setupTabs() {
        tabText.setOnClickListener {
            isShowingQueue = false
            updateTabs()
            showTextView()
        }
        tabQueue.setOnClickListener {
            isShowingQueue = true
            updateTabs()
            showQueue()
        }
        updateTabs()
    }

    private fun updateTabs() {
        val selectedColor = resources.getColor(android.R.color.white)
        val unselectedColor = resources.getColor(android.R.color.darker_gray)
        val selectedBg = resources.getColor(R.color.tab_selected)
        val unselectedBg = resources.getColor(R.color.tab_unselected)

        tabText.setTextColor(if (!isShowingQueue) selectedColor else unselectedColor)
        tabQueue.setTextColor(if (isShowingQueue) selectedColor else unselectedColor)
        tabText.setBackgroundColor(if (!isShowingQueue) selectedBg else unselectedBg)
        tabQueue.setBackgroundColor(if (isShowingQueue) selectedBg else unselectedBg)
    }

    private fun setupRecyclerView() {
        queueAdapter = QueueAdapter(
            onItemClick = { track, position ->
                PlaybackHub.playQueue(PlaybackHub.getQueue(), position)
            },
            onRemoveClick = { _, position ->
                PlaybackHub.removeFromQueue(position)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = queueAdapter
    }

    private fun updateMiniPlayer() {
        val current = PlaybackHub.getCurrentTrack()
        val isPlaying = PlaybackHub.isPlaying()

        if (current != null) {
            miniPlayer.visibility = View.VISIBLE
            val metadata = current.mediaMetadata
            miniTitle.text = metadata?.title?.takeIf { it.isNotEmpty() } ?: getString(R.string.queue_no_title)
            miniArtist.text = metadata?.artist?.takeIf { it.isNotEmpty() } ?: getString(R.string.queue_no_artist)
            miniPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)

            val artworkUri = metadata?.artworkUri?.toString()
            if (artworkUri.isNotEmpty()) {
                miniCover.load(artworkUri) {
                    placeholder(R.drawable.bg_cover_placeholder)
                    error(R.drawable.bg_cover_placeholder)
                }
            } else {
                miniCover.setImageResource(R.drawable.bg_cover_placeholder)
            }
        } else {
            miniPlayer.visibility = View.GONE
        }
    }

    private fun showQueue() {
        val queue = PlaybackHub.getQueue()
        recyclerView.visibility = if (queue.isEmpty()) View.GONE else View.VISIBLE
        emptyView.visibility = if (queue.isEmpty()) View.VISIBLE else View.GONE
        emptyView.text = getString(R.string.queue_empty)
    }

    private fun showTextView() {
        recyclerView.visibility = View.GONE
        emptyView.visibility = View.VISIBLE
        emptyView.text = getString(R.string.lyrics_placeholder)
    }

    private fun openFullPlayer() {
        val intent = Intent(this, PlayerActivity::class.java)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        updateMiniPlayer()
        if (isShowingQueue) showQueue() else showTextView()
    }

    companion object {
        fun start(context: android.content.Context) {
            val intent = Intent(context, QueueActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            context.startActivity(intent)
        }
    }
}

private class QueueAdapter(
    private val onItemClick: (MediaItem, Int) -> Unit,
    private val onRemoveClick: (MediaItem, Int) -> Unit
) : RecyclerView.Adapter<QueueAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cover: ImageView = view.findViewById(R.id.track_cover)
        val title: TextView = view.findViewById(R.id.track_title)
        val artist: TextView = view.findViewById(R.id.track_artist)
        val remove: ImageButton = view.findViewById(R.id.track_remove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_queue_track, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val queue = PlaybackHub.getQueue()
        if (position >= queue.size) return

        val track = queue[position]
        val metadata = track.mediaMetadata

        holder.title.text = metadata?.title?.takeIf { it.isNotEmpty() } ?: holder.itemView.context.getString(R.string.track_title)
        holder.artist.text = metadata?.artist?.takeIf { it.isNotEmpty() } ?: holder.itemView.context.getString(R.string.track_artist)

        val artworkUri = metadata?.artworkUri?.toString()
        if (artworkUri.isNotEmpty()) {
            holder.cover.load(artworkUri) {
                placeholder(R.drawable.bg_cover_placeholder)
                error(R.drawable.bg_cover_placeholder)
            }
        } else {
            holder.cover.setImageResource(R.drawable.bg_cover_placeholder)
        }

        holder.itemView.setOnClickListener { onItemClick(track, position) }
        holder.remove.setOnClickListener { onRemoveClick(track, position) }
    }

    override fun getItemCount(): Int = PlaybackHub.getQueue().size
}