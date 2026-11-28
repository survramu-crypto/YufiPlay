package com.yufiplay.app.util

import java.util.regex.Pattern

object UrlUtils {
    private val URL_PATTERN = Pattern.compile(
        "(https?://(?:www\\.|m\\.|mobile\\.|music\\.)?(?:youtube\\.com|youtu\\.be|instagram\\.com|facebook\\.com|fb\\.watch|fb\\.com)[^\\s\"'<>]*)",
        Pattern.CASE_INSENSITIVE
    )

    fun extractSupportedUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val matcher = URL_PATTERN.matcher(text.trim())
        if (matcher.find()) {
            val raw = matcher.group(1) ?: return null
            return raw.trimEnd('.', ',', ';', ')', ']', '}')
        }
        return null
    }

    fun isSupportedDirectUrl(input: String): Boolean {
        return extractSupportedUrl(input) != null
    }

    fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(90)
            .ifBlank { "YufiPlay_${System.currentTimeMillis()}" }
    }
}
