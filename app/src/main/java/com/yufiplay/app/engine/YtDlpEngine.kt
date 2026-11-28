package com.yufiplay.app.engine

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yufiplay.app.YufiPlayApp
import com.yufiplay.app.data.DownloadFormat
import com.yufiplay.app.data.VideoComment
import com.yufiplay.app.data.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

object YtDlpEngine {

    private suspend fun ensureReady() {
        YufiPlayApp.engineReady.await()
    }

    private fun YoutubeDLRequest.applyBypassFlags(client: String = "android,web") {
        addOption("--extractor-args", "youtube:player_client=$client")
        addOption("--no-check-certificates")
        addOption("--geo-bypass")
        addOption("--no-warnings")
        addOption("--ignore-errors")
    }

    suspend fun searchYouTube(query: String, count: Int = 18): List<VideoItem> =
        withContext(Dispatchers.IO) {
            ensureReady()
            val searchTarget = "ytsearch$count:$query"
            val request = YoutubeDLRequest(searchTarget).apply {
                addOption("--dump-single-json")
                addOption("--flat-playlist")
                applyBypassFlags("web")
            }

            val response = YoutubeDL.getInstance().execute(request)
            val jsonOut = response.out ?: return@withContext emptyList()
            val root = JsonParser.parseString(jsonOut).asJsonObject
            val entries = root.getAsJsonArray("entries") ?: return@withContext emptyList()

            parseEntries(entries)
        }

    suspend fun fetchChannelVideos(
        channelUrl: String,
        channelName: String,
        count: Int = 25
    ): List<VideoItem> = withContext(Dispatchers.IO) {
        ensureReady()
        val target = if (channelUrl.startsWith("http")) {
            val trimmed = channelUrl.trimEnd('/')
            if (trimmed.endsWith("/videos")) trimmed else "$trimmed/videos"
        } else {
            "ytsearch$count:$channelName"
        }

        val request = YoutubeDLRequest(target).apply {
            addOption("--dump-single-json")
            addOption("--flat-playlist")
            addOption("--playlist-end", count.toString())
            applyBypassFlags("web")
        }

        val response = YoutubeDL.getInstance().execute(request)
        val jsonOut = response.out ?: return@withContext emptyList()
        val root = JsonParser.parseString(jsonOut).asJsonObject
        val entries = root.getAsJsonArray("entries")
        if (entries != null && entries.size() > 0) {
            parseEntries(entries, fallbackChannel = channelName, fallbackChannelUrl = channelUrl)
        } else {
            searchYouTube(channelName, count)
        }
    }

    private fun parseEntries(
        entries: com.google.gson.JsonArray,
        fallbackChannel: String = "YouTube",
        fallbackChannelUrl: String = ""
    ): List<VideoItem> {
        val results = mutableListOf<VideoItem>()
        for (el in entries) {
            if (!el.isJsonObject) continue
            val obj = el.asJsonObject
            val id = obj.getStringSafe("id") ?: continue
            val title = obj.getStringSafe("title") ?: "Untitled Video"
            if (title == "[Deleted video]" || title == "[Private video]") continue

            val channel = obj.getStringSafe("uploader")
                ?: obj.getStringSafe("channel")
                ?: fallbackChannel
            val chUrl = obj.getStringSafe("uploader_url")
                ?: obj.getStringSafe("channel_url")
                ?: fallbackChannelUrl

            val duration = obj.getLongSafe("duration")
            val views = formatCompactCount(obj.getLongSafe("view_count"), "views")
            val subs = formatCompactCount(obj.getLongSafe("channel_follower_count"), "Subscribers")

            val rawUrl = obj.getStringSafe("webpage_url") ?: obj.getStringSafe("url")
            val webpageUrl = if (!rawUrl.isNullOrBlank() && rawUrl.startsWith("http")) {
                rawUrl
            } else {
                "https://www.youtube.com/watch?v=$id"
            }

            var thumb = obj.getStringSafe("thumbnail")
            if (thumb.isNullOrBlank() && obj.has("thumbnails") && obj.get("thumbnails").isJsonArray) {
                val thumbs = obj.getAsJsonArray("thumbnails")
                if (thumbs.size() > 0) {
                    thumb = thumbs.get(thumbs.size() - 1).asJsonObject.getStringSafe("url")
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
                    webpageUrl = webpageUrl,
                    channelUrl = chUrl,
                    subscriberCountText = subs,
                    viewCountText = views
                )
            )
        }
        return results
    }

