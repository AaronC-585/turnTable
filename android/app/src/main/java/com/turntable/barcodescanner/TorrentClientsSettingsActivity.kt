package com.turntable.barcodescanner

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.turntable.barcodescanner.databinding.ActivityTorrentClientsSettingsBinding

/**
 * qBittorrent Web UI, [TransmissionRpcClient], and [RtorrentXmlRpcClient] (rTorrent XML-RPC) in one place.
 */
class TorrentClientsSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTorrentClientsSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTorrentClientsSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)

        val prefs = SearchPrefs(this)
        binding.editQbtBaseUrl.setText(prefs.qbittorrentBaseUrl ?: "")
        binding.editQbtUsername.setText(prefs.qbittorrentUsername ?: "")
        binding.editQbtPassword.setText(prefs.qbittorrentPassword ?: "")
        binding.editTransmissionRpcUrl.setText(prefs.transmissionRpcUrl ?: "")
        binding.editTransmissionUsername.setText(prefs.transmissionUsername ?: "")
        binding.editTransmissionPassword.setText(prefs.transmissionPassword ?: "")
        binding.editRtorrentXmlRpcUrl.setText(prefs.rtorrentXmlRpcUrl ?: "")
        binding.editRtorrentUsername.setText(prefs.rtorrentUsername ?: "")
        binding.editRtorrentPassword.setText(prefs.rtorrentPassword ?: "")

        binding.textQbtSettingsHelp.setRichHelp(R.string.qbt_settings_help)
        binding.textTransmissionSettingsHelp.setRichHelp(R.string.transmission_settings_help)
        binding.textRtorrentSettingsHelp.setRichHelp(R.string.rtorrent_settings_help)

        binding.buttonSave.setOnClickListener {
            prefs.qbittorrentBaseUrl = binding.editQbtBaseUrl.text.normalizeUrlInput().takeIf { it.isNotBlank() }
            prefs.qbittorrentUsername = binding.editQbtUsername.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            prefs.qbittorrentPassword = binding.editQbtPassword.text?.toString()?.takeIf { it.isNotBlank() }
            prefs.transmissionRpcUrl = binding.editTransmissionRpcUrl.text.normalizeUrlInput().takeIf { it.isNotBlank() }
            prefs.transmissionUsername = binding.editTransmissionUsername.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            prefs.transmissionPassword = binding.editTransmissionPassword.text?.toString()?.takeIf { it.isNotBlank() }
            prefs.rtorrentXmlRpcUrl = binding.editRtorrentXmlRpcUrl.text.normalizeUrlInput().takeIf { it.isNotBlank() }
            prefs.rtorrentUsername = binding.editRtorrentUsername.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            prefs.rtorrentPassword = binding.editRtorrentPassword.text?.toString()?.takeIf { it.isNotBlank() }
            Toast.makeText(this, R.string.settings_saved_toast, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
