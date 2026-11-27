package com.yufiplay.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yufiplay.app.data.HistoryRepository
import com.yufiplay.app.data.PrefsManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class YufiPlayApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        val prefs = PrefsManager(this)
        AppCompatDelegate.setDefaultNightMode(
            if (prefs.isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(prefs.languageCode)
        )

        HistoryRepository.init(this)
        createNotificationChannels()

        appScope.launch {
            try {
                YoutubeDL.getInstance().init(applicationContext)
                FFmpeg.getInstance().init(applicationContext)
                engineReady.complete(true)
                Log.i(TAG, "YoutubeDL and FFmpeg initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize YoutubeDL/FFmpeg", e)
                engineReady.complete(false)
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            val downloadChannel = NotificationChannel(
                CHANNEL_DOWNLOADS,
                "YufiPlay Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time video & audio download progress"
            }
            nm.createNotificationChannel(downloadChannel)

            val playbackChannel = NotificationChannel(
                CHANNEL_PLAYBACK,
                "YufiPlay Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for background audio and video playback"
            }
            nm.createNotificationChannel(playbackChannel)
        }
    }

    companion object {
        private const val TAG = "YufiPlayApp"
        const val CHANNEL_DOWNLOADS = "yufiplay_downloads"
        const val CHANNEL_PLAYBACK = "yufiplay_playback"

        val engineReady = CompletableDeferred<Boolean>()
    }
}
