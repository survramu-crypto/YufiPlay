package com.yufiplay.app.ui.main

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yufiplay.app.R
import com.yufiplay.app.data.HistoryRepository
import com.yufiplay.app.data.VideoItem
import com.yufiplay.app.databinding.FragmentHomeBinding
import com.yufiplay.app.engine.YtDlpEngine
import com.yufiplay.app.ui.adapters.SearchHistoryAdapter
import com.yufiplay.app.ui.adapters.VideoAdapter
import com.yufiplay.app.ui.player.DownloadBottomSheetFragment
import com.yufiplay.app.ui.player.PlayerActivity
import com.yufiplay.app.util.UrlUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var videoAdapter: VideoAdapter
    private lateinit var searchHistoryAdapter: SearchHistoryAdapter

    private var fetchJob: Job? = null
    private var isAllTabSelected = true
    private var currentCategoryQuery = "trending india"
    private var currentFetchCount = 18
    private var isLoadingMore = false
    private var refreshCounter = 0
    private val currentFeedList = mutableListOf<VideoItem>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        videoAdapter = VideoAdapter(
            onPlayClick = { openPlayer(it) },
            onDownloadClick = { openDownloadSheet(it) },
            onShareClick = { shareVideo(it) }
        )

        val layoutManager = LinearLayoutManager(requireContext())
        binding.rvVideos.layoutManager = layoutManager
        binding.rvVideos.adapter = videoAdapter

        // Infinite scroll listener
        binding.rvVideos.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy <= 0 || isLoadingMore) return
                val totalItemCount = layoutManager.itemCount
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (totalItemCount >= 6 && lastVisible >= totalItemCount - 4) {
                    loadMoreVideos()
                }
            }
        })

        setupSearchBarAndHistory()
        setupCategoryChips()

        binding.swipeRefreshHome.setOnRefreshListener {
            refreshCounter++
            currentFetchCount = 18
            if (isAllTabSelected) {
                loadPersonalizedAllFeed(reset = true)
            } else {
                val variedQuery = if (refreshCounter % 2 == 1) {
                    "$currentCategoryQuery new"
                } else {
                    "$currentCategoryQuery latest"
                }
                loadFeed(variedQuery, count = currentFetchCount, reset = true)
            }
        }

        // Load "All" tab by default
        isAllTabSelected = true
        loadPersonalizedAllFeed(reset = true)
    }

    private fun setupSearchBarAndHistory() {
        searchHistoryAdapter = SearchHistoryAdapter(
            onItemClick = { item ->
                binding.cardSearchHistory.visibility = View.GONE
                binding.etSearch.setText(item.queryOrUrl)
                binding.etSearch.setSelection(item.queryOrUrl.length)
                hideKeyboard()
                handleSearchOrUrl(item.queryOrUrl)
            },
            onDeleteClick = { item ->
                HistoryRepository.removeSearchHistory(requireContext(), item.queryOrUrl)
            }
        )
        binding.rvSearchHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSearchHistory.adapter = searchHistoryAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                HistoryRepository.searchHistoryFlow.collect { list ->
                    searchHistoryAdapter.submitList(list)
                    if (list.isEmpty()) {
                        binding.cardSearchHistory.visibility = View.GONE
                    }
                }
            }
        }

        binding.etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && HistoryRepository.searchHistoryFlow.value.isNotEmpty()) {
                binding.cardSearchHistory.visibility = View.VISIBLE
            }
        }

        binding.etSearch.setOnClickListener {
            if (HistoryRepository.searchHistoryFlow.value.isNotEmpty()) {
                binding.cardSearchHistory.visibility = View.VISIBLE
            }
        }

        binding.btnCloseSearchHistory.setOnClickListener {
            binding.cardSearchHistory.visibility = View.GONE
            hideKeyboard()
        }

        binding.btnClearAllSearchHistory.setOnClickListener {
            HistoryRepository.clearSearchHistory(requireContext())
            binding.cardSearchHistory.visibility = View.GONE
        }

        binding.etSearch.doAfterTextChanged { text ->
            val str = text?.toString().orEmpty()
            binding.btnClearSearch.visibility = if (str.isEmpty()) View.GONE else View.VISIBLE
            val detected = UrlUtils.extractSupportedUrl(str)
            if (detected != null && str.length > 15 && str.contains("://")) {
                binding.cardSearchHistory.visibility = View.GONE
            }
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
            binding.cardDirectUrlResult.visibility = View.GONE
        }

        binding.btnPasteLink.setOnClickListener {
            val clipboard =
                requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipText = clipboard.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.coerceToText(requireContext())?.toString()?.trim()
            if (!clipText.isNullOrBlank()) {
                binding.etSearch.setText(clipText)
                binding.cardSearchHistory.visibility = View.GONE
                hideKeyboard()
                handleSearchOrUrl(clipText)
            } else {
                Toast.makeText(requireContext(), "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        }

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etSearch.text?.toString()?.trim().orEmpty()
                if (query.isNotEmpty()) {
                    binding.cardSearchHistory.visibility = View.GONE
                    hideKeyboard()
                    handleSearchOrUrl(query)
                }
                true
            } else {
                false
            }
        }
    }

    private fun setupCategoryChips() {
        val chipQueries = mapOf(
            R.id.chipTrending to "trending india",
            R.id.chipMusic to "bollywood hindi songs",
            R.id.chipShayari to "hindi shayari status video",
            R.id.chipGaming to "gaming india live highlights",
            R.id.chipMovies to "new hindi movies trailer"
        )

        binding.chipGroupCategories.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            binding.cardSearchHistory.visibility = View.GONE
            binding.cardDirectUrlResult.visibility = View.GONE
            currentFetchCount = 18

            if (checkedId == R.id.chipAll) {
                isAllTabSelected = true
                loadPersonalizedAllFeed(reset = true)
            } else {
                isAllTabSelected = false
                val query = chipQueries[checkedId] ?: return@setOnCheckedStateChangeListener
                currentCategoryQuery = query
                loadFeed(query, count = currentFetchCount, reset = true)
            }
        }
    }

    private fun buildPersonalizedSeedQuery(): String {
        val recentSearches = HistoryRepository.searchHistoryFlow.value
            .filter { !it.isUrl }
            .map { it.queryOrUrl }
        val recentWatched = HistoryRepository.historyFlow.value
            .map { it.channel }
            .filter { it.isNotBlank() && it != "YouTube" && it != "Creator" }

        val pool = mutableListOf(
            "trending videos india",
            "latest hindi songs",
            "viral shorts india",
            "comedy videos india",
            "tech review hindi",
            "cricket highlights india"
        )
        pool.addAll(recentSearches.take(4))
        pool.addAll(recentWatched.take(4))
        return pool.shuffled().first()
    }

    private fun loadPersonalizedAllFeed(reset: Boolean) {
        val seed = buildPersonalizedSeedQuery()
        currentCategoryQuery = seed
        loadFeed(seed, count = currentFetchCount, reset = reset)
    }

    private fun handleSearchOrUrl(input: String) {
        val directUrl = UrlUtils.extractSupportedUrl(input)
        if (directUrl != null) {
            HistoryRepository.addSearchHistory(requireContext(), directUrl, isUrl = true)
            fetchJob?.cancel()
            fetchJob = viewLifecycleOwner.lifecycleScope.launch {
                setLoading(true)
                try {
                    val item = YtDlpEngine.extractDirectInfo(directUrl)
                    setLoading(false)
                    showDirectUrlActionCard(item)
                    openDownloadSheet(item)
                } catch (e: Exception) {
                    setLoading(false)
                    Toast.makeText(
                        requireContext(),
                        e.message ?: "Failed to extract URL",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            HistoryRepository.addSearchHistory(requireContext(), input, isUrl = false)
            binding.cardDirectUrlResult.visibility = View.GONE
            isAllTabSelected = false
            currentCategoryQuery = input
            currentFetchCount = 20
            loadFeed(input, count = currentFetchCount, reset = true)
        }
    }

    private fun showDirectUrlActionCard(item: VideoItem) {
        binding.cardDirectUrlResult.visibility = View.VISIBLE
        binding.tvDirectUrlTitle.text = item.title
        binding.tvDirectUrlChannel.text = "${item.channel} • ${item.formattedDuration()}"
        binding.btnDirectUrlPlay.setOnClickListener { openPlayer(item) }
        binding.btnDirectUrlDownload.setOnClickListener { openDownloadSheet(item) }
    }

    private fun loadFeed(query: String, count: Int, reset: Boolean) {
        fetchJob?.cancel()
        fetchJob = viewLifecycleOwner.lifecycleScope.launch {
            if (reset) setLoading(true)
            binding.tvEmptyHome.visibility = View.GONE
            try {
                val items = YtDlpEngine.searchYouTube(query, count)
                if (reset) {
                    currentFeedList.clear()
                    val finalItems = if (isAllTabSelected) items.shuffled() else items
                    currentFeedList.addAll(finalItems)
                } else {
                    val existingIds = currentFeedList.map { it.id }.toSet()
                    val fresh = items.filterNot { it.id in existingIds }
                    currentFeedList.addAll(if (isAllTabSelected) fresh.shuffled() else fresh)
                }
                videoAdapter.submitList(currentFeedList.toList())
                binding.tvEmptyHome.visibility =
                    if (currentFeedList.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    e.message ?: "Error loading videos",
                    Toast.LENGTH_SHORT
                ).show()
                if (currentFeedList.isEmpty()) {
                    binding.tvEmptyHome.visibility = View.VISIBLE
                }
            } finally {
                setLoading(false)
                isLoadingMore = false
                binding.progressLoadMore.visibility = View.GONE
            }
        }
    }

    private fun loadMoreVideos() {
        isLoadingMore = true
        binding.progressLoadMore.visibility = View.VISIBLE
        currentFetchCount += 15
        val nextQuery = if (isAllTabSelected) {
            buildPersonalizedSeedQuery()
        } else {
            currentCategoryQuery
        }
        loadFeed(nextQuery, count = currentFetchCount, reset = false)
    }

    private fun setLoading(loading: Boolean) {
        binding.swipeRefreshHome.isRefreshing = loading
        binding.progressHome.visibility = if (loading && currentFeedList.isEmpty()) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    private fun openPlayer(item: VideoItem) {
        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_VIDEO, item)
            putExtra(PlayerActivity.EXTRA_QUEUE, ArrayList(currentFeedList.take(20)))
        }
        startActivity(intent)
    }

    private fun openDownloadSheet(item: VideoItem) {
        DownloadBottomSheetFragment.newInstance(item)
            .show(parentFragmentManager, "DownloadSheet")
    }

    private fun shareVideo(item: VideoItem) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, item.title)
            putExtra(Intent.EXTRA_TEXT, "${item.title}\n${item.webpageUrl}")
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
