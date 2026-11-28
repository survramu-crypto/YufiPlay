package com.yufiplay.app.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.yufiplay.app.data.HistoryItem
import com.yufiplay.app.data.HistoryRepository
import com.yufiplay.app.data.HistoryType
import com.yufiplay.app.data.VideoItem
import com.yufiplay.app.databinding.FragmentHistoryBinding
import com.yufiplay.app.engine.YtDlpEngine
import com.yufiplay.app.ui.adapters.HistoryAdapter
import com.yufiplay.app.ui.adapters.SearchHistoryAdapter
import com.yufiplay.app.ui.player.DownloadBottomSheetFragment
import com.yufiplay.app.ui.player.PlayerActivity
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var searchAdapter: SearchHistoryAdapter

    // 0 = Watch History, 1 = Search & URL History
    private var selectedTab = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        historyAdapter = HistoryAdapter { item -> openHistoryItem(item) }
        searchAdapter = SearchHistoryAdapter(
            onItemClick = { sItem ->
                if (sItem.isUrl) {
                    openUrlFromHistory(sItem.queryOrUrl)
                } else {
                    openSearchQueryAsPlayerOrFeed(sItem.queryOrUrl)
                }
            },
            onDeleteClick = { sItem ->
                HistoryRepository.removeSearchHistory(requireContext(), sItem.queryOrUrl)
            }
        )

        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = historyAdapter

        binding.tabHistoryFilter.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                selectedTab = tab?.position ?: 0
                updateUiForSelectedTab()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        binding.btnClearHistory.setOnClickListener {
            if (selectedTab == 0) {
                HistoryRepository.clearHistory(requireContext())
            } else {
                HistoryRepository.clearSearchHistory(requireContext())
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    HistoryRepository.historyFlow.collect { items ->
                        historyAdapter.submitList(items)
                        if (selectedTab == 0) updateUiForSelectedTab()
                    }
                }
                launch {
                    HistoryRepository.searchHistoryFlow.collect { items ->
                        searchAdapter.submitList(items)
                        if (selectedTab == 1) updateUiForSelectedTab()
                    }
                }
            }
        }
    }

    private fun updateUiForSelectedTab() {
        if (selectedTab == 0) {
            binding.rvHistory.adapter = historyAdapter
            val isEmpty = HistoryRepository.historyFlow.value.isEmpty()
            binding.tvNoHistory.text = "No watched or downloaded videos yet"
            binding.tvNoHistory.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.btnClearHistory.visibility = if (isEmpty) View.GONE else View.VISIBLE
        } else {
            binding.rvHistory.adapter = searchAdapter
            val isEmpty = HistoryRepository.searchHistoryFlow.value.isEmpty()
            binding.tvNoHistory.text = "No search or URL requests yet"
            binding.tvNoHistory.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.btnClearHistory.visibility = if (isEmpty) View.GONE else View.VISIBLE
        }
    }

    private fun openUrlFromHistory(url: String) {
        Toast.makeText(requireContext(), "Opening link…", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val video = YtDlpEngine.extractDirectInfo(url)
                DownloadBottomSheetFragment.newInstance(video)
                    .show(parentFragmentManager, "DownloadSheet")
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "Unable to open URL", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openSearchQueryAsPlayerOrFeed(query: String) {
        Toast.makeText(requireContext(), "Searching: $query", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val results = YtDlpEngine.searchYouTube(query, count = 10)
                val first = results.firstOrNull() ?: return@launch
                val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                    putExtra(PlayerActivity.EXTRA_VIDEO, first)
                    putExtra(PlayerActivity.EXTRA_QUEUE, ArrayList(results))
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "Search failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openHistoryItem(item: HistoryItem) {
        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            if (item.type == HistoryType.DOWNLOADED && item.url.startsWith("content://")) {
                putExtra(PlayerActivity.EXTRA_LOCAL_URI, item.url)
            }
            putExtra(
                PlayerActivity.EXTRA_VIDEO,
                VideoItem(
                    id = item.id,
                    title = item.title,
                    channel = item.channel,
                    durationSeconds = item.durationSeconds,
                    thumbnailUrl = item.thumbnailUrl,
                    webpageUrl = item.url
                )
            )
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
