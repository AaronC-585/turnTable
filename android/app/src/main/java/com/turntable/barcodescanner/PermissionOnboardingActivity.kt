package com.turntable.barcodescanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.turntable.barcodescanner.databinding.ActivityPermissionOnboardingBinding

class PermissionOnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionOnboardingBinding

    private val requestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { grantedByOs ->
        updateCameraStatusFromOs()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        setupToolbarHome(binding.toolbar)

        updateCameraStatusFromOs()

        binding.buttonRequestCamera.setOnClickListener {
            requestCamera.launch(Manifest.permission.CAMERA)
        }

        binding.buttonContinue.setOnClickListener {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        UpdateCheckCoordinator.consumePendingSplashUpdateIfAny(this)
    }

    private fun updateCameraStatusFromOs() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        binding.textCameraStatus.visibility = View.VISIBLE
        binding.textCameraStatus.text = if (granted) getString(R.string.permission_status_granted) else getString(R.string.permission_status_denied)
        binding.textCameraStatus.setTextColor(
            ContextCompat.getColor(this, if (granted) android.R.color.holo_green_dark else android.R.color.holo_red_dark)
        )
    }

    companion object {
        const val PREFS_NAME = "permission_onboarding"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
    }
}
