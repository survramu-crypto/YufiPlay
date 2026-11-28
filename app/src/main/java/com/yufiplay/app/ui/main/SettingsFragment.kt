package com.yufiplay.app.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.yausername.youtubedl_android.YoutubeDL
import com.yufiplay.app.R
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

        binding.switchDarkMode.isChecked = prefs.isDarkMode
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.isDarkMode = isChecked
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        if (prefs.languageCode == "hi") {
            binding.radioHindi.isChecked = true
        } else {
            binding.radioEnglish.isChecked = true
        }

        binding.radioGroupLanguage.setOnCheckedChangeListener { _, checkedId ->
            val targetLang = if (checkedId == R.id.radioHindi) "hi" else "en"
            if (targetLang != prefs.languageCode) {
                prefs.languageCode = targetLang
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(targetLang)
                )
            }
        }

        binding.btnUpdateEngine.setOnClickListener {
            runEngineUpdate()
        }
    }

    private fun runEngineUpdate() {
        binding.btnUpdateEngine.isEnabled = false
        binding.progressEngineUpdate.visibility = View.VISIBLE
        binding.tvEngineStatus.text = getString(R.string.updating_engine)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val status = YtDlpEngine.updateEngine(requireContext().applicationContext)
                val msg = when (status) {
                    YoutubeDL.UpdateStatus.DONE -> getString(R.string.engine_updated)
                    YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> getString(R.string.engine_up_to_date)
                    else -> getString(R.string.engine_up_to_date)
                }
                binding.tvEngineStatus.text = msg
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                val err = e.message ?: "Update failed"
                binding.tvEngineStatus.text = err
                Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show()
            } finally {
                binding.btnUpdateEngine.isEnabled = true
                binding.progressEngineUpdate.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
