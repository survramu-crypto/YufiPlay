package com.yufiplay.app.ui.player

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.yufiplay.app.R
import com.yufiplay.app.data.HistoryItem
import com.yufiplay.app.data.HistoryRepository
import com.yufiplay.app.data.HistoryType
import com.yufiplay.app.data.PrefsManager
import com.yufiplay.app.data.VideoItem
import com.yufiplay.app.databinding.ActivityPlayerBinding
import com.yufiplay.app.engine.YtDlpEngine
import com.yufiplay.app.service.PlaybackService
import com.yufiplay.app.ui.adapters.VideoAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var prefs: PrefsManager
    private lateinit var relatedAdapter: VideoAdapter

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private var currentVideo: VideoItem? = null
    private var localUriString: String? = null
    private val playQueue = mutableListOf<VideoItem>()

    private var resolveJob: Job? = null
    private var triedFallbackClient = false
    private var isFullscreen = false
    private var isLiked = false
    private var isDisliked = false
    private var isSubscribed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)
        extractIntentData(intent)

        setupRelatedRecycler()
        setupControls()
        bindVideoMetadata(currentVideo)
        initMediaController()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractIntentData(intent)
        bindVideoMetadata(currentVideo)
        startPlaybackForCurrentSource()
    }

    @Suppress("DEPRECATION")
    private fun extractIntentData(intent: Intent?) {
        localUriString = intent?.getStringExtra(EXTRA_LOCAL_URI)
        currentVideo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getSerializableExtra(EXTRA_VIDEO, VideoItem::class.java)
        } else {
            intent?.getSerializableExtra(EXTRA_VIDEO) as? VideoItem
        }

        val queueExtra = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getSerializableExtra(EXTRA_QUEUE, ArrayList::class.java)
        } else {
            intent?.getSerializableExtra(EXTRA_QUEUE) as? ArrayList<*>
        }

        playQueue.clear()
        queueExtra?.filterIsInstance<VideoItem>()?.let { list ->
            playQueue.addAll(list)
        }
    }

    private fun setupRelatedRecycler() {
        relatedAdapter = VideoAdapter(
            onPlayClick = { nextVideo ->
                localUriString = null
                currentVideo = nextVideo
                bindVideoMetadata(nextVideo)
                startPlaybackForCurrentSource()
            },
            onDownloadClick = { item ->
                DownloadBottomSheetFragment.newInstance(item)
                    .show(supportFragmentManager, "DownloadSheet")
            },
            onShareClick = { item ->
                shareVideo(item)
            }
        )
        binding.rvRelatedVideos.layoutManager = LinearLayoutManager(this)
        binding.rvRelatedVideos.adapter = relatedAdapter

        val initialRelated = playQueue.filter { it.id != currentVideo?.id }
        relatedAdapter.submitList(initialRelated)
    }

    private fun setupControls() {
        binding.playerView.setFullscreenButtonClickListener { fullscreen ->
            toggleFullscreen(fullscreen)
        }

        binding.btnDownload.setOnClickListener {
            val video = currentVideo ?: return@setOnClickListener
            if (localUriString != null && !video.webpageUrl.startsWith("http")) {
                Toast.makeText(this, "Already saved offline!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            DownloadBottomSheetFragment.newInstance(video)
                .show(supportFragmentManager, "DownloadSheet")
        }

        binding.btnShare.setOnClickListener {
            currentVideo?.let { shareVideo(it) }
        }

        binding.btnPip.setOnClickListener {
            enterPipModeIfSupported()
        }

        binding.btnLike.setOnClickListener {
            isLiked = !isLiked
            if (isLiked) isDisliked = false
            binding.btnLike.alpha = if (isLiked) 1.0f else 0.75f
            binding.btnDislike.alpha = 0.75f
            Toast.makeText(this, if (isLiked) "Liked" else "Like removed", Toast.LENGTH_SHORT).show()
        }

        binding.btnDislike.setOnClickListener {
            isDisliked = !isDisliked
            if (isDisliked) isLiked = false
            binding.btnDislike.alpha = if (isDisliked) 1.0f else 0.75f
            binding.btnLike.alpha = 0.75f
        }

        binding.btnSubscribe.setOnClickListener {
            isSubscribed = !isSubscribed
            binding.btnSubscribe.text = if (isSubscribed) "Subscribed" else "Subscribe"
        }

        binding.tvCommentsSummary.setOnClickListener {
            showCommentsDialog()
        }
    }

    private fun bindVideoMetadata(video: VideoItem?) {
        if (video == null) return
        binding.tvPlayerTitle.text = video.title
        binding.tvChannelName.text = video.channel
        binding.tvChannelSubs.text = video.subscriberCountText.ifBlank { "Creator Channel" }
        binding.tvPlayerViews.text = buildString {
            if (video.viewCountText.isNotBlank()) append(video.viewCountText)
            if (video.formattedDuration().isNotBlank()) {
                if (isNotEmpty()) append(" • ")
                append(video.formattedDuration())
            }
        }
        binding.btnLike.text = video.likeCountText.ifBlank { "Like" }
        binding.btnDislike.text = video.dislikeCountText.ifBlank { "Dislike" }

        val commentLabel = if (video.comments.isNotEmpty()) {
            "Comments (${video.comments.size}) • Tap to read"
        } else {
            "${video.commentCountText.ifBlank { "Comments" }} • Tap to view"
        }
        binding.tvCommentsSummary.text = commentLabel

        val filteredQueue = playQueue.filter { it.id != video.id }
        if (filteredQueue.isNotEmpty()) {
            relatedAdapter.submitList(filteredQueue)
        } else if (localUriString == null && video.channel.isNotBlank()) {
            loadRelatedVideosForChannel(video)
        }
    }

    private fun loadRelatedVideosForChannel(video: VideoItem) {
        lifecycleScope.launch {
            runCatching {
                val list = YtDlpEngine.fetchChannelVideos(
                    channelUrl = video.channelUrl,
                    channelName = video.channel,
                    count = 12
                ).filter { it.id != video.id }
                if (list.isNotEmpty()) {
                    playQueue.clear()
                    playQueue.addAll(list)
                    relatedAdapter.submitList(list)
                }
            }
        }
    }

    private fun initMediaController() {
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            val controller = runCatching { controllerFuture?.get() }.getOrNull() ?: return@addListener
            mediaController = controller
            binding.playerView.player = controller

            controller.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        binding.progressPlayer.visibility = View.GONE
                    } else if (playbackState == Player.STATE_BUFFERING) {
                        binding.progressPlayer.visibility = View.VISIBLE
                    } else if (playbackState == Player.STATE_ENDED) {
                        if (prefs.autoPlayNext && localUriString == null) {
                            playNextInQueue()
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (!triedFallbackClient && localUriString == null) {
                        triedFallbackClient = true
                        resolveAndPlayOnline(useFallback = true)
                    } else {
                        binding.progressPlayer.visibility = View.GONE
                        Toast.makeText(
                            this@PlayerActivity,
                            "Playback error: ${error.localizedMessage ?: "Stream unavailable"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            })

            startPlaybackForCurrentSource()
        }, MoreExecutors.directExecutor())
    }

    private fun startPlaybackForCurrentSource() {
        val controller = mediaController ?: return
        triedFallbackClient = false

        val local = localUriString
        if (!local.isNullOrBlank()) {
            binding.progressPlayer.visibility = View.GONE
            val title = currentVideo?.title ?: "Offline Media"
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(local))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(currentVideo?.channel ?: "YufiPlay")
                        .build()
                )
                .build()
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
            return
        }

        resolveAndPlayOnline(useFallback = false)
    }

    private fun resolveAndPlayOnline(useFallback: Boolean) {
        val video = currentVideo ?: return
        val controller = mediaController ?: return

        resolveJob?.cancel()
        binding.progressPlayer.visibility = View.VISIBLE

        resolveJob = lifecycleScope.launch {
            try {
                val (streamUrl, enrichedItem) = YtDlpEngine.resolvePlayableStreamUrl(
                    url = video.webpageUrl,
                    useFallbackClient = useFallback
                )
                currentVideo = enrichedItem
                bindVideoMetadata(enrichedItem)

                HistoryRepository.addHistory(
                    this@PlayerActivity,
                    HistoryItem(
                        id = enrichedItem.id,
                        title = enrichedItem.title,
                        channel = enrichedItem.channel,
                        thumbnailUrl = enrichedItem.thumbnailUrl,
                        url = enrichedItem.webpageUrl,
                        durationSeconds = enrichedItem.durationSeconds,
                        type = HistoryType.WATCHED
                    )
                )

                val mediaItem = MediaItem.Builder()
                    .setUri(streamUrl)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(enrichedItem.title)
                            .setArtist(enrichedItem.channel)
                            .setArtworkUri(Uri.parse(enrichedItem.thumbnailUrl))
                            .build()
                    )
                    .build()

                controller.setMediaItem(mediaItem)
                controller.prepare()
                controller.play()
            } catch (e: Exception) {
                if (!useFallback) {
                    triedFallbackClient = true
                    resolveAndPlayOnline(useFallback = true)
                } else {
                    binding.progressPlayer.visibility = View.GONE
                    Toast.makeText(
                        this@PlayerActivity,
                        e.message ?: "Unable to stream video",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun playNextInQueue() {
        val currentId = currentVideo?.id
        val idx = playQueue.indexOfFirst { it.id == currentId }
        val next = when {
            idx >= 0 && idx + 1 < playQueue.size -> playQueue[idx + 1]
            playQueue.isNotEmpty() && playQueue[0].id != currentId -> playQueue[0]
            else -> null
        }
        if (next != null) {
            Toast.makeText(this, "Up next: ${next.title}", Toast.LENGTH_SHORT).show()
            currentVideo = next
            bindVideoMetadata(next)
            startPlaybackForCurrentSource()
        }
    }

    private fun showCommentsDialog() {
        val comments = currentVideo?.comments.orEmpty()
        if (comments.isEmpty()) {
            Toast.makeText(this, "No comments available for this video", Toast.LENGTH_SHORT).show()
            return
        }
        val formatted = comments.map { c ->
            "${c.author} ${if (c.timeAgo.isNotBlank()) "(${c.timeAgo})" else ""}\n${c.text}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Top Comments (${comments.size})")
            .setItems(formatted, null)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun shareVideo(item: VideoItem) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, item.title)
            putExtra(Intent.EXTRA_TEXT, "${item.title}\n${item.webpageUrl}")
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
    }

    private fun toggleFullscreen(enable: Boolean) {
        isFullscreen = enable
        requestedOrientation = if (enable) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (enable) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            binding.scrollVideoDetails.visibility = View.GONE
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            binding.scrollVideoDetails.visibility = View.VISIBLE
        }
    }

    private fun enterPipModeIfSupported() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        } else {
            Toast.makeText(this, "PiP not supported on this Android version", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        binding.playerView.useController = !isInPictureInPictureMode
        binding.scrollVideoDetails.visibility =
            if (isInPictureInPictureMode || isFullscreen) View.GONE else View.VISIBLE
    }

    override fun onStop() {
        super.onStop()
        val inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode
        if (!inPip && !prefs.backgroundAudioEnabled) {
            mediaController?.pause()
        }
    }

    override fun onDestroy() {
        resolveJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_VIDEO = "extra_video"
        const val EXTRA_LOCAL_URI = "extra_local_uri"
        const val EXTRA_QUEUE = "extra_queue"
    }
}
