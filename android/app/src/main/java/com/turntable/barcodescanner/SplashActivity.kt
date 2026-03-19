package com.turntable.barcodescanner

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.turntable.barcodescanner.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Handler(Looper.getMainLooper()).postDelayed({
            val prefs = getSharedPreferences(PermissionOnboardingActivity.PREFS_NAME, MODE_PRIVATE)
            val onboardingDone = prefs.getBoolean(PermissionOnboardingActivity.KEY_ONBOARDING_DONE, false)
            val next = if (onboardingDone) MainActivity::class.java else PermissionOnboardingActivity::class.java
            startActivity(Intent(this, next))
            finish()
        }, SPLASH_MS)
    }

    companion object {
        private const val SPLASH_MS = 2500L
    }
}
