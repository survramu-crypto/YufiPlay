package com.yufiplay.app.engine

import android.content.Context
import com.google.gson.JsonParser
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yufiplay.app.YufiPlayApp
import com.yufiplay.app.data.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object YtDlpEngine {

    private suspend fun ensureReady() {
        YufiPlayApp.engineReady.await()
    }

    suspend fun searchYouTube(query: String, count: Int = 15): List<VideoItem> =
        withContext(Dispatchers.IO) {
            ensureReady()
            val searchTarget = "ytsearch$count:$query"
            val request = YoutubeDLRequest(searchTarget).apply {
                addOption("--dump-single-json")
                addOption("--flat-playlist")
                addOption("--no-warnings")
                addOption("--ignore-errors")
            }

            val response = YoutubeDL.getInstance().execute(request)
            val jsonOut = response.out ?: return@withContext emptyList()
            val root = JsonParser.parseString(jsonOut).asJsonObject
            val entries = root.getAsJsonArray("entries") ?: return@withContext emptyList()

            val results = mutableListOf<VideoItem>()
            for (el in entries) {
                if (!el.isJsonObject) continue
                val obj = el.asJsonObject
                val id = obj.get("id")?.takeIf { !it.isJsonNull }?.asString ?: continue
                val title = obj.get("title")?.takeIf { !it.isJsonNull }?.asString ?: "Untitled Video"
                val channel = obj.get("uploader")?.takeIf { !it.isJsonNull }?.asString
                    ?: obj.get("channel")?.takeIf { !it.isJsonNull }?.asString
                    ?: "YouTube"
                val duration = obj.get("duration")?.takeIf { !it.isJsonNull }?.asDouble?.toLong() ?: 0L

                val rawUrl = obj.get("webpage_url")?.takeIf { !it.isJsonNull }?.asString
                    ?: obj.get("url")?.takeIf { !it.isJsonNull }?.asString
                val webpageUrl = if (!rawUrl.isNullOrBlank() && rawUrl.startsWith("http")) {
                    rawUrl
                } else {
                    "https://www.youtube.com/watch?v=$id"
                }

                var thumb = obj.get("thumbnail")?.takeIf { !it.isJsonNull }?.asString
                if (thumb.isNullOrBlank() && obj.has("thumbnails") && obj.get("thumbnails").isJsonArray) {
                    val thumbs = obj.getAsJsonArray("thumbnails")
                    if (thumbs.size() > 0) {
                        thumb = thumbs.get(thumbs.size() - 1).asJsonObject.get("url")?.asString
                    }
                }
                if (thumb.isNullOrBlank()) {
                    thumb = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
                }

                results.add(
                    VideoItem(
                        id = id,
                        title = title,
                        channel = channel,
                        durationSeconds = duration,
                        thumbnailUrl = thumb,
                        webpageUrl = webpageUrl
                    )
                )
            }
            results
        }

    suspend fun extractDirectInfo(url: String): VideoItem = withContext(Dispatchers.IO) {
        ensureReady()
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("-f", "best[ext=mp4]/best")
        }
        val info = YoutubeDL.getInstance().getInfo(request)
        VideoItem(
            id = info.id ?: url.hashCode().toString(),
            title = info.title ?: "Public Video",
            channel = info.uploader ?: "Creator",
            durationSeconds = info.duration.toLong(),
            thumbnailUrl = info.thumbnail ?: "",
            webpageUrl = url
        )
    }

    suspend fun resolvePlayableStreamUrl(url: String): Pair<String, VideoItem> =
        withContext(Dispatchers.IO) {
            ensureReady()
            val request = YoutubeDLRequest(url).apply {
                addOption("--no-playlist")
                addOption("-f", "best[ext=mp4]/best")
            }
            val info = YoutubeDL.getInstance().getInfo(request)
            val streamUrl = info.url
                ?: throw IllegalStateException("No playable stream URL found")
            val item = VideoItem(
                id = info.id ?: url.hashCode().toString(),
                title = info.title ?: "YufiPlay Video",
                channel = info.uploader ?: "Creator",
                durationSeconds = info.duration.toLong(),
                thumbnailUrl = info.thumbnail ?: "",
                webpageUrl = url
            )
            streamUrl to item
        }

    suspend fun updateEngine(context: Context): YoutubeDL.UpdateStatus? =
        withContext(Dispatchers.IO) {
            ensureReady()
            YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
        }
}
