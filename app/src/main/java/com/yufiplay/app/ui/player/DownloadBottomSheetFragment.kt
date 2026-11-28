package com.yufiplay.app.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.yufiplay.app.R
import com.yufiplay.app.data.DownloadFormat
import com.yufiplay.app.data.VideoItem
import com.yufiplay.app.databinding.BottomSheetDownloadBinding
import com.yufiplay.app.engine.DownloadManager

class DownloadBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetDownloadBinding? = null
    private val binding get() = _binding!!

    private lateinit var videoItem: VideoItem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        videoItem = requireArguments().getSerializable(ARG_VIDEO) as VideoItem
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetDownloadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.tvSheetVideoTitle.text = videoItem.title

        val formatMap = mapOf(
            binding.btn144p to DownloadFormat.MP4_144,
            binding.btn240p to DownloadFormat.MP4_240,
            binding.btn360p to DownloadFormat.MP4_360,
            binding.btn480p to DownloadFormat.MP4_480,
            binding.btn720p to DownloadFormat.MP4_720,
            binding.btn1080p to DownloadFormat.MP4_1080,
            binding.btnMp3128 to DownloadFormat.MP3_128,
            binding.btnMp3192 to DownloadFormat.MP3_192,
            binding.btnMp3320 to DownloadFormat.MP3_320
        )

        formatMap.forEach { (button, format) ->
            button.setOnClickListener {
                DownloadManager.enqueueDownload(requireContext().applicationContext, videoItem, format)
                Toast.makeText(requireContext(), R.string.download_started, Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_VIDEO = "arg_video"

        fun newInstance(video: VideoItem): DownloadBottomSheetFragment {
            return DownloadBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_VIDEO, video)
                }
            }
        }
    }
}
