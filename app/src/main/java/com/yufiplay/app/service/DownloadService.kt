package com.yufiplay.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yufiplay.app.R
import com.yufiplay.app.YufiPlayApp
import com.yufiplay.app.data.DownloadFormat
import com.yufiplay.app.data.DownloadStatus
import com.yufiplay.app.data.DownloadTask
import com.yufiplay.app.data.HistoryItem
import com.yufiplay.app.data.HistoryRepository
import com.yufiplay.app.data.HistoryType
import com.yufiplay.app.data.VideoItem
import com.yufiplay.app.engine.DownloadManager
import com.yufiplay.app.ui.player.PlayerActivity
import com.yufiplay.app.util.MediaStoreSaver
import com.yufiplay.app.util.UrlUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isProcessing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification("Preparing download…", 0, indeterminate = true)
        if (!isProcessing) {
            isProcessing = true
            processQueue()
        }
        return START_NOT_STICKY
    }

    private fun processQueue() {
        serviceScope.launch {
            YufiPlayApp.engineReady.await()
            while (true) {
                val task = DownloadManager.pollNextTask() ?: break
                executeTask(task)
            }
            isProcessing = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun buildRequest(
        task: DownloadTask,
        outputTemplate: String,
        playerClient: String
    ): YoutubeDLRequest = YoutubeDLRequest(task.url).apply {
        addOption("--no-playlist")
        addOption("--newline")
        addOption("--no-check-certificates")
        addOption("--geo-bypass")
        addOption("--retries", "5")
        addOption("--fragment-retries", "5")
        addOption("--extractor-args", "youtube:player_client=$playerClient")
        addOption("-o", outputTemplate)

        if (task.format.isAudio) {
            addOption("-f", "bestaudio/best")
            addOption("-x")
            addOption("--audio-format", "mp3")
            addOption("--audio-quality", "${task.format.audioBitrateKbps}K")
        } else if (task.format == DownloadFormat.ORIGINAL || task.format.heightLimit <= 0) {
            addOption("-f", "bestvideo+bestaudio/best")
            addOption("--merge-output-format", "mp4")
        } else {
            val h = task.format.heightLimit
            addOption(
                "-f",
                "bestvideo[height<=$h]+bestaudio/best[height<=$h]/bestvideo+bestaudio/best"
            )
            addOption("--merge-output-format", "mp4")
        }
    }

    private fun executeTask(task: DownloadTask) {
        val workDir = File(getExternalFilesDir("work"), task.taskId).apply { mkdirs() }
        val safeTitle = UrlUtils.sanitizeFileName(task.title)
        val outputTemplate = File(workDir, "$safeTitle.%(ext)s").absolutePath

        DownloadManager.updateTask(task.taskId) {
            it.copy(status = DownloadStatus.DOWNLOADING, speedOrEta = "0% • Connecting…")
        }

        var lastNotifyTime = 0L
        val progressCallback: (Float, Long, String) -> Unit = { progress, etaInSeconds, line ->
            val pct = progress.toInt().coerceIn(0, 100)
            val speedRaw = SPEED_REGEX.find(line)?.groupValues?.getOrNull(1)
                ?.replace("KiB/s", "KB/s")
                ?.replace("MiB/s", "MB/s")
                ?.replace("GiB/s", "GB/s")
            val sizeRaw = SIZE_REGEX.find(line)?.groupValues?.getOrNull(1)
                ?.replace("KiB", "KB")
                ?.replace("MiB", "MB")

            val statusText = buildString {
                append("$pct%")
                if (!speedRaw.isNullOrBlank()) append(" • $speedRaw")
                if (!sizeRaw.isNullOrBlank()) append(" • $sizeRaw")
                if (etaInSeconds > 0) append(" • ${etaInSeconds}s")
            }

            DownloadManager.updateTask(task.taskId) {
                it.copy(progress = pct, speedOrEta = statusText)
            }

            val now = System.currentTimeMillis()
            if (now - lastNotifyTime > 400L) {
                lastNotifyTime = now
                updateNotification(
                    title = task.title,
                    text = statusText,
                    progress = pct,
                    indeterminate = pct <= 0
                )
            }
        }

        try {
            try {
                YoutubeDL.getInstance().execute(
                    buildRequest(task, outputTemplate, "android,web"),
                    task.taskId,
                    progressCallback
                )
            } catch (firstErr: Exception) {
                workDir.listFiles()?.forEach { it.delete() }
                YoutubeDL.getInstance().execute(
                    buildRequest(task, outputTemplate, "ios,tv_embedded"),
                    task.taskId,
                    progressCallback
                )
            }

            val outputFile = workDir.listFiles()
                ?.firstOrNull { it.isFile && it.length() > 0L }
                ?: throw IllegalStateException("Output file missing after download")

            val savedUri = MediaStoreSaver.saveDownloadedMedia(
                context = applicationContext,
                sourceFile = outputFile,
                displayName = safeTitle,
                isAudio = task.format.isAudio
            ) ?: throw IllegalStateException("Failed to save to MediaStore")

            val completedTask = task.copy(
                progress = 100,
                status = DownloadStatus.COMPLETED,
                speedOrEta = "Saved • ${task.format.label}",
                savedContentUri = savedUri.toString(),
                fileName = outputFile.name
            )
            HistoryRepository.addCompletedDownload(applicationContext, completedTask)
            HistoryRepository.addHistory(
                applicationContext,
                HistoryItem(
                    id = task.taskId,
                    title = task.title,
                    channel = task.format.label,
                    thumbnailUrl = task.thumbnailUrl,
                    url = savedUri.toString(),
                    durationSeconds = 0L,
                    type = HistoryType.DOWNLOADED,
                    qualityLabel = task.format.label
                )
            )
            DownloadManager.removeActiveTask(task.taskId)
            val folderHint = if (task.format.isAudio) "Music/YufiPlay/Music" else "Movies/YufiPlay/Video"
            showCompletionNotification(completedTask, savedUri, "100% Complete • Saved in $folderHint")
        } catch (e: Exception) {
            DownloadManager.updateTask(task.taskId) {
                it.copy(status = DownloadStatus.FAILED, speedOrEta = e.message ?: "Failed")
            }
            showFailureNotification("Download Failed: ${task.title}", e.message ?: "Unknown error")
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun startForegroundNotification(text: String, progress: Int, indeterminate: Boolean) {
        val notification = buildProgressNotification("YufiPlay Downloader", text, progress, indeterminate)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID_PROGRESS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID_PROGRESS, notification)
        }
    }

    private fun updateNotification(title: String, text: String, progress: Int, indeterminate: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(
            NOTIFICATION_ID_PROGRESS,
            buildProgressNotification(title, text, progress, indeterminate)
        )
    }

    private fun buildProgressNotification(
        title: String,
        text: String,
        progress: Int,
        indeterminate: Boolean
    ) = NotificationCompat.Builder(this, YufiPlayApp.CHANNEL_DOWNLOADS)
        .setSmallIcon(R.drawable.ic_download)
        .setContentTitle(title)
        .setContentText(text)
        .setSubText(if (indeterminate) "Starting" else "$progress%")
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .setProgress(100, progress, indeterminate)
        .build()

    private fun showCompletionNotification(task: DownloadTask, savedUri: Uri, message: String) {
        val playIntent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_LOCAL_URI, savedUri.toString())
            putExtra(
                PlayerActivity.EXTRA_VIDEO,
                VideoItem(
                    id = task.taskId,
                    title = task.title,
                    channel = task.format.label,
                    durationSeconds = 0L,
                    thumbnailUrl = task.thumbnailUrl,
                    webpageUrl = task.url
                )
            )
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            (System.currentTimeMillis() % 100000).toInt(),
            playIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nm = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, YufiPlayApp.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_yufiplay_logo)
            .setContentTitle("Downloaded: ${task.title}")
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        nm.notify((System.currentTimeMillis() % 100000).toInt() + 200, notification)
    }

    private fun showFailureNotification(title: String, message: String) {
        val nm = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, YufiPlayApp.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_yufiplay_logo)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        nm.notify((System.currentTimeMillis() % 100000).toInt() + 500, notification)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID_PROGRESS = 1001
        private val SPEED_REGEX = Regex("at\\s+([0-9.]+[KMG]i?B/s)")
        private val SIZE_REGEX = Regex("of\\s+~?([0-9.]+[KMG]i?B)")
    }
}
