package com.yufiplay.app.ui.main

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.yufiplay.app.R
import com.yufiplay.app.data.PrefsManager
import com.yufiplay.app.databinding.ActivityMainBinding
import com.yufiplay.app.engine.YtDlpEngine
import com.yufiplay.app.ui.player.DownloadBottomSheetFragment
import com.yufiplay.app.util.UrlUtils
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager
    private var detectedClipboardUrl: String? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Permissions handled gracefully at point of use */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsManager(this)

        requestRuntimePermissions()
        setupBottomNavigation()

        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }

        binding.btnClipboardDismiss.setOnClickListener {
            detectedClipboardUrl?.let { prefs.lastHandledClipboardUrl = it }
            binding.cardClipboardBanner.visibility = View.GONE
        }

        binding.btnClipboardDownload.setOnClickListener {
            val url = detectedClipboardUrl ?: return@setOnClickListener
            prefs.lastHandledClipboardUrl = url
            binding.cardClipboardBanner.visibility = View.GONE
            openDownloadSheetForUrl(url)
        }

        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        checkClipboardForLinks()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            checkClipboardForLinks()
        }
    }

    private fun checkClipboardForLinks() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
        val url = UrlUtils.extractSupportedUrl(text) ?: return

        if (url != prefs.lastHandledClipboardUrl && url != detectedClipboardUrl) {
            detectedClipboardUrl = url
            binding.tvClipboardUrlPreview.text = url
            binding.cardClipboardBanner.visibility = View.VISIBLE
        }
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            val url = UrlUtils.extractSupportedUrl(sharedText)
            if (url != null) {
                openDownloadSheetForUrl(url)
            }
        }
    }

    fun openDownloadSheetForUrl(url: String) {
        Toast.makeText(this, R.string.extracting_info, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val video = YtDlpEngine.extractDirectInfo(url)
                DownloadBottomSheetFragment.newInstance(video)
                    .show(supportFragmentManager, "DownloadSheet")
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    e.message ?: "Could not extract link",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_status -> StatusSaverFragment()
                R.id.nav_downloads -> DownloadsFragment()
                R.id.nav_history -> HistoryFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> HomeFragment()
            }
            loadFragment(fragment)
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun requestRuntimePermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                perms.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                perms.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                perms.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (perms.isNotEmpty()) {
            permissionLauncher.launch(perms.toTypedArray())
        }
    }
}
