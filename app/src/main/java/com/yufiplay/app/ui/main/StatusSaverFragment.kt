package com.yufiplay.app.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.tabs.TabLayout
import com.yufiplay.app.R
import com.yufiplay.app.data.PrefsManager
import com.yufiplay.app.data.StatusMediaItem
import com.yufiplay.app.data.VideoItem
import com.yufiplay.app.databinding.FragmentStatusSaverBinding
import com.yufiplay.app.ui.adapters.StatusMediaAdapter
import com.yufiplay.app.ui.player.PlayerActivity
import com.yufiplay.app.util.MediaStoreSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StatusSaverFragment : Fragment() {

    private var _binding: FragmentStatusSaverBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: PrefsManager
    private lateinit var adapter: StatusMediaAdapter

    private val allStatuses = mutableListOf<StatusMediaItem>()
    // 0 = All, 1 = Images, 2 = Videos
    private var selectedTab = 0

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                requireContext().contentResolver.takePersistableUriPermission(uri, takeFlags)
            }
            prefs.statusTreeUri = uri.toString()
            loadStatusesFromTree(uri)
        } else {
            Toast.makeText(
                requireContext(),
                "Please select the .Statuses folder to view statuses",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatusSaverBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = PrefsManager(requireContext())

        adapter = StatusMediaAdapter(
            onItemClick = { item -> previewStatus(item) },
            onSaveClick = { item -> saveStatus(item) },
            onShareClick = { item -> shareStatus(item) }
        )

        binding.rvStatusMedia.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvStatusMedia.adapter = adapter

        binding.btnGrantStatusPermission.setOnClickListener {
            openWhatsAppStatusFolderPicker()
        }

        binding.tabStatusFilter.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                selectedTab = tab?.position ?: 0
                applyFilter()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        binding.swipeRefreshStatus.setOnRefreshListener {
            val savedStr = prefs.statusTreeUri
            if (!savedStr.isNullOrBlank()) {
                loadStatusesFromTree(Uri.parse(savedStr))
            } else {
                binding.swipeRefreshStatus.isRefreshing = false
            }
        }

        checkSavedPermissionAndLoad()
    }

    private fun checkSavedPermissionAndLoad() {
        val savedStr = prefs.statusTreeUri
        if (!savedStr.isNullOrBlank()) {
            val uri = Uri.parse(savedStr)
            val hasPerm = requireContext().contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission
            }
            if (hasPerm) {
                binding.layoutStatusPermission.visibility = View.GONE
                loadStatusesFromTree(uri)
                return
            }
        }
        binding.layoutStatusPermission.visibility = View.VISIBLE
        binding.tvNoStatus.visibility = View.GONE
    }

    private fun openWhatsAppStatusFolderPicker() {
        val initialUri = Uri.parse(
            "content://com.android.externalstorage.documents/document/primary%3AAndroid%2Fmedia%2Fcom.whatsapp%2FWhatsApp%2FMedia%2F.Statuses"
        )
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
            }
        }
        runCatching {
            folderPickerLauncher.launch(initialUri)
        }.onFailure {
            startActivity(intent)
        }
    }

    private fun loadStatusesFromTree(treeUri: Uri) {
        binding.layoutStatusPermission.visibility = View.GONE
        binding.swipeRefreshStatus.isRefreshing = true

        viewLifecycleOwner.lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                val rootDoc = DocumentFile.fromTreeUri(requireContext(), treeUri)
                val files = rootDoc?.listFiles().orEmpty()
                files.mapNotNull { doc ->
                    val name = doc.name ?: return@mapNotNull null
                    if (name.startsWith(".") || name.equals("nomedia", ignoreCase = true)) {
                        return@mapNotNull null
                    }
                    val lower = name.lowercase()
                    val isVideo = lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".3gp")
                    val isImage = lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")
                    if (!isVideo && !isImage) return@mapNotNull null

                    StatusMediaItem(
                        uri = doc.uri,
                        name = name,
                        isVideo = isVideo,
                        lastModified = doc.lastModified()
                    )
                }.sortedByDescending { it.lastModified }
            }

            if (_binding == null) return@launch
            binding.swipeRefreshStatus.isRefreshing = false
            allStatuses.clear()
            allStatuses.addAll(items)
            applyFilter()
        }
    }

    private fun applyFilter() {
        val filtered = when (selectedTab) {
            1 -> allStatuses.filter { !it.isVideo }
            2 -> allStatuses.filter { it.isVideo }
            else -> allStatuses
        }
        adapter.submitList(filtered)
        binding.tvNoStatus.visibility =
            if (filtered.isEmpty() && binding.layoutStatusPermission.visibility != View.VISIBLE) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun previewStatus(item: StatusMediaItem) {
        if (item.isVideo) {
            val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_LOCAL_URI, item.uri.toString())
                putExtra(
                    PlayerActivity.EXTRA_VIDEO,
                    VideoItem(
                        id = item.name,
                        title = item.name,
                        channel = "WhatsApp Status",
                        durationSeconds = 0L,
                        thumbnailUrl = "",
                        webpageUrl = item.uri.toString()
                    )
                )
            }
            startActivity(intent)
        } else {
            val imageView = ImageView(requireContext()).apply {
                adjustViewBounds = true
                setPadding(24, 24, 24, 24)
            }
            Glide.with(this).load(item.uri).into(imageView)
            AlertDialog.Builder(requireContext())
                .setTitle(item.name)
                .setView(imageView)
                .setPositiveButton("Save") { _, _ -> saveStatus(item) }
                .setNeutralButton("Share") { _, _ -> shareStatus(item) }
                .setNegativeButton("Close", null)
                .show()
        }
    }

    private fun saveStatus(item: StatusMediaItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            val savedUri = withContext(Dispatchers.IO) {
                MediaStoreSaver.saveStatusToGallery(
                    context = requireContext(),
                    sourceUri = item.uri,
                    fileName = item.name,
                    isVideo = item.isVideo
                )
            }
            if (_binding == null) return@launch
            if (savedUri != null) {
                Toast.makeText(
                    requireContext(),
                    "Saved to Gallery (YufiPlay Status)!",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(requireContext(), "Failed to save status", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareStatus(item: StatusMediaItem) {
        val mime = if (item.isVideo) "video/mp4" else "image/jpeg"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, item.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
