package com.yufiplay.app.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.yufiplay.app.R
import com.yufiplay.app.data.DownloadStatus
import com.yufiplay.app.data.DownloadTask
import com.yufiplay.app.data.HistoryRepository
import com.yufiplay.app.data.VideoItem
import com.yufiplay.app.databinding.FragmentDownloadsBinding
import com.yufiplay.app.engine.DownloadManager
import com.yufiplay.app.ui.adapters.DownloadAdapter
import com.yufiplay.app.ui.player.PlayerActivity
import kotlinx.coroutines.launch

class DownloadsFragment : Fragment() {

    private var _binding: FragmentDownloadsBinding? = null
    private val binding get() = _binding!!

    private lateinit var activeAdapter: DownloadAdapter
    private lateinit var completedAdapter: DownloadAdapter

    // 0 = All, 1 = Video, 2 = Music
    private var selectedFilterTab = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDownloadsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        activeAdapter = DownloadAdapter(
            onPlayClick = {},
            onShareClick = {},
            onCancelOrDeleteClick = { task -> DownloadManager.cancelTask(task.taskId) }
        )

        completedAdapter = DownloadAdapter(
            onPlayClick = { task -> playSavedFile(task) },
            onShareClick = { task -> shareSavedFile(task) },
            onCancelOrDeleteClick = { task -> deleteCompletedEntry(task) }
        )

        binding.rvActiveDownloads.layoutManager = LinearLayoutManager(requireContext())
        binding.rvActiveDownloads.adapter = activeAdapter

        binding.rvCompletedDownloads.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCompletedDownloads.adapter = completedAdapter

        binding.tabDownloadFilter.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                selectedFilterTab = tab?.position ?: 0
                refreshLists()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    DownloadManager.activeTasks.collect {
                        refreshLists()
                    }
                }
                launch {
                    HistoryRepository.completedDownloadsFlow.collect {
                        refreshLists()
                    }
                }
            }
        }
    }

    private fun filterByTab(list: List<DownloadTask>): List<DownloadTask> {
        return when (selectedFilterTab) {
            1 -> list.filter { !it.format.isAudio } // Video
            2 -> list.filter { it.format.isAudio }  // Music
            else -> list                              // All
        }
    }

    private fun refreshLists() {
        val activeFiltered = filterByTab(DownloadManager.activeTasks.value)
        val completedFiltered = filterByTab(HistoryRepository.completedDownloadsFlow.value)

        activeAdapter.submitList(activeFiltered)
        completedAdapter.submitList(completedFiltered)

        binding.tvActiveHeader.visibility =
            if (activeFiltered.isEmpty()) View.GONE else View.VISIBLE
        binding.rvActiveDownloads.visibility =
            if (activeFiltered.isEmpty()) View.GONE else View.VISIBLE

        binding.tvCompletedHeader.visibility =
            if (completedFiltered.isEmpty()) View.GONE else View.VISIBLE

        val folderLabel = when (selectedFilterTab) {
            1 -> "Saved Folder: Movies/YufiPlay/Video"
            2 -> "Saved Folder: Music/YufiPlay/Music"
            else -> "Folders: Movies/YufiPlay/Video & Music/YufiPlay/Music"
        }
        binding.tvFolderHint.text = folderLabel

        binding.tvNoDownloads.visibility =
            if (activeFiltered.isEmpty() && completedFiltered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun playSavedFile(task: DownloadTask) {
        val uriStr = task.savedContentUri ?: return
        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_LOCAL_URI, uriStr)
            putExtra(
                PlayerActivity.EXTRA_VIDEO,
                VideoItem(
                    id = task.taskId,
                    title = task.title,
                    channel = task.format.label,
                    durationSeconds = 0L,
                    thumbnailUrl = task.thumbnailUrl,
                    webpageUrl = task.url
                )
            )
        }
        startActivity(intent)
    }

    private fun shareSavedFile(task: DownloadTask) {
        val uriStr = task.savedContentUri ?: return
        val uri = Uri.parse(uriStr)
        val mime = if (task.format.isAudio) "audio/mpeg" else "video/mp4"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    private fun deleteCompletedEntry(task: DownloadTask) {
        if (task.status == DownloadStatus.COMPLETED) {
            HistoryRepository.removeCompletedDownload(requireContext(), task.taskId)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
