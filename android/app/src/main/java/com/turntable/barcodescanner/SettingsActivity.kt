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

        val prefs = SearchPrefs(this)

        binding.checkBeepOnScan.isChecked = prefs.beepOnScan

        loadListsAndBind()

        binding.editDiscogsToken.setText(prefs.discogsPersonalToken ?: "")
        binding.editRedactedApiKey.setText(prefs.redactedApiKey ?: "")
        binding.editTheAudioDbApiKey.setText(prefs.theAudioDbApiKey ?: "")
        binding.editLastFmApiKey.setText(prefs.lastFmApiKey ?: "")

        binding.editContentType.setText(prefs.postContentType ?: "application/json")
        binding.editPostBody.setText(prefs.postBody ?: """{"code":"%s"}""")
        binding.editPostHeaders.setText(prefs.postHeaders ?: "")

        val methods = listOf(SearchPrefs.METHOD_GET, SearchPrefs.METHOD_POST)
        binding.spinnerMethod.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            methods
        )
        val methodIndex = methods.indexOf(prefs.method).coerceAtLeast(0)
        binding.spinnerMethod.setSelection(methodIndex)
        updatePostOptionsVisibility(methodIndex == 1)

        binding.spinnerMethod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updatePostOptionsVisibility(methods[position] == SearchPrefs.METHOD_POST)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

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

        binding.buttonRedactedHub.setOnClickListener {
            if (prefs.redactedApiKey.isNullOrBlank()) {
                Toast.makeText(this, R.string.redacted_need_api_key, Toast.LENGTH_LONG).show()
            } else {
                startActivity(Intent(this, RedactedHubActivity::class.java))
            }
        }

        binding.buttonSave.setOnClickListener {
            prefs.beepOnScan = binding.checkBeepOnScan.isChecked
            prefs.discogsPersonalToken = binding.editDiscogsToken.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            prefs.redactedApiKey = binding.editRedactedApiKey.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            prefs.theAudioDbApiKey = binding.editTheAudioDbApiKey.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            prefs.lastFmApiKey = binding.editLastFmApiKey.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            prefs.secondarySearchUrl = binding.editSecondaryUrl.text?.toString()?.trim()
            prefs.secondarySearchAutoFromMusicBrainz = binding.checkSecondaryAutoMusicBrainz.isChecked
            prefs.method = methods[binding.spinnerMethod.selectedItemPosition]
            prefs.postContentType = binding.editContentType.text?.toString()?.trim()
            prefs.postBody = binding.editPostBody.text?.toString()?.trim()
            prefs.postHeaders = binding.editPostHeaders.text?.toString()?.trim()
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

    /** Hard-coded list for secondary search: Default + Play Store browsers (Android). On iOS use same list with appStoreUrl. */
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

    private fun updatePostOptionsVisibility(show: Boolean) {
        binding.postOptionsContainer.visibility = if (show) View.VISIBLE else View.GONE
    }
}
