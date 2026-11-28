package com.yufiplay.app.data

import android.net.Uri
import java.io.Serializable

data class VideoComment(
    val author: String,
    val text: String,
    val likes: String = "",
    val timeAgo: String = ""
) : Serializable

data class VideoItem(
    val id: String,
    val title: String,
    val channel: String,
    val durationSeconds: Long,
    val thumbnailUrl: String,
    val webpageUrl: String,
    val channelUrl: String = "",
    val subscriberCountText: String = "",
    val likeCountText: String = "",
    val dislikeCountText: String = "",
    val commentCountText: String = "",
    val viewCountText: String = "",
    val comments: List<VideoComment> = emptyList()
) : Serializable {
    fun formattedDuration(): String {
        if (durationSeconds <= 0L) return "HD"
        val hrs = durationSeconds / 3600
        val mins = (durationSeconds % 3600) / 60
        val secs = durationSeconds % 60
        return if (hrs > 0) {
            String.format("%d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }
}

enum class DownloadFormat(
    val label: String,
    val isAudio: Boolean,
    val heightLimit: Int = 0,
    val audioBitrateKbps: Int = 0
) {
    ORIGINAL("Original Quality (Best)", false, heightLimit = 0),
    MP4_1080("1080p Full HD MP4", false, heightLimit = 1080),
    MP4_720("720p HD MP4", false, heightLimit = 720),
    MP4_480("480p SD MP4", false, heightLimit = 480),
    MP4_360("360p MP4", false, heightLimit = 360),
    MP4_240("240p MP4", false, heightLimit = 240),
    MP4_144("144p MP4", false, heightLimit = 144),
    MP3_320("320kbps MP3 Music", true, audioBitrateKbps = 320),
    MP3_192("192kbps MP3 Music", true, audioBitrateKbps = 192),
    MP3_128("128kbps MP3 Music", true, audioBitrateKbps = 128)
}

enum class DownloadStatus {
    QUEUED, DOWNLOADING, COMPLETED, FAILED, CANCELLED
}

data class DownloadTask(
    val taskId: String,
    val title: String,
    val url: String,
    val thumbnailUrl: String,
    val format: DownloadFormat,
    val progress: Int = 0,
    val speedOrEta: String = "",
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val savedContentUri: String? = null,
    val fileName: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

enum class HistoryType { WATCHED, DOWNLOADED }

data class HistoryItem(
    val id: String,
    val title: String,
    val channel: String,
    val thumbnailUrl: String,
    val url: String,
    val durationSeconds: Long,
    val type: HistoryType,
    val qualityLabel: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class SearchHistoryItem(
    val queryOrUrl: String,
    val isUrl: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class StatusMediaItem(
    val uri: Uri,
    val name: String,
    val isVideo: Boolean,
    val lastModified: Long
)
