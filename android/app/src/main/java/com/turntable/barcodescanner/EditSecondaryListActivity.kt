package com.turntable.barcodescanner

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.turntable.barcodescanner.databinding.ActivityEditSecondaryListBinding

class EditSecondaryListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditSecondaryListBinding
    private val list = mutableListOf<SearchPresets.Preset>()
    private var selectedIndex = 0
    private var ignoreSpinnerSelection = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditSecondaryListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        setupToolbarHome(binding.toolbar)

        binding.textHelp.setText(R.string.edit_secondary_list_help)

        val prefs = SearchPrefs(this)
        val saved = SearchPresets.parseSecondaryListText(prefs.secondaryListText)
        list.clear()
        list.addAll(saved ?: SearchPresets.secondaryTrackersDefault)

        binding.spinnerPresets.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            list.map { it.name }
        )
        binding.spinnerPresets.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (ignoreSpinnerSelection) return
                saveCurrentToIndex(selectedIndex)
                selectedIndex = position.coerceIn(0, list.size - 1)
                showPresetAt(selectedIndex)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.editName.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) saveCurrentToIndex(selectedIndex) }
        binding.editUrl.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) saveCurrentToIndex(selectedIndex) }

        binding.buttonMoveUp.setOnClickListener {
            saveCurrentToIndex(selectedIndex)
            if (selectedIndex > 0) {
                list.removeAt(selectedIndex).let { list.add(selectedIndex - 1, it) }
                selectedIndex--
                refreshSpinnerAndSelection()
            }
        }
        binding.buttonMoveDown.setOnClickListener {
            saveCurrentToIndex(selectedIndex)
            if (selectedIndex < list.size - 1) {
                list.removeAt(selectedIndex).let { list.add(selectedIndex + 1, it) }
                selectedIndex++
                refreshSpinnerAndSelection()
            }
        }
        binding.buttonRemove.setOnClickListener {
            if (list.size <= 1) {
                Toast.makeText(this, R.string.secondary_edit_need_one, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveCurrentToIndex(selectedIndex)
            list.removeAt(selectedIndex)
            selectedIndex = selectedIndex.coerceIn(0, list.size - 1)
            refreshSpinnerAndSelection()
            showPresetAt(selectedIndex)
        }
        binding.buttonAdd.setOnClickListener {
            saveCurrentToIndex(selectedIndex)
            list.add(SearchPresets.Preset(SearchPresets.CUSTOM_ID, "New search", "https://example.com/search?q=%s"))
            selectedIndex = list.size - 1
            refreshSpinnerAndSelection()
            showPresetAt(selectedIndex)
        }

        binding.buttonSave.setOnClickListener {
            val name = binding.editName.text?.toString()?.trim().orEmpty()
            val url = binding.editUrl.text?.toString()?.trim().orEmpty()
            if (name.isBlank() || url.isBlank() || !url.contains("%s")) {
                Toast.makeText(this, R.string.secondary_edit_invalid, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (selectedIndex in list.indices) list[selectedIndex] = list[selectedIndex].copy(name = name, url = url)
            prefs.secondaryListText = SearchPresets.serializeSecondaryList(list)
            setResult(RESULT_OK)
            finish()
        }
        binding.buttonCancel.setOnClickListener { finish() }

        showPresetAt(0)
    }

    private val prefs: SearchPrefs get() = SearchPrefs(this)

    private fun saveCurrentToIndex(index: Int) {
        if (index !in list.indices) return
        val name = binding.editName.text?.toString()?.trim().orEmpty()
        val url = binding.editUrl.text?.toString()?.trim().orEmpty()
        list[index] = list[index].copy(name = name.ifBlank { list[index].name }, url = url.ifBlank { list[index].url })
    }

    private fun showPresetAt(index: Int) {
        if (index !in list.indices) return
        ignoreSpinnerSelection = true
        binding.editName.setText(list[index].name)
        binding.editUrl.setText(list[index].url)
        binding.spinnerPresets.setSelection(index)
        ignoreSpinnerSelection = false
    }

    private fun refreshSpinnerAndSelection() {
        ignoreSpinnerSelection = true
        binding.spinnerPresets.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            list.map { it.name }
        )
        binding.spinnerPresets.setSelection(selectedIndex.coerceIn(0, list.size - 1))
        ignoreSpinnerSelection = false
    }
}
