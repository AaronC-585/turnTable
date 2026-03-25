package com.turntable.barcodescanner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.turntable.barcodescanner.databinding.ActivitySettingsBinding

data class BrowserEntry(val label: String, val packageName: String?)

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private var browserEntries: List<BrowserEntry> = emptyList()
    private var secondaryPresets: List<SearchPresets.Preset> = emptyList()

    private val editListLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Reload listboxes after edit.
        loadListsAndBind()
    }

    private val pickTorrentDirLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.redacted_torrent_folder_permission_failed, Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        val prefs = SearchPrefs(this)
        releasePersistableTree(prefs.redactedTorrentDownloadTreeUri)
        prefs.redactedTorrentDownloadTreeUri = uri.toString()
        updateTorrentDirSummary()
    }

    private val backupSettingsJsonLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val payload = SettingsBackup.buildJson(this).toString(2).toByteArray(Charsets.UTF_8)
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(payload)
            } ?: error("openOutputStream returned null")
            Toast.makeText(this, R.string.settings_backup_saved_toast, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.settings_backup_failed, e.message ?: e.javaClass.simpleName),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        setupToolbarHome(binding.toolbar)

        binding.textRedactedApiKeyHelp.setRichHelp(R.string.redacted_api_key_help)
        binding.textTorrentDownloadDirHelp.setRichHelp(R.string.redacted_torrent_download_dir_help)
        binding.textTheAudioDbHelp.setRichHelp(R.string.theaudiodb_api_key_help)

        val prefs = SearchPrefs(this)

        binding.checkBeepOnScan.isChecked = prefs.beepOnScan
        binding.checkDownloadWifiOnly.isChecked = prefs.downloadOverWifiOnly

        val themeChoices = listOf(
            SearchPrefs.THEME_LIGHT to getString(R.string.theme_light),
            SearchPrefs.THEME_DARK to getString(R.string.theme_dark),
            SearchPrefs.THEME_FOLLOW_SYSTEM to getString(R.string.theme_follow_system),
        )
        val themeIndex = themeChoices.indexOfFirst { it.first == prefs.themeMode }.takeIf { it >= 0 } ?: 2
        ExpandableBulletChoice.bindLabelList(
            binding.expandTheme,
            getString(R.string.settings_theme),
            themeChoices.map { it.second },
            themeIndex.coerceIn(0, themeChoices.size - 1),
        )

        bindTrackerStatusColorFields(prefs)

        loadListsAndBind()

        binding.editRedactedApiKey.setText(prefs.redactedApiKey ?: "")
        binding.editTheAudioDbApiKey.setText(prefs.theAudioDbApiKey ?: "")

        browserEntries = getSecondaryBrowserList()
        val savedSecondaryPackage = prefs.secondaryBrowserPackage
        val secondaryBrowserIndex = when {
            savedSecondaryPackage == null -> 0
            else -> browserEntries.indexOfFirst { it.packageName == savedSecondaryPackage }.takeIf { it >= 0 } ?: 0
        }
        ExpandableBulletChoice.bindLabelList(
            binding.expandSecondaryBrowser,
            getString(R.string.open_secondary_in_browser),
            browserEntries.map { it.label },
            secondaryBrowserIndex.coerceIn(0, browserEntries.size - 1),
        )

        binding.buttonEditPrimaryList.setOnClickListener {
            editListLauncher.launch(Intent(this, EditPrimaryApiListActivity::class.java))
        }

        binding.buttonEditSecondaryList.setOnClickListener {
            editListLauncher.launch(Intent(this, EditSecondaryListActivity::class.java))
        }

        binding.buttonBackupSettingsJson.setOnClickListener {
            val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
            backupSettingsJsonLauncher.launch("turntable-settings-$stamp.json")
        }

        binding.buttonAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        binding.buttonCheckUpdates.setOnClickListener {
            UpdateCheckCoordinator.checkManually(this)
        }

        binding.buttonPickTorrentDir.setOnClickListener {
            pickTorrentDirLauncher.launch(null)
        }
        binding.buttonClearTorrentDir.setOnClickListener {
            val sp = SearchPrefs(this)
            releasePersistableTree(sp.redactedTorrentDownloadTreeUri)
            sp.redactedTorrentDownloadTreeUri = null
            updateTorrentDirSummary()
        }
        updateTorrentDirSummary()
        updateTorrentClientsSummary()

        binding.buttonTorrentClientsSettings.setOnClickListener {
            startActivity(Intent(this, TorrentClientsSettingsActivity::class.java))
        }

        binding.buttonSave.setOnClickListener {
            val (barOk, barParsed) = parseOptionalColorForSave(
                binding.editTrackerStatusBarBg.text?.toString(),
                R.string.settings_tracker_status_bar_bg,
            )
            if (!barOk) return@setOnClickListener
            val (upOk, upParsed) = parseOptionalColorForSave(
                binding.editTrackerStatusIconUp.text?.toString(),
                R.string.settings_tracker_status_icon_up,
            )
            if (!upOk) return@setOnClickListener
            val (downOk, downParsed) = parseOptionalColorForSave(
                binding.editTrackerStatusIconDown.text?.toString(),
                R.string.settings_tracker_status_icon_down,
            )
            if (!downOk) return@setOnClickListener

            val themePos = ListViewSingleChoice.selectedIndex(binding.expandTheme.listExpandChoices).coerceIn(0, themeChoices.size - 1)
            val newTheme = themeChoices[themePos].first
            if (newTheme != prefs.themeMode) {
                prefs.themeMode = newTheme
                AppTheme.applyPersistentNightMode(this)
                (application as TurnTableApp).bumpThemeEpoch()
                delegate.applyDayNight()
            }

            prefs.trackerStatusBarBackgroundColor = barParsed
            prefs.trackerStatusIconUpColor = upParsed
            prefs.trackerStatusIconDownColor = downParsed
            AppBottomBars.refreshTrackerStatusChrome(this)

            prefs.beepOnScan = binding.checkBeepOnScan.isChecked
            prefs.downloadOverWifiOnly = binding.checkDownloadWifiOnly.isChecked
            prefs.redactedApiKey = binding.editRedactedApiKey.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            prefs.theAudioDbApiKey = binding.editTheAudioDbApiKey.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            prefs.secondarySearchUrl = binding.editSecondaryUrl.text.normalizeUrlInput().takeIf { it.isNotBlank() }
            prefs.secondarySearchAutoFromMusicBrainz = binding.checkSecondaryAutoMusicBrainz.isChecked
            val secondaryBrowserPos = ListViewSingleChoice.selectedIndex(binding.expandSecondaryBrowser.listExpandChoices).coerceIn(0, browserEntries.size - 1)
            prefs.secondaryBrowserPackage = browserEntries.getOrNull(secondaryBrowserPos)?.packageName
            Toast.makeText(this, R.string.settings_saved_toast, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        updateTorrentClientsSummary()
    }

    private fun bindTrackerStatusColorFields(prefs: SearchPrefs) {
        binding.editTrackerStatusBarBg.setText(
            prefs.trackerStatusBarBackgroundColor?.let { ColorHexInput.formatForDisplay(it) } ?: "",
        )
        binding.editTrackerStatusIconUp.setText(
            prefs.trackerStatusIconUpColor?.let { ColorHexInput.formatForDisplay(it) } ?: "",
        )
        binding.editTrackerStatusIconDown.setText(
            prefs.trackerStatusIconDownColor?.let { ColorHexInput.formatForDisplay(it) } ?: "",
        )
    }

    /** First = success; second = parsed ARGB or `null` to clear override (blank field). */
    private fun parseOptionalColorForSave(raw: String?, labelRes: Int): Pair<Boolean, Int?> {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return true to null
        val c = ColorHexInput.parseOrNull(t) ?: run {
            Toast.makeText(
                this,
                getString(R.string.settings_color_invalid, getString(labelRes)),
                Toast.LENGTH_LONG,
            ).show()
            return false to null
        }
        return true to c
    }

    private fun loadListsAndBind() {
        val prefs = SearchPrefs(this)

        secondaryPresets = SearchPresets.secondaryTrackers(this)

        val currentSecondaryUrl = prefs.secondarySearchUrl
        val secondaryPresetIndex = secondaryPresets.indexOfFirst { it.url == currentSecondaryUrl }.takeIf { it >= 0 } ?: 0
        binding.editSecondaryUrl.setText(currentSecondaryUrl ?: "")
        ExpandableBulletChoice.bindLabelList(
            binding.expandSecondaryPreset,
            getString(R.string.secondary_search),
            secondaryPresets.map { it.name },
            secondaryPresetIndex.coerceIn(0, secondaryPresets.size.coerceAtLeast(1) - 1),
            onItemClick = { position ->
                val preset = secondaryPresets.getOrNull(position)
                if (preset != null && preset.id != SearchPresets.CUSTOM_ID) {
                    binding.editSecondaryUrl.setText(preset.url)
                }
            },
        )
        binding.checkSecondaryAutoMusicBrainz.isChecked = prefs.secondarySearchAutoFromMusicBrainz
    }

    /** Default + all Android-capable browsers from [KnownBrowsers.all] (includes Android-only entries). */
    private fun getSecondaryBrowserList(): List<BrowserEntry> {
        val list = mutableListOf(BrowserEntry(getString(R.string.browser_default), null))
        for (b in KnownBrowsers.all) {
            val installed = isPackageInstalled(b.packageName)
            val label = if (installed) b.name else "${b.name} (not installed)"
            list.add(BrowserEntry(label, b.packageName))
        }
        return list
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun updateTorrentClientsSummary() {
        val prefs = SearchPrefs(this)
        val qbtLine = torrentClientSummaryLine(
            prefs.qbittorrentClientEnabled,
            prefs.isQbittorrentConfigured(),
            prefs.qbittorrentBaseUrl?.trim().orEmpty(),
            R.string.torrent_client_name_qbt,
            R.string.torrent_clients_line_qbt_fmt,
            R.string.torrent_clients_line_qbt_off,
        )
        val trLine = torrentClientSummaryLine(
            prefs.transmissionClientEnabled,
            prefs.isTransmissionConfigured(),
            prefs.transmissionRpcUrl?.trim().orEmpty(),
            R.string.torrent_client_name_transmission,
            R.string.torrent_clients_line_transmission_fmt,
            R.string.torrent_clients_line_transmission_off,
        )
        val rtLine = torrentClientSummaryLine(
            prefs.rtorrentClientEnabled,
            prefs.isRtorrentConfigured(),
            prefs.rtorrentXmlRpcUrl?.trim().orEmpty(),
            R.string.torrent_client_name_rtorrent,
            R.string.torrent_clients_line_rtorrent_fmt,
            R.string.torrent_clients_line_rtorrent_off,
        )
        val dLine = torrentClientSummaryLine(
            prefs.delugeClientEnabled,
            prefs.isDelugeConfigured(),
            prefs.delugeWebUrl?.trim().orEmpty(),
            R.string.torrent_client_name_deluge,
            R.string.torrent_clients_line_deluge_fmt,
            R.string.torrent_clients_line_deluge_off,
        )
        binding.textTorrentClientsSummary.text = "$qbtLine\n$trLine\n$rtLine\n$dLine"
    }

    private fun torrentClientSummaryLine(
        enabled: Boolean,
        configured: Boolean,
        url: String,
        nameRes: Int,
        configuredFmt: Int,
        notConfiguredLine: Int,
    ): String {
        val name = getString(nameRes)
        return when {
            !enabled -> getString(R.string.torrent_clients_line_disabled_fmt, name)
            configured -> getString(configuredFmt, url)
            else -> getString(notConfiguredLine)
        }
    }

    private fun updateTorrentDirSummary() {
        val prefs = SearchPrefs(this)
        val u = prefs.redactedTorrentDownloadTreeUri
        if (u.isNullOrBlank()) {
            binding.textTorrentDirSummary.text = getString(R.string.redacted_torrent_download_dir_summary_default)
            binding.buttonClearTorrentDir.visibility = View.GONE
        } else {
            val label = runCatching {
                DocumentFile.fromTreeUri(this, Uri.parse(u))?.name
            }.getOrNull()
            binding.textTorrentDirSummary.text = when {
                !label.isNullOrBlank() ->
                    getString(R.string.redacted_torrent_download_dir_summary_fmt, label)
                else -> u
            }
            binding.buttonClearTorrentDir.visibility = View.VISIBLE
        }
    }

    private fun releasePersistableTree(uriString: String?) {
        if (uriString.isNullOrBlank()) return
        try {
            contentResolver.releasePersistableUriPermission(
                Uri.parse(uriString),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: Exception) {
            // Already released or invalid
        }
    }

}
