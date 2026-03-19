package com.turntable.barcodescanner

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.turntable.barcodescanner.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val presets = SearchPresets.all

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = SearchPrefs(this)

        binding.spinnerPreset.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            presets.map { it.name }
        )
        val currentUrl = prefs.searchUrl
        val presetIndex = presets.indexOfFirst { it.url == currentUrl }.takeIf { it >= 0 } ?: 0
        binding.spinnerPreset.setSelection(presetIndex)

        binding.spinnerPreset.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val preset = presets[position]
                if (preset.id != SearchPresets.CUSTOM_ID) {
                    binding.editSearchUrl.setText(preset.url)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.editSearchUrl.setText(currentUrl ?: "")
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

        binding.buttonSave.setOnClickListener {
            prefs.searchUrl = binding.editSearchUrl.text?.toString()?.trim()
            prefs.method = methods[binding.spinnerMethod.selectedItemPosition]
            prefs.postContentType = binding.editContentType.text?.toString()?.trim()
            prefs.postBody = binding.editPostBody.text?.toString()?.trim()
            prefs.postHeaders = binding.editPostHeaders.text?.toString()?.trim()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun updatePostOptionsVisibility(show: Boolean) {
        binding.postOptionsContainer.visibility = if (show) View.VISIBLE else View.GONE
    }
}
