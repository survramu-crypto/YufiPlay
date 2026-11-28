package com.yufiplay.app.ui.splash

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yufiplay.app.YufiPlayApp
import com.yufiplay.app.databinding.ActivitySplashBinding
import com.yufiplay.app.ui.main.MainActivity
import com.yufiplay.app.util.StartupChimePlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        StartupChimePlayer.playHarmonicChime()
        runAnimations()

        lifecycleScope.launch {
            delay(1800L)
            withTimeoutOrNull(1500L) {
                YufiPlayApp.engineReady.await()
            }
            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun runAnimations() {
        binding.ivSplashLogo.alpha = 0f
        binding.ivSplashLogo.scaleX = 0.55f
        binding.ivSplashLogo.scaleY = 0.55f
        binding.tvSplashBrand.alpha = 0f
        binding.tvSplashBrand.translationY = 24f
        binding.tvSplashTagline.alpha = 0f

        val logoScaleX = ObjectAnimator.ofFloat(binding.ivSplashLogo, "scaleX", 0.55f, 1.0f).apply {
            duration = 850L
            interpolator = OvershootInterpolator(1.3f)
        }
        val logoScaleY = ObjectAnimator.ofFloat(binding.ivSplashLogo, "scaleY", 0.55f, 1.0f).apply {
            duration = 850L
            interpolator = OvershootInterpolator(1.3f)
        }
        val logoAlpha = ObjectAnimator.ofFloat(binding.ivSplashLogo, "alpha", 0f, 1f).apply {
            duration = 650L
        }

        val glowScaleX = ObjectAnimator.ofFloat(binding.viewSplashGlow, "scaleX", 0.8f, 1.25f, 1.0f).apply {
            duration = 1600L
            interpolator = AccelerateDecelerateInterpolator()
        }
        val glowScaleY = ObjectAnimator.ofFloat(binding.viewSplashGlow, "scaleY", 0.8f, 1.25f, 1.0f).apply {
            duration = 1600L
            interpolator = AccelerateDecelerateInterpolator()
        }
        val glowAlpha = ObjectAnimator.ofFloat(binding.viewSplashGlow, "alpha", 0.1f, 0.9f, 0.45f).apply {
            duration = 1600L
        }

        val brandAlpha = ObjectAnimator.ofFloat(binding.tvSplashBrand, "alpha", 0f, 1f).apply {
            startDelay = 300L
            duration = 700L
        }
        val brandTrans = ObjectAnimator.ofFloat(binding.tvSplashBrand, "translationY", 24f, 0f).apply {
            startDelay = 300L
            duration = 700L
        }
        val tagAlpha = ObjectAnimator.ofFloat(binding.tvSplashTagline, "alpha", 0f, 1f).apply {
            startDelay = 600L
            duration = 700L
        }

        AnimatorSet().apply {
            playTogether(
                logoScaleX, logoScaleY, logoAlpha,
                glowScaleX, glowScaleY, glowAlpha,
                brandAlpha, brandTrans, tagAlpha
            )
            start()
        }
    }
}
