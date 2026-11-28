package com.yufiplay.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HistoryRepository {
    private const val PREF_NAME = "yufiplay_history_store"
    private const val KEY_HISTORY = "history_items"
    private const val KEY_COMPLETED_DOWNLOADS = "completed_downloads"
    private const val KEY_SEARCH_HISTORY = "search_url_history"

    private val gson = Gson()

    private val _historyFlow = MutableStateFlow<List<HistoryItem>>(emptyList())
    val historyFlow: StateFlow<List<HistoryItem>> = _historyFlow.asStateFlow()

    private val _completedDownloadsFlow = MutableStateFlow<List<DownloadTask>>(emptyList())
    val completedDownloadsFlow: StateFlow<List<DownloadTask>> = _completedDownloadsFlow.asStateFlow()

    private val _searchHistoryFlow = MutableStateFlow<List<SearchHistoryItem>>(emptyList())
    val searchHistoryFlow: StateFlow<List<SearchHistoryItem>> = _searchHistoryFlow.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        val histJson = prefs.getString(KEY_HISTORY, null)
        if (!histJson.isNullOrBlank()) {
            val type = object : TypeToken<List<HistoryItem>>() {}.type
            _historyFlow.value = runCatching { gson.fromJson<List<HistoryItem>>(histJson, type) }
                .getOrDefault(emptyList())
        }

        val dlJson = prefs.getString(KEY_COMPLETED_DOWNLOADS, null)
        if (!dlJson.isNullOrBlank()) {
            val type = object : TypeToken<List<DownloadTask>>() {}.type
            _completedDownloadsFlow.value =
                runCatching { gson.fromJson<List<DownloadTask>>(dlJson, type) }
                    .getOrDefault(emptyList())
        }

        val searchJson = prefs.getString(KEY_SEARCH_HISTORY, null)
        if (!searchJson.isNullOrBlank()) {
            val type = object : TypeToken<List<SearchHistoryItem>>() {}.type
            _searchHistoryFlow.value =
                runCatching { gson.fromJson<List<SearchHistoryItem>>(searchJson, type) }
                    .getOrDefault(emptyList())
        }
    }

    @Synchronized
    fun addSearchHistory(context: Context, queryOrUrl: String, isUrl: Boolean) {
        val clean = queryOrUrl.trim()
        if (clean.isEmpty()) return
        val item = SearchHistoryItem(
            queryOrUrl = clean,
            isUrl = isUrl,
            timestamp = System.currentTimeMillis()
        )
        val updated = _searchHistoryFlow.value
            .filterNot { it.queryOrUrl.equals(clean, ignoreCase = true) }
            .toMutableList()
            .apply { add(0, item) }
            .take(100)
        _searchHistoryFlow.value = updated
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SEARCH_HISTORY, gson.toJson(updated))
            .apply()
    }

    @Synchronized
    fun removeSearchHistory(context: Context, queryOrUrl: String) {
        val updated = _searchHistoryFlow.value.filterNot {
            it.queryOrUrl.equals(queryOrUrl, ignoreCase = true)
        }
        _searchHistoryFlow.value = updated
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SEARCH_HISTORY, gson.toJson(updated))
            .apply()
    }

    @Synchronized
    fun clearSearchHistory(context: Context) {
        _searchHistoryFlow.value = emptyList()
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SEARCH_HISTORY)
            .apply()
    }

    @Synchronized
    fun addHistory(context: Context, item: HistoryItem) {
        val updated = _historyFlow.value
            .filterNot { it.url == item.url && it.type == item.type }
            .toMutableList()
            .apply { add(0, item) }
            .take(250)
        _historyFlow.value = updated
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY, gson.toJson(updated))
            .apply()
    }

    @Synchronized
    fun clearHistory(context: Context) {
        _historyFlow.value = emptyList()
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_HISTORY)
            .apply()
    }

    @Synchronized
    fun addCompletedDownload(context: Context, task: DownloadTask) {
        val updated = _completedDownloadsFlow.value
            .filterNot { it.taskId == task.taskId }
            .toMutableList()
            .apply { add(0, task) }
            .take(300)
        _completedDownloadsFlow.value = updated
        saveCompletedDownloads(context, updated)
    }

    @Synchronized
    fun removeCompletedDownload(context: Context, taskId: String) {
        val updated = _completedDownloadsFlow.value.filterNot { it.taskId == taskId }
        _completedDownloadsFlow.value = updated
        saveCompletedDownloads(context, updated)
    }

    private fun saveCompletedDownloads(context: Context, list: List<DownloadTask>) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_COMPLETED_DOWNLOADS, gson.toJson(list))
            .apply()
    }
}
