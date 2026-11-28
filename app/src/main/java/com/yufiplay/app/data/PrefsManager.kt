package com.yufiplay.app.data

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("yufiplay_prefs", Context.MODE_PRIVATE)

    var isDarkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

    var languageCode: String
        get() = prefs.getString(KEY_LANGUAGE, "en") ?: "en"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    var lastHandledClipboardUrl: String?
        get() = prefs.getString(KEY_LAST_CLIPBOARD_URL, null)
        set(value) = prefs.edit().putString(KEY_LAST_CLIPBOARD_URL, value).apply()

    var statusTreeUri: String?
        get() = prefs.getString(KEY_STATUS_TREE_URI, null)
        set(value) = prefs.edit().putString(KEY_STATUS_TREE_URI, value).apply()

    var backgroundAudioEnabled: Boolean
        get() = prefs.getBoolean(KEY_BG_AUDIO, true)
        set(value) = prefs.edit().putBoolean(KEY_BG_AUDIO, value).apply()

    var autoPlayNext: Boolean
        get() = prefs.getBoolean(KEY_AUTO_PLAY_NEXT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_PLAY_NEXT, value).apply()

    var userName: String
        get() = prefs.getString(KEY_USER_NAME, "YufiPlay User") ?: "YufiPlay User"
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    var defaultQualityName: String
        get() = prefs.getString(KEY_DEFAULT_QUALITY, DownloadFormat.ORIGINAL.name)
            ?: DownloadFormat.ORIGINAL.name
        set(value) = prefs.edit().putString(KEY_DEFAULT_QUALITY, value).apply()

    var lastEngineUpdateCheck: Long
        get() = prefs.getLong(KEY_ENGINE_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_ENGINE_CHECK, value).apply()

    companion object {
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_LANGUAGE = "language_code"
        private const val KEY_LAST_CLIPBOARD_URL = "last_clipboard_url"
        private const val KEY_STATUS_TREE_URI = "status_tree_uri"
        private const val KEY_BG_AUDIO = "background_audio"
        private const val KEY_AUTO_PLAY_NEXT = "auto_play_next"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_DEFAULT_QUALITY = "default_quality"
        private const val KEY_ENGINE_CHECK = "last_engine_check"
    }
}
