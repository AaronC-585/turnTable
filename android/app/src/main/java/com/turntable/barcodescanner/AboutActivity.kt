package com.turntable.barcodescanner

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.turntable.barcodescanner.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)

        binding.textAppTitle.text = getString(R.string.app_name)
        val ver = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName ?: "—"
        } catch (_: Exception) {
            "—"
        }
        binding.textVersion.text = getString(R.string.about_version_fmt, ver)

        binding.buttonCheckUpdates.setOnClickListener {
            UpdateCheckCoordinator.checkManually(this)
        }
    }
}
