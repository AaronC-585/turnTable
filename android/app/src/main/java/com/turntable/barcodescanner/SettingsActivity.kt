package com.turntable.barcodescanner

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.turntable.barcodescanner.databinding.ActivitySettingsBinding

data class BrowserEntry(val label: String, val packageName: String?)

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private var browserEntries: List<BrowserEntry> = emptyList()
    private var secondaryPresets: List<SearchPresets.Preset> = emptyList()

    private val editListLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Reload spinners after edit.
        loadListsAndBind()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        setupToolbarHome(binding.toolbar)

        val prefs = SearchPrefs(this)

        binding.checkBeepOnScan.isChecked = prefs.beepOnScan
        binding.checkHapticOnScan.isChecked = prefs.hapticOnScan

        val themeChoices = listOf(
            SearchPrefs.THEME_LIGHT to getString(R.string.theme_light),
            SearchPrefs.THEME_DARK to getString(R.string.theme_dark),
            SearchPrefs.THEME_FOLLOW_SYSTEM to getString(R.string.theme_follow_system),
        )
        binding.spinnerTheme.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            themeChoices.map { it.second }
        )
        val themeIndex = themeChoices.indexOfFirst { it.first == prefs.themeMode }.takeIf { it >= 0 } ?: 2
        binding.spinnerTheme.setSelection(themeIndex.coerceIn(0, themeChoices.size - 1))

        loadListsAndBind()

        binding.editRedactedApiKey.setText(prefs.redactedApiKey ?: "")
        binding.editTheAudioDbApiKey.setText(prefs.theAudioDbApiKey ?: "")

        browserEntries = getSecondaryBrowserList()
        binding.spinnerSecondaryBrowser.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            browserEntries.map { it.label }
        )
        val savedSecondaryPackage = prefs.secondaryBrowserPackage
        val secondaryBrowserIndex = when {
            savedSecondaryPackage == null -> 0
            else -> browserEntries.indexOfFirst { it.packageName == savedSecondaryPackage }.takeIf { it >= 0 } ?: 0
        }
        binding.spinnerSecondaryBrowser.setSelection(secondaryBrowserIndex.coerceIn(0, browserEntries.size - 1))

        binding.buttonEditPrimaryList.setOnClickListener {
            editListLauncher.launch(Intent(this, EditPrimaryApiListActivity::class.java))
        }

        binding.buttonEditSecondaryList.setOnClickListener {
            editListLauncher.launch(Intent(this, EditSecondaryListActivity::class.java))
        }

        binding.buttonAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        binding.buttonCheckUpdates.setOnClickListener {
            UpdateCheckCoordinator.checkManually(this)
        }
        binding.buttonDonate.setOnClickListener {
            startActivity(Intent(this, DonationActivity::class.java))
        }

        binding.buttonSave.setOnClickListener {
            val themePos = binding.spinnerTheme.selectedItemPosition.coerceIn(0, themeChoices.size - 1)
            val newTheme = themeChoices[themePos].first
            if (newTheme != prefs.themeMode) {
                prefs.themeMode = newTheme
                AppTheme.applyPersistentNightMode(this)
                (application as TurnTableApp).bumpThemeEpoch()
                delegate.applyDayNight()
            }

            prefs.beepOnScan = binding.checkBeepOnScan.isChecked
            prefs.hapticOnScan = binding.checkHapticOnScan.isChecked
            prefs.redactedApiKey = binding.editRedactedApiKey.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            prefs.theAudioDbApiKey = binding.editTheAudioDbApiKey.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            prefs.secondarySearchUrl = binding.editSecondaryUrl.text.normalizeUrlInput().takeIf { it.isNotBlank() }
            prefs.secondarySearchAutoFromMusicBrainz = binding.checkSecondaryAutoMusicBrainz.isChecked
            val secondaryBrowserPos = binding.spinnerSecondaryBrowser.selectedItemPosition.coerceIn(0, browserEntries.size - 1)
            prefs.secondaryBrowserPackage = browserEntries.getOrNull(secondaryBrowserPos)?.packageName
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadListsAndBind() {
        val prefs = SearchPrefs(this)

        secondaryPresets = SearchPresets.secondaryTrackers(this)

        binding.spinnerSecondaryPreset.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            secondaryPresets.map { it.name }
        )
        val currentSecondaryUrl = prefs.secondarySearchUrl
        val secondaryPresetIndex = secondaryPresets.indexOfFirst { it.url == currentSecondaryUrl }.takeIf { it >= 0 } ?: 0
        binding.spinnerSecondaryPreset.setSelection(secondaryPresetIndex)
        binding.editSecondaryUrl.setText(currentSecondaryUrl ?: "")
        binding.spinnerSecondaryPreset.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val preset = secondaryPresets[position]
                if (preset.id != SearchPresets.CUSTOM_ID) {
                    binding.editSecondaryUrl.setText(preset.url)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
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

}
