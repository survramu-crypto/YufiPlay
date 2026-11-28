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

    companion object {
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_LANGUAGE = "language_code"
        private const val KEY_LAST_CLIPBOARD_URL = "last_clipboard_url"
        private const val KEY_STATUS_TREE_URI = "status_tree_uri"
        private const val KEY_BG_AUDIO = "background_audio"
    }
}
