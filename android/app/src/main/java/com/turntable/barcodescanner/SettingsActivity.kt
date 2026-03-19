package com.turntable.barcodescanner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.turntable.barcodescanner.databinding.ActivitySettingsBinding

data class BrowserEntry(val label: String, val packageName: String?)

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val primaryApis = SearchPresets.primaryMusicInfo
    private val secondaryPresets = SearchPresets.secondaryTrackers
    private var browserEntries: List<BrowserEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = SearchPrefs(this)

        binding.checkBeepOnScan.isChecked = prefs.beepOnScan

        binding.spinnerPrimaryApi.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            primaryApis.map { it.name }
        )
        val primaryId = prefs.primaryMusicInfoApiId
        val primaryIndex = primaryApis.indexOfFirst { it.id == primaryId }.takeIf { it >= 0 } ?: 0
        binding.spinnerPrimaryApi.setSelection(primaryIndex)

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

        browserEntries = getAppsThatHandleWebLinks()
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

        binding.buttonSave.setOnClickListener {
            prefs.beepOnScan = binding.checkBeepOnScan.isChecked
            prefs.primaryMusicInfoApiId = primaryApis.getOrNull(binding.spinnerPrimaryApi.selectedItemPosition)?.id
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

    private fun getAppsThatHandleWebLinks(): List<BrowserEntry> {
        val list = mutableListOf(BrowserEntry(getString(R.string.browser_default), null))
        val seen = mutableSetOf<String>()
        for (uri in listOf("https://", "http://")) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            val resolveInfos = packageManager.queryIntentActivities(intent, 0)
            for (ri in resolveInfos) {
                val pkg = ri.activityInfo.packageName
                if (seen.add(pkg)) {
                    val label = ri.loadLabel(packageManager).toString()
                    list.add(BrowserEntry(label, pkg))
                }
            }
        }
        return list
    }

    private fun updatePostOptionsVisibility(show: Boolean) {
        binding.postOptionsContainer.visibility = if (show) View.VISIBLE else View.GONE
    }
}
