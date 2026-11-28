package com.yufiplay.app.ui.player

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
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
import kotlinx.coroutines.launch

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var prefs: PrefsManager

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private var currentVideo: VideoItem? = null
    private var localFileUri: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsManager(this)

        @Suppress("DEPRECATION")
        currentVideo = intent.getSerializableExtra(EXTRA_VIDEO) as? VideoItem
        localFileUri = intent.getStringExtra(EXTRA_LOCAL_URI)

        binding.switchBackgroundAudio.isChecked = prefs.backgroundAudioEnabled
        binding.switchBackgroundAudio.setOnCheckedChangeListener { _, isChecked ->
            prefs.backgroundAudioEnabled = isChecked
        }

        binding.btnPip.setOnClickListener { enterPipMode() }
        binding.btnDownloadVideo.setOnClickListener {
            currentVideo?.let { video ->
                DownloadBottomSheetFragment.newInstance(video)
                    .show(supportFragmentManager, "DownloadSheet")
            }
        }
        binding.btnBackPlayer.setOnClickListener { finish() }

        if (localFileUri != null) {
            binding.btnDownloadVideo.visibility = View.GONE
        }

        currentVideo?.let {
            binding.tvPlayerTitle.text = it.title
            binding.tvPlayerChannel.text = it.channel
        }

        initializeMediaController()
    }

    private fun initializeMediaController() {
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            val controller = controllerFuture?.get() ?: return@addListener
            mediaController = controller
            binding.playerView.player = controller
            controller.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        binding.progressPlayerLoading.visibility = View.GONE
                    }
                }
            })
            prepareMedia()
        }, MoreExecutors.directExecutor())
    }

    private fun prepareMedia() {
        val controller = mediaController ?: return
        val localUriStr = localFileUri
        if (!localUriStr.isNullOrBlank()) {
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(localUriStr))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(currentVideo?.title ?: "Saved Media")
                        .setArtist(currentVideo?.channel ?: "YufiPlay")
                        .build()
                )
                .build()
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
            binding.progressPlayerLoading.visibility = View.GONE
            return
        }

        val video = currentVideo ?: return
        binding.progressPlayerLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val (streamUrl, resolvedVideo) = YtDlpEngine.resolvePlayableStreamUrl(video.webpageUrl)
                currentVideo = resolvedVideo
                binding.tvPlayerTitle.text = resolvedVideo.title
                binding.tvPlayerChannel.text = resolvedVideo.channel

                HistoryRepository.addHistory(
                    applicationContext,
                    HistoryItem(
                        id = resolvedVideo.id,
                        title = resolvedVideo.title,
                        channel = resolvedVideo.channel,
                        thumbnailUrl = resolvedVideo.thumbnailUrl,
                        url = resolvedVideo.webpageUrl,
                        durationSeconds = resolvedVideo.durationSeconds,
                        type = HistoryType.WATCHED
                    )
                )

                val mediaItem = MediaItem.Builder()
                    .setUri(streamUrl)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(resolvedVideo.title)
                            .setArtist(resolvedVideo.channel)
                            .build()
                    )
                    .build()
                controller.setMediaItem(mediaItem)
                controller.prepare()
                controller.play()
            } catch (e: Exception) {
                binding.progressPlayerLoading.visibility = View.GONE
                Toast.makeText(
                    this@PlayerActivity,
                    e.message ?: "Unable to stream video",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        } else {
            Toast.makeText(this, "PiP requires Android 8.0+", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mediaController?.isPlaying == true) {
            enterPipMode()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        val visibility = if (isInPictureInPictureMode) View.GONE else View.VISIBLE
        binding.layoutPlayerControls.visibility = visibility
        binding.toolbarPlayer.visibility = visibility
        binding.playerView.useController = !isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        if (!prefs.backgroundAudioEnabled && !isInPictureInPictureMode) {
            mediaController?.pause()
        }
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_VIDEO = "extra_video"
        const val EXTRA_LOCAL_URI = "extra_local_uri"
    }
}
