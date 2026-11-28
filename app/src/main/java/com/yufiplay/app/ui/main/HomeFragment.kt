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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yufiplay.app.R
import com.yufiplay.app.data.SearchHistoryItem
import com.yufiplay.app.data.VideoItem
import com.yufiplay.app.databinding.FragmentHomeBinding
import com.yufiplay.app.engine.YtDlpEngine
import com.yufiplay.app.ui.adapters.SearchHistoryAdapter
import com.yufiplay.app.ui.adapters.VideoAdapter
import com.yufiplay.app.ui.player.DownloadBottomSheetFragment
import com.yufiplay.app.ui.player.PlayerActivity
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
    private var currentFetchCount = 16
    private var isLoadingMore = false
    private var refreshCounter = 0
    private val currentFeedList = mutableListOf<VideoItem>()
    private val recentSearches = mutableListOf<SearchHistoryItem>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        videoAdapter = VideoAdapter(
            onPlayClick = { openPlayer(it) },
            onDownloadClick = { openDownloadSheet(it) },
            onShareClick = { shareVideo(it) }
        )

        val layoutManager = LinearLayoutManager(requireContext())
        binding.rvVideos.layoutManager = layoutManager
        binding.rvVideos.adapter = videoAdapter

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
        setupSwipeRefresh()

        loadFeed(buildPersonalizedSeedQuery(), count = currentFetchCount, reset = true)
    }

    private fun setupSearchBarAndHistory() {
        searchHistoryAdapter = SearchHistoryAdapter(
            onItemClick = { item ->
                val query = item.query
                binding.etSearch.setText(query)
                binding.cardSearchHistory.visibility = View.GONE
                hideKeyboard()
                handleSearchOrUrl(query)
            },
            onDeleteClick = { item ->
                recentSearches.remove(item)
                searchHistoryAdapter.submitList(recentSearches.toList())
                if (recentSearches.isEmpty()) {
                    binding.cardSearchHistory.visibility = View.GONE
                }
            }
        )

        binding.rvSearchHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSearchHistory.adapter = searchHistoryAdapter

        binding.etSearch.doAfterTextChanged { text ->
            val query = text?.toString().orEmpty()
            binding.btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
            if (query.isNotEmpty() && binding.etSearch.hasFocus() && recentSearches.isNotEmpty()) {
                binding.cardSearchHistory.visibility = View.VISIBLE
            }
        }

        binding.etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && binding.etSearch.text?.isNotEmpty() == true && recentSearches.isNotEmpty()) {
                binding.cardSearchHistory.visibility = View.VISIBLE
            }
        }

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val input = binding.etSearch.text?.toString()?.trim().orEmpty()
                if (input.isNotEmpty()) {
                    binding.cardSearchHistory.visibility = View.GONE
                    hideKeyboard()
                    handleSearchOrUrl(input)
                }
                true
            } else {
                false
            }
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
            binding.cardSearchHistory.visibility = View.GONE
            loadFeed(buildPersonalizedSeedQuery(), count = currentFetchCount, reset = true)
        }

        binding.btnPasteLink.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipText = clipboard.primaryClip?.getItemAt(0)?.coerceToText(requireContext())?.toString()?.trim().orEmpty()
            if (clipText.isNotEmpty()) {
                binding.etSearch.setText(clipText)
                binding.cardSearchHistory.visibility = View.GONE
                hideKeyboard()
                handleSearchOrUrl(clipText)
            } else {
                Toast.makeText(requireContext(), "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSearchGo.setOnClickListener {
            val input = binding.etSearch.text?.toString()?.trim().orEmpty()
            if (input.isNotEmpty()) {
                binding.cardSearchHistory.visibility = View.GONE
                hideKeyboard()
                handleSearchOrUrl(input)
            } else {
                Toast.makeText(requireContext(), "Enter a search term or video URL", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupCategoryChips() {
        binding.chipGroupCategories.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            currentFetchCount = 16
            refreshCounter = 0

            when (checkedId) {
                R.id.chipAll -> {
                    isAllTabSelected = true
                    loadFeed(buildPersonalizedSeedQuery(), count = currentFetchCount, reset = true)
                }
                R.id.chipTrending -> {
                    isAllTabSelected = false
                    currentCategoryQuery = "trending india"
                    loadFeed(currentCategoryQuery, count = currentFetchCount, reset = true)
                }
                R.id.chipMusic -> {
                    isAllTabSelected = false
                    currentCategoryQuery = "latest hindi songs"
                    loadFeed(currentCategoryQuery, count = currentFetchCount, reset = true)
                }
                R.id.chipShayari -> {
                    isAllTabSelected = false
                    currentCategoryQuery = "hindi shayari status"
                    loadFeed(currentCategoryQuery, count = currentFetchCount, reset = true)
                }
                R.id.chipGaming -> {
                    isAllTabSelected = false
                    currentCategoryQuery = "bgmi gaming shorts"
                    loadFeed(currentCategoryQuery, count = currentFetchCount, reset = true)
                }
                R.id.chipMovies -> {
                    isAllTabSelected = false
                    currentCategoryQuery = "latest movie trailers"
                    loadFeed(currentCategoryQuery, count = currentFetchCount, reset = true)
                }
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshHome.setOnRefreshListener {
            refreshCounter++
            currentFetchCount = 16
            val query = if (isAllTabSelected) {
                buildPersonalizedSeedQuery()
            } else {
                currentCategoryQuery
            }
            loadFeed(query, count = currentFetchCount, reset = true)
        }
    }

    private fun buildPersonalizedSeedQuery(): String {
        val seeds = listOf(
            "trending india",
            "popular hindi songs",
            "viral shorts india",
            "new movie trailers"
        )
        return seeds[refreshCounter % seeds.size]
    }

    private fun isDirectUrl(input: String): Boolean {
        return input.startsWith("http://", ignoreCase = true) ||
            input.startsWith("https://", ignoreCase = true)
    }

    private fun handleSearchOrUrl(input: String) {
        recentSearches.removeAll { it.query == input }
        recentSearches.add(0, SearchHistoryItem(input))
        searchHistoryAdapter.submitList(recentSearches.toList())

        if (isDirectUrl(input)) {
            binding.cardDirectUrlResult.visibility = View.VISIBLE
            binding.tvDirectUrlTitle.text = input
            binding.tvDirectUrlChannel.text = "Ready to Play or Download"

            val directItem = VideoItem(
                id = input.hashCode().toString(),
                title = input,
                channel = "Direct Link",
                durationSeconds = 0L,
                thumbnailUrl = "",
                webpageUrl = input
            )

            binding.btnDirectUrlPlay.setOnClickListener {
                openPlayer(directItem)
            }
            binding.btnDirectUrlDownload.setOnClickListener {
                openDownloadSheet(directItem)
            }
        } else {
            binding.cardDirectUrlResult.visibility = View.GONE
            isAllTabSelected = false
            currentCategoryQuery = input
            currentFetchCount = 16
            loadFeed(input, count = currentFetchCount, reset = true)
        }
    }

    private fun loadFeed(query: String, count: Int, reset: Boolean) {
        fetchJob?.cancel()
        fetchJob = viewLifecycleOwner.lifecycleScope.launch {
            if (reset && currentFeedList.isEmpty()) {
                setLoading(true)
            }
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
        currentFetchCount += 16
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
