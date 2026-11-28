package com.yufiplay.app.ui.player

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.yufiplay.app.R
import com.yufiplay.app.data.DownloadFormat
import com.yufiplay.app.data.PrefsManager
import com.yufiplay.app.data.VideoItem
import com.yufiplay.app.databinding.BottomSheetDownloadBinding
import com.yufiplay.app.engine.DownloadManager
import com.yufiplay.app.engine.YtDlpEngine
import kotlinx.coroutines.launch

class DownloadBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetDownloadBinding? = null
    private val binding get() = _binding!!

    private var videoItem: VideoItem? = null
    private lateinit var prefs: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        videoItem = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getSerializable(ARG_VIDEO, VideoItem::class.java)
        } else {
            arguments?.getSerializable(ARG_VIDEO) as? VideoItem
        }
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
        val video = videoItem ?: run {
            dismiss()
            return
        }
        prefs = PrefsManager(requireContext())

        binding.tvSheetTitle.text = video.title
        binding.tvSheetMeta.text = buildString {
            append(video.channel)
            val dur = video.formattedDuration()
            if (dur.isNotBlank()) append(" • $dur")
        }

        Glide.with(this)
            .load(video.thumbnailUrl)
            .placeholder(R.drawable.ic_yufiplay_logo)
            .into(binding.ivSheetThumb)

        val formatToButton = mapOf(
            DownloadFormat.ORIGINAL to binding.rbOriginal,
            DownloadFormat.MP4_4K to binding.rb4K,
            DownloadFormat.MP4_2K to binding.rb2K,
            DownloadFormat.MP4_1080 to binding.rb1080p,
            DownloadFormat.MP4_720 to binding.rb720p,
            DownloadFormat.MP4_480 to binding.rb480p,
            DownloadFormat.MP4_360 to binding.rb360p,
            DownloadFormat.MP4_240 to binding.rb240p,
            DownloadFormat.MP4_144 to binding.rb144p,
            DownloadFormat.MP3_320 to binding.rbMp3320,
            DownloadFormat.MP3_192 to binding.rbMp3192,
            DownloadFormat.MP3_128 to binding.rbMp3128
        )

        val idToFormat = formatToButton.entries.associate { (fmt, rb) -> rb.id to fmt }

        // Apply initial labels and select user's preferred default quality
        formatToButton.forEach { (fmt, rb) ->
            rb.text = fmt.label
        }
        selectPreferredFormat(formatToButton)

        // Fetch actual available resolutions and exact file sizes for this video
        binding.progressSizes.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            val sizeMap = YtDlpEngine.fetchFormatSizes(video.webpageUrl)
            if (_binding == null) return@launch
            binding.progressSizes.visibility = View.GONE

            val hasExactFormats = sizeMap.values.any {
                it.isNotBlank() && !it.startsWith("~") && it != "MP4" && it != "MP3"
            }

            formatToButton.forEach { (fmt, rb) ->
                val sizeStr = sizeMap[fmt].orEmpty()
                rb.text = if (sizeStr.isNotBlank()) "${fmt.label}  •  $sizeStr" else fmt.label

                // Hide 4K, 2K, or 1080p if this specific video doesn't have those high resolutions
                if (hasExactFormats && (fmt == DownloadFormat.MP4_4K || fmt == DownloadFormat.MP4_2K || fmt == DownloadFormat.MP4_1080)) {
                    val isAvailable = sizeStr.isNotBlank() && !sizeStr.startsWith("~")
                    rb.visibility = if (isAvailable) View.VISIBLE else View.GONE
                }
            }

            // Re-verify selection in case the previously selected tier was hidden
            val checkedId = binding.radioGroupFormats.checkedRadioButtonId
            val checkedRb = formatToButton.values.firstOrNull { it.id == checkedId }
            if (checkedRb == null || checkedRb.visibility != View.VISIBLE) {
                selectPreferredFormat(formatToButton)
            }
        }

        binding.btnStartDownload.setOnClickListener {
            val selectedId = binding.radioGroupFormats.checkedRadioButtonId
            val chosenFormat = idToFormat[selectedId] ?: DownloadFormat.ORIGINAL

            DownloadManager.enqueueDownload(requireContext(), video, chosenFormat)
            Toast.makeText(
                requireContext(),
                "Downloading (${chosenFormat.label})…",
                Toast.LENGTH_SHORT
            ).show()
            dismiss()
        }
    }

    private fun selectPreferredFormat(formatToButton: Map<DownloadFormat, RadioButton>) {
        val preferred = runCatching {
            DownloadFormat.valueOf(prefs.defaultQualityName)
        }.getOrDefault(DownloadFormat.ORIGINAL)

        val targetBtn = formatToButton[preferred]?.takeIf { it.visibility == View.VISIBLE }
            ?: binding.rbOriginal
        targetBtn.isChecked = true
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