    suspend fun extractDirectInfo(url: String): VideoItem = withContext(Dispatchers.IO) {
        ensureReady()
        val request = YoutubeDLRequest(url).apply {
            addOption("--dump-single-json")
            addOption("--no-playlist")
            applyBypassFlags("android,web")
        }
        val response = YoutubeDL.getInstance().execute(request)
        val root = JsonParser.parseString(response.out).asJsonObject
        buildVideoItemFromJson(root, url)
    }

    suspend fun resolvePlayableStreamUrl(
        url: String,
        useFallbackClient: Boolean = false
    ): Pair<String, VideoItem> = withContext(Dispatchers.IO) {
        ensureReady()
        val client = if (useFallbackClient) "ios,tv_embedded" else "android,web"
        val request = YoutubeDLRequest(url).apply {
            addOption("--dump-single-json")
            addOption("--no-playlist")
            addOption("-f", "best[ext=mp4][acodec!=none][vcodec!=none]/best[acodec!=none][vcodec!=none]/best")
            applyBypassFlags(client)
        }
        val response = YoutubeDL.getInstance().execute(request)
        val root = JsonParser.parseString(response.out).asJsonObject

        var streamUrl = root.getStringSafe("url")
        if (streamUrl.isNullOrBlank() && root.has("formats") && root.get("formats").isJsonArray) {
            val formats = root.getAsJsonArray("formats")
            for (i in formats.size() - 1 downTo 0) {
                val f = formats.get(i).asJsonObject
                val vcodec = f.getStringSafe("vcodec") ?: "none"
                val acodec = f.getStringSafe("acodec") ?: "none"
                val fUrl = f.getStringSafe("url")
                if (vcodec != "none" && acodec != "none" && !fUrl.isNullOrBlank()) {
                    streamUrl = fUrl
                    break
                }
            }
        }

        if (streamUrl.isNullOrBlank()) {
            throw IllegalStateException("No playable stream URL found")
        }

        val item = buildVideoItemFromJson(root, url)
        streamUrl to item
    }

    suspend fun fetchFormatSizes(url: String): Map<DownloadFormat, String> =
        withContext(Dispatchers.IO) {
            ensureReady()
            val sizeMap = mutableMapOf<DownloadFormat, String>()
            try {
                val request = YoutubeDLRequest(url).apply {
                    addOption("--dump-single-json")
                    addOption("--no-playlist")
                    applyBypassFlags("android,web")
                }
                val response = YoutubeDL.getInstance().execute(request)
                val root = JsonParser.parseString(response.out).asJsonObject
                val duration = root.getLongSafe("duration").coerceAtLeast(10L)
                val formats = root.getAsJsonArray("formats")

                var bestAudioBytes = 0L
                val heightToBytes = mutableMapOf<Int, Long>()

                if (formats != null) {
                    for (el in formats) {
                        if (!el.isJsonObject) continue
                        val f = el.asJsonObject
                        val vcodec = f.getStringSafe("vcodec") ?: "none"
                        val acodec = f.getStringSafe("acodec") ?: "none"
                        val height = f.getLongSafe("height").toInt()
                        val bytes = f.getLongSafe("filesize").takeIf { it > 0 }
                            ?: f.getLongSafe("filesize_approx")

                        if (vcodec == "none" && acodec != "none" && bytes > bestAudioBytes) {
                            bestAudioBytes = bytes
                        }
                        if (vcodec != "none" && height > 0 && bytes > 0) {
                            val existing = heightToBytes[height] ?: 0L
                            if (bytes > existing) heightToBytes[height] = bytes
                        }
                    }
                }

                val maxHeight = heightToBytes.keys.maxOrNull() ?: 1080
                for (fmt in DownloadFormat.values()) {
                    if (fmt.isAudio) {
                        val estBytes = (fmt.audioBitrateKbps * 1000L / 8L) * duration
                        sizeMap[fmt] = formatBytes(estBytes)
                    } else {
                        val targetH = if (fmt == DownloadFormat.ORIGINAL) maxHeight else fmt.heightLimit
                        val vBytes = heightToBytes[targetH]
                            ?: heightToBytes.entries.minByOrNull { kotlin.math.abs(it.key - targetH) }?.value
                        if (vBytes != null && vBytes > 0) {
                            sizeMap[fmt] = formatBytes(vBytes + bestAudioBytes)
                        } else {
                            val kbps = when (targetH) {
                                in 2000..4320 -> 14000L
                                in 1400..1999 -> 7500L
                                in 1000..1399 -> 2600L
                                in 700..999 -> 1400L
                                in 450..699 -> 800L
                                in 300..449 -> 500L
                                in 200..299 -> 300L
                                else -> 180L
                            }
                            sizeMap[fmt] = "~" + formatBytes((kbps * 1000L / 8L) * duration)
                        }
                    }
                }
            } catch (_: Exception) {
                for (fmt in DownloadFormat.values()) {
                    sizeMap[fmt] = if (fmt.isAudio) "MP3" else "MP4"
                }
            }
            sizeMap
        }

