package com.yufiplay.app.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.yufiplay.app.R
import com.yufiplay.app.data.PrefsManager
import com.yufiplay.app.data.StatusMediaItem
import com.yufiplay.app.data.VideoItem
import com.yufiplay.app.databinding.FragmentStatusSaverBinding
import com.yufiplay.app.ui.adapters.StatusAdapter
import com.yufiplay.app.ui.player.PlayerActivity
import com.yufiplay.app.util.MediaStoreSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StatusSaverFragment : Fragment() {

    private var _binding: FragmentStatusSaverBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: PrefsManager
    private lateinit var statusAdapter: StatusAdapter

    private val openStatusTreeLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                requireContext().contentResolver.takePersistableUriPermission(uri, flags)
            }
            prefs.statusTreeUri = uri.toString()
            loadStatuses(uri)
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

        statusAdapter = StatusAdapter(
            onPreviewClick = { item -> previewStatus(item) },
            onSaveClick = { item -> saveStatus(item) }
        )
        binding.rvStatuses.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvStatuses.adapter = statusAdapter

        binding.btnGrantStatusFolder.setOnClickListener {
            launchWhatsAppFolderPicker()
        }

        val savedUri = prefs.statusTreeUri?.let { Uri.parse(it) }
        if (savedUri != null) {
            loadStatuses(savedUri)
        } else {
            showPermissionState(true)
        }
    }

    private fun launchWhatsAppFolderPicker() {
        openStatusTreeLauncher.launch(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:Android/media/com.whatsapp/WhatsApp/Media/.Statuses"
                )
            } else null
        )
    }

    private fun loadStatuses(treeUri: Uri) {
        showPermissionState(false)
        binding.progressStatus.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                val docTree = DocumentFile.fromTreeUri(requireContext(), treeUri)
                docTree?.listFiles()
                    ?.filter { file ->
                        val name = file.name?.lowercase().orEmpty()
                        file.isFile && (name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                            name.endsWith(".png") || name.endsWith(".mp4"))
                    }
                    ?.map { file ->
                        val name = file.name ?: "status_${System.currentTimeMillis()}"
                        StatusMediaItem(
                            uri = file.uri,
                            name = name,
                            isVideo = name.endsWith(".mp4", ignoreCase = true),
                            lastModified = file.lastModified()
                        )
                    }
                    ?.sortedByDescending { it.lastModified }
                    .orEmpty()
            }
            binding.progressStatus.visibility = View.GONE
            statusAdapter.submitList(items)
            binding.tvNoStatuses.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
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
                        webpageUrl = ""
                    )
                )
            }
            startActivity(intent)
        } else {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(item.uri, "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        }
    }

    private fun saveStatus(item: StatusMediaItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                MediaStoreSaver.saveStatusToGallery(
                    context = requireContext(),
                    sourceUri = item.uri,
                    fileName = item.name,
                    isVideo = item.isVideo
                )
            }
            if (saved != null) {
                Toast.makeText(requireContext(), R.string.status_saved, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Failed to save status", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showPermissionState(show: Boolean) {
        binding.layoutStatusPermission.visibility = if (show) View.VISIBLE else View.GONE
        binding.rvStatuses.visibility = if (show) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
