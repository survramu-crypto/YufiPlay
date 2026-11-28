package com.yufiplay.app.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.yausername.youtubedl_android.YoutubeDL
import com.yufiplay.app.data.DownloadFormat
import com.yufiplay.app.data.PrefsManager
import com.yufiplay.app.databinding.FragmentSettingsBinding
import com.yufiplay.app.engine.YtDlpEngine
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: PrefsManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = PrefsManager(requireContext())

        refreshUiState()
        setupListeners()
    }

    private fun refreshUiState() {
        binding.tvUserName.text = prefs.userName
        binding.switchDarkMode.isChecked = prefs.isDarkMode
        binding.switchBackgroundAudio.isChecked = prefs.backgroundAudioEnabled
        binding.switchAutoPlayNext.isChecked = prefs.autoPlayNext

        val currentFormat = runCatching {
            DownloadFormat.valueOf(prefs.defaultQualityName)
        }.getOrDefault(DownloadFormat.ORIGINAL)
        binding.tvSelectedQuality.text = currentFormat.label

        binding.tvSelectedLanguage.text = when (prefs.languageCode) {
            "hi" -> "हिन्दी (Hindi)"
            else -> "English"
        }
    }

    private fun setupListeners() {
        binding.btnEditProfile.setOnClickListener {
            showEditNameDialog()
        }

        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            if (prefs.isDarkMode != isChecked) {
                prefs.isDarkMode = isChecked
                AppCompatDelegate.setDefaultNightMode(
                    if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                    else AppCompatDelegate.MODE_NIGHT_NO
                )
            }
        }

        binding.switchBackgroundAudio.setOnCheckedChangeListener { _, isChecked ->
            prefs.backgroundAudioEnabled = isChecked
        }

        binding.switchAutoPlayNext.setOnCheckedChangeListener { _, isChecked ->
            prefs.autoPlayNext = isChecked
        }

        binding.layoutDefaultQuality.setOnClickListener {
            showDefaultQualityDialog()
        }

        binding.layoutLanguage.setOnClickListener {
            showLanguageDialog()
        }

        binding.btnUpdateEngine.setOnClickListener {
            updateDownloadEngine()
        }

        binding.btnClearCache.setOnClickListener {
            runCatching {
                requireContext().cacheDir.deleteRecursively()
                requireContext().getExternalFilesDir("work")?.deleteRecursively()
            }
            Toast.makeText(requireContext(), "App cache cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEditNameDialog() {
        val input = EditText(requireContext()).apply {
            setText(prefs.userName)
            setSelection(text.length)
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Edit Profile Name")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    prefs.userName = newName
                    binding.tvUserName.text = newName
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDefaultQualityDialog() {
        val formats = DownloadFormat.values()
        val labels = formats.map { it.label }.toTypedArray()
        val currentIdx = formats.indexOfFirst { it.name == prefs.defaultQualityName }.coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle("Default Download Quality")
            .setSingleChoiceItems(labels, currentIdx) { dialog, which ->
                val chosen = formats[which]
                prefs.defaultQualityName = chosen.name
                binding.tvSelectedQuality.text = chosen.label
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLanguageDialog() {
        val languages = arrayOf("English", "हिन्दी (Hindi)")
        val codes = arrayOf("en", "hi")
        val currentIdx = if (prefs.languageCode == "hi") 1 else 0

        AlertDialog.Builder(requireContext())
            .setTitle("App Language")
            .setSingleChoiceItems(languages, currentIdx) { dialog, which ->
                val code = codes[which]
                prefs.languageCode = code
                binding.tvSelectedLanguage.text = languages[which]
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(code)
                )
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateDownloadEngine() {
        binding.btnUpdateEngine.isEnabled = false
        binding.tvEngineStatus.text = "Updating engine…"
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val status = YtDlpEngine.updateEngine(requireContext())
                val msg = when (status) {
                    YoutubeDL.UpdateStatus.DONE -> "Engine updated to latest nightly!"
                    YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "Engine is already up to date"
                    else -> "Update check completed"
                }
                binding.tvEngineStatus.text = msg
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                binding.tvEngineStatus.text = "Update failed"
                Toast.makeText(
                    requireContext(),
                    e.message ?: "Engine update failed",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                if (_binding != null) {
                    binding.btnUpdateEngine.isEnabled = true
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
