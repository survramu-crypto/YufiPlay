package com.yufiplay.app.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yufiplay.app.R
import com.yufiplay.app.YufiPlayApp
import com.yufiplay.app.data.DownloadStatus
import com.yufiplay.app.data.DownloadTask
import com.yufiplay.app.data.HistoryItem
import com.yufiplay.app.data.HistoryRepository
import com.yufiplay.app.data.HistoryType
import com.yufiplay.app.engine.DownloadManager
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

    private fun executeTask(task: DownloadTask) {
        val workDir = File(getExternalFilesDir("work"), task.taskId).apply { mkdirs() }
        val safeTitle = UrlUtils.sanitizeFileName(task.title)
        val outputTemplate = File(workDir, "$safeTitle.%(ext)s").absolutePath

        DownloadManager.updateTask(task.taskId) {
            it.copy(status = DownloadStatus.DOWNLOADING, speedOrEta = "Starting…")
        }

        val request = YoutubeDLRequest(task.url).apply {
            addOption("--no-playlist")
            addOption("--newline")
            addOption("-o", outputTemplate)
            if (task.format.isAudio) {
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", "${task.format.audioBitrateKbps}K")
            } else {
                val h = task.format.heightLimit
                addOption(
                    "-f",
                    "bestvideo[height<=$h][ext=mp4]+bestaudio[ext=m4a]/best[height<=$h][ext=mp4]/bestvideo[height<=$h]+bestaudio/best"
                )
                addOption("--merge-output-format", "mp4")
            }
        }

        var lastNotifyTime = 0L

        try {
            YoutubeDL.getInstance().execute(request, task.taskId) { progress, etaInSeconds, line ->
                val pct = progress.toInt().coerceIn(0, 100)
                val speedMatch = SPEED_REGEX.find(line)?.groupValues?.getOrNull(1)
                val statusText = buildString {
                    append("$pct%")
                    if (!speedMatch.isNullOrBlank()) append(" • $speedMatch")
                    if (etaInSeconds > 0) append(" • ETA ${etaInSeconds}s")
                }

                DownloadManager.updateTask(task.taskId) {
                    it.copy(progress = pct, speedOrEta = statusText)
                }

                val now = System.currentTimeMillis()
                if (now - lastNotifyTime > 500L) {
                    lastNotifyTime = now
                    updateNotification(
                        title = task.title,
                        text = statusText,
                        progress = pct,
                        indeterminate = pct <= 0
                    )
                }
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
                speedOrEta = "Saved",
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
            showCompletionNotification(task.title, "${task.format.label} saved to Gallery/Music")
        } catch (e: Exception) {
            DownloadManager.updateTask(task.taskId) {
                it.copy(status = DownloadStatus.FAILED, speedOrEta = e.message ?: "Failed")
            }
            showCompletionNotification("Download Failed: ${task.title}", e.message ?: "Unknown error")
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
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .setProgress(100, progress, indeterminate)
        .build()

    private fun showCompletionNotification(title: String, message: String) {
        val nm = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, YufiPlayApp.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_yufiplay_logo)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        nm.notify((System.currentTimeMillis() % 100000).toInt() + 200, notification)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID_PROGRESS = 1001
        private val SPEED_REGEX = Regex("at\\s+([0-9.]+[KMG]i?B/s)")
    }
}
