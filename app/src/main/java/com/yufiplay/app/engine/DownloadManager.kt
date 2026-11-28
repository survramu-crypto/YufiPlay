package com.yufiplay.app.engine

import android.content.Context
import android.content.Intent
import android.os.Build
import com.yausername.youtubedl_android.YoutubeDL
import com.yufiplay.app.data.DownloadFormat
import com.yufiplay.app.data.DownloadStatus
import com.yufiplay.app.data.DownloadTask
import com.yufiplay.app.data.VideoItem
import com.yufiplay.app.service.DownloadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object DownloadManager {
    private val _activeTasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val activeTasks: StateFlow<List<DownloadTask>> = _activeTasks.asStateFlow()

    private val pendingQueue = ArrayDeque<DownloadTask>()

    fun enqueueDownload(context: Context, video: VideoItem, format: DownloadFormat) {
        val task = DownloadTask(
            taskId = UUID.randomUUID().toString(),
            title = video.title,
            url = video.webpageUrl,
            thumbnailUrl = video.thumbnailUrl,
            format = format,
            status = DownloadStatus.QUEUED
        )
        synchronized(this) {
            pendingQueue.addLast(task)
            _activeTasks.value = _activeTasks.value + task
        }

        val intent = Intent(context, DownloadService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    @Synchronized
    fun pollNextTask(): DownloadTask? = pendingQueue.removeFirstOrNull()

    @Synchronized
    fun updateTask(taskId: String, transform: (DownloadTask) -> DownloadTask) {
        _activeTasks.value = _activeTasks.value.map {
            if (it.taskId == taskId) transform(it) else it
        }
    }

    @Synchronized
    fun removeActiveTask(taskId: String) {
        _activeTasks.value = _activeTasks.value.filterNot { it.taskId == taskId }
    }

    fun cancelTask(taskId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(taskId) }
        synchronized(this) {
            pendingQueue.removeAll { it.taskId == taskId }
        }
        updateTask(taskId) { it.copy(status = DownloadStatus.CANCELLED) }
        removeActiveTask(taskId)
    }
}
