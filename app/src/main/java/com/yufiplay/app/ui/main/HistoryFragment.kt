package com.yufiplay.app.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.yufiplay.app.data.HistoryItem
import com.yufiplay.app.data.HistoryRepository
import com.yufiplay.app.data.HistoryType
import com.yufiplay.app.data.VideoItem
import com.yufiplay.app.databinding.FragmentHistoryBinding
import com.yufiplay.app.ui.adapters.HistoryAdapter
import com.yufiplay.app.ui.player.PlayerActivity
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var historyAdapter: HistoryAdapter

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
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = historyAdapter

        binding.btnClearHistory.setOnClickListener {
            HistoryRepository.clearHistory(requireContext())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                HistoryRepository.historyFlow.collect { items ->
                    historyAdapter.submitList(items)
                    binding.tvNoHistory.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    binding.btnClearHistory.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
                }
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
