package com.turntable.barcodescanner

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.turntable.barcodescanner.databinding.ActivityQbittorrentSettingsBinding

class QbittorrentSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQbittorrentSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQbittorrentSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)

        val prefs = SearchPrefs(this)
        binding.editQbtBaseUrl.setText(prefs.qbittorrentBaseUrl ?: "")
        binding.editQbtUsername.setText(prefs.qbittorrentUsername ?: "")
        binding.editQbtPassword.setText(prefs.qbittorrentPassword ?: "")

        binding.textQbtSettingsHelp.setRichHelp(R.string.qbt_settings_help)

        binding.buttonSave.setOnClickListener {
            prefs.qbittorrentBaseUrl = binding.editQbtBaseUrl.text.normalizeUrlInput().takeIf { it.isNotBlank() }
            prefs.qbittorrentUsername = binding.editQbtUsername.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            prefs.qbittorrentPassword = binding.editQbtPassword.text?.toString()?.takeIf { it.isNotBlank() }
            Toast.makeText(this, R.string.settings_saved_toast, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