    private fun buildVideoItemFromJson(root: JsonObject, fallbackUrl: String): VideoItem {
        val id = root.getStringSafe("id") ?: fallbackUrl.hashCode().toString()
        val title = root.getStringSafe("title") ?: "YufiPlay Video"
        val channel = root.getStringSafe("uploader")
            ?: root.getStringSafe("channel")
            ?: "Creator"
        val channelUrl = root.getStringSafe("uploader_url")
            ?: root.getStringSafe("channel_url")
            ?: ""
        val duration = root.getLongSafe("duration")
        val thumb = root.getStringSafe("thumbnail")
            ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"
        val webpageUrl = root.getStringSafe("webpage_url") ?: fallbackUrl

        val subsCount = root.getLongSafe("channel_follower_count")
        val likeCount = root.getLongSafe("like_count")
        val dislikeCount = root.getLongSafe("dislike_count")
        val commentCount = root.getLongSafe("comment_count")
        val viewCount = root.getLongSafe("view_count")

        val commentsList = mutableListOf<VideoComment>()
        if (root.has("comments") && root.get("comments").isJsonArray) {
            val arr = root.getAsJsonArray("comments")
            for (el in arr) {
                if (!el.isJsonObject) continue
                val c = el.asJsonObject
                val author = c.getStringSafe("author") ?: "Viewer"
                val text = c.getStringSafe("text") ?: continue
                val likes = formatCompactCount(c.getLongSafe("like_count"), "")
                val time = c.getStringSafe("_time_text") ?: ""
                commentsList.add(VideoComment(author, text, likes, time))
                if (commentsList.size >= 15) break
            }
        }

        return VideoItem(
            id = id,
            title = title,
            channel = channel,
            durationSeconds = duration,
            thumbnailUrl = thumb,
            webpageUrl = webpageUrl,
            channelUrl = channelUrl,
            subscriberCountText = formatCompactCount(subsCount, "Subscribers").ifEmpty { "Creator Channel" },
            likeCountText = formatCompactCount(likeCount, "").ifEmpty { "Like" },
            dislikeCountText = formatCompactCount(dislikeCount, "").ifEmpty { "Dislike" },
            commentCountText = formatCompactCount(commentCount, "").ifEmpty { "Comments" },
            viewCountText = formatCompactCount(viewCount, "views"),
            comments = commentsList
        )
    }

    private fun JsonObject.getStringSafe(key: String): String? =
        get(key)?.takeIf { !it.isJsonNull }?.asString

    private fun JsonObject.getLongSafe(key: String): Long =
        runCatching { get(key)?.takeIf { !it.isJsonNull }?.asDouble?.toLong() ?: 0L }
            .getOrDefault(0L)

    fun formatCompactCount(count: Long, suffix: String): String {
        if (count <= 0L) return ""
        val num = when {
            count >= 1_000_000_000L -> String.format(Locale.US, "%.1fB", count / 1_000_000_000.0)
            count >= 1_000_000L -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
            count >= 1_000L -> String.format(Locale.US, "%.1fK", count / 1_000.0)
            else -> count.toString()
        }
        return if (suffix.isBlank()) num else "$num $suffix"
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return ""
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            else -> String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
        }
    }

    suspend fun updateEngine(context: Context): YoutubeDL.UpdateStatus? =
        withContext(Dispatchers.IO) {
            ensureReady()
            YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel._NIGHTLY)
        }
}
