package com.turntable.barcodescanner

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.turntable.barcodescanner.databinding.ActivityTorrentClientsSettingsBinding

/**
 * qBittorrent, [TransmissionRpcClient], [RtorrentXmlRpcClient], and [DelugeWebClient], with per-client enable switches.
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
        binding.switchQbtEnabled.isChecked = prefs.qbittorrentClientEnabled
        binding.switchTransmissionEnabled.isChecked = prefs.transmissionClientEnabled
        binding.switchRtorrentEnabled.isChecked = prefs.rtorrentClientEnabled
        binding.switchDelugeEnabled.isChecked = prefs.delugeClientEnabled

        binding.editQbtBaseUrl.setText(prefs.qbittorrentBaseUrl ?: "")
        binding.editQbtUsername.setText(prefs.qbittorrentUsername ?: "")
        binding.editQbtPassword.setText(prefs.qbittorrentPassword ?: "")
        binding.editTransmissionRpcUrl.setText(prefs.transmissionRpcUrl ?: "")
        binding.editTransmissionUsername.setText(prefs.transmissionUsername ?: "")
        binding.editTransmissionPassword.setText(prefs.transmissionPassword ?: "")
        binding.editRtorrentXmlRpcUrl.setText(prefs.rtorrentXmlRpcUrl ?: "")
        binding.editRtorrentUsername.setText(prefs.rtorrentUsername ?: "")
        binding.editRtorrentPassword.setText(prefs.rtorrentPassword ?: "")
        binding.editDelugeWebUrl.setText(prefs.delugeWebUrl ?: "")
        binding.editDelugePassword.setText(prefs.delugePassword ?: "")

        binding.textQbtSettingsHelp.setRichHelp(R.string.qbt_settings_help)
        binding.textTransmissionSettingsHelp.setRichHelp(R.string.transmission_settings_help)
        binding.textRtorrentSettingsHelp.setRichHelp(R.string.rtorrent_settings_help)
        binding.textDelugeSettingsHelp.setRichHelp(R.string.deluge_settings_help)

        val apply = { applyTorrentClientInputsEnabled() }
        binding.switchQbtEnabled.setOnCheckedChangeListener { _, _ -> apply() }
        binding.switchTransmissionEnabled.setOnCheckedChangeListener { _, _ -> apply() }
        binding.switchRtorrentEnabled.setOnCheckedChangeListener { _, _ -> apply() }
        binding.switchDelugeEnabled.setOnCheckedChangeListener { _, _ -> apply() }
        apply()

        binding.buttonSave.setOnClickListener {
            prefs.qbittorrentClientEnabled = binding.switchQbtEnabled.isChecked
            prefs.transmissionClientEnabled = binding.switchTransmissionEnabled.isChecked
            prefs.rtorrentClientEnabled = binding.switchRtorrentEnabled.isChecked
            prefs.delugeClientEnabled = binding.switchDelugeEnabled.isChecked

            prefs.qbittorrentBaseUrl = binding.editQbtBaseUrl.text.normalizeUrlInput().takeIf { it.isNotBlank() }
            prefs.qbittorrentUsername = binding.editQbtUsername.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            prefs.qbittorrentPassword = binding.editQbtPassword.text?.toString()?.takeIf { it.isNotBlank() }
            prefs.transmissionRpcUrl = binding.editTransmissionRpcUrl.text.normalizeUrlInput().takeIf { it.isNotBlank() }
            prefs.transmissionUsername = binding.editTransmissionUsername.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            prefs.transmissionPassword = binding.editTransmissionPassword.text?.toString()?.takeIf { it.isNotBlank() }
            prefs.rtorrentXmlRpcUrl = binding.editRtorrentXmlRpcUrl.text.normalizeUrlInput().takeIf { it.isNotBlank() }
            prefs.rtorrentUsername = binding.editRtorrentUsername.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            prefs.rtorrentPassword = binding.editRtorrentPassword.text?.toString()?.takeIf { it.isNotBlank() }
            prefs.delugeWebUrl = binding.editDelugeWebUrl.text.normalizeUrlInput().takeIf { it.isNotBlank() }
            prefs.delugePassword = binding.editDelugePassword.text?.toString()?.takeIf { it.isNotBlank() }
            Toast.makeText(this, R.string.settings_saved_toast, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun applyTorrentClientInputsEnabled() {
        styleSection(
            binding.switchQbtEnabled.isChecked,
            binding.textQbtSettingsHelp,
            binding.editQbtBaseUrl,
            binding.editQbtUsername,
            binding.editQbtPassword,
        )
        styleSection(
            binding.switchTransmissionEnabled.isChecked,
            binding.textTransmissionSettingsHelp,
            binding.editTransmissionRpcUrl,
            binding.editTransmissionUsername,
            binding.editTransmissionPassword,
        )
        styleSection(
            binding.switchRtorrentEnabled.isChecked,
            binding.textRtorrentSettingsHelp,
            binding.editRtorrentXmlRpcUrl,
            binding.editRtorrentUsername,
            binding.editRtorrentPassword,
        )
        styleSection(
            binding.switchDelugeEnabled.isChecked,
            binding.textDelugeSettingsHelp,
            binding.editDelugeWebUrl,
            binding.editDelugePassword,
        )
    }

    private fun styleSection(enabled: Boolean, vararg views: View) {
        for (v in views) {
            v.isEnabled = enabled
            v.alpha = if (enabled) 1f else 0.45f
        }
    }
}
