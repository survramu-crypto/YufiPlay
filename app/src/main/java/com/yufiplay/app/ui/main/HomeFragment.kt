package com.yufiplay.app.ui.main

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.yufiplay.app.R
import com.yufiplay.app.data.VideoItem
import com.yufiplay.app.databinding.FragmentHomeBinding
import com.yufiplay.app.engine.YtDlpEngine
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
    private var fetchJob: Job? = null
    private var currentCategoryQuery = "trending india"

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

        binding.rvVideos.layoutManager = LinearLayoutManager(requireContext())
        binding.rvVideos.adapter = videoAdapter

        setupSearchBar()
        setupCategoryChips()

        binding.swipeRefreshHome.setOnRefreshListener {
            loadFeed(currentCategoryQuery, count = 15)
        }

        loadFeed(currentCategoryQuery, count = 15)
    }

    private fun setupSearchBar() {
        binding.etSearch.doAfterTextChanged { text ->
            binding.btnClearSearch.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
        }

        binding.btnPasteLink.setOnClickListener {
            val clipboard =
                requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipText = clipboard.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.coerceToText(requireContext())?.toString()?.trim()
            if (!clipText.isNullOrBlank()) {
                binding.etSearch.setText(clipText)
                handleSearchOrUrl(clipText)
            } else {
                Toast.makeText(requireContext(), "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        }

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etSearch.text?.toString()?.trim().orEmpty()
                if (query.isNotEmpty()) {
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
            R.id.chipMusic to "music india",
            R.id.chipShayari to "shayari status india",
            R.id.chipGaming to "gaming india",
            R.id.chipMovies to "movies trailer india"
        )

        binding.chipGroupCategories.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val query = chipQueries[checkedId] ?: return@setOnCheckedStateChangeListener
            currentCategoryQuery = query
            loadFeed(query, count = 15)
        }
    }

    private fun handleSearchOrUrl(input: String) {
        val directUrl = UrlUtils.extractSupportedUrl(input)
        if (directUrl != null) {
            fetchJob?.cancel()
            fetchJob = viewLifecycleOwner.lifecycleScope.launch {
                setLoading(true)
                try {
                    val item = YtDlpEngine.extractDirectInfo(directUrl)
                    setLoading(false)
                    videoAdapter.submitList(listOf(item))
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
            currentCategoryQuery = input
            loadFeed(input, count = 20)
        }
    }

    private fun loadFeed(query: String, count: Int) {
        fetchJob?.cancel()
        fetchJob = viewLifecycleOwner.lifecycleScope.launch {
            setLoading(true)
            binding.tvEmptyHome.visibility = View.GONE
            try {
                val items = YtDlpEngine.searchYouTube(query, count)
                videoAdapter.submitList(items)
                binding.tvEmptyHome.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    e.message ?: "Error loading videos",
                    Toast.LENGTH_SHORT
                ).show()
                if (videoAdapter.itemCount == 0) {
                    binding.tvEmptyHome.visibility = View.VISIBLE
                }
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.swipeRefreshHome.isRefreshing = loading
        binding.progressHome.visibility = if (loading && videoAdapter.itemCount == 0) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun openPlayer(item: VideoItem) {
        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_VIDEO, item)
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
