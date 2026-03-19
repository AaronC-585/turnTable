package com.turntable.barcodescanner

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.turntable.barcodescanner.databinding.ActivityEditPrimaryApiListBinding

class EditPrimaryApiListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditPrimaryApiListBinding

    private val availableApis: List<SearchPresets.Preset>
        get() = SearchPresets.primaryMusicInfoAvailable()

    private var entries: MutableList<SearchPresets.PrimaryApiEntry> = mutableListOf()
    private var selectedEntryIndex: Int = 0
    private var ignoreEntrySelectionChange: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditPrimaryApiListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.textTitle.setText(R.string.edit_primary_list_title)
        binding.textHelp.setText(R.string.edit_primary_list_help)

        entries = SearchPresets.primaryApiEntries(this).toMutableList()

        binding.spinnerPrimaryApiTypes.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            availableApis.map { it.name }
        )
        val musicBrainzIdx = availableApis.indexOfFirst { it.id == "musicbrainz" }
        binding.spinnerPrimaryApiTypes.setSelection((musicBrainzIdx.takeIf { it >= 0 }) ?: 0)

        refreshPrimaryEntriesSpinner()
        loadEntry(selectedEntryIndex)

        binding.spinnerPrimaryEntries.onItemSelectedListener = object :
            AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (ignoreEntrySelectionChange) return
                commitFieldsToSelectedEntry()
                selectedEntryIndex = position.coerceIn(0, entries.lastIndex.coerceAtLeast(0))
                loadEntry(selectedEntryIndex)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.checkPrimaryEnabled.setOnCheckedChangeListener { _, isChecked ->
            if (selectedEntryIndex in entries.indices) {
                entries[selectedEntryIndex] =
                    entries[selectedEntryIndex].copy(enabled = isChecked)
            }
        }

        binding.editDisplayName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) commitFieldsToSelectedEntry()
        }
        binding.editSearchCmd.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) commitFieldsToSelectedEntry()
        }

        binding.buttonMoveUp.setOnClickListener {
            if (selectedEntryIndex <= 0) return@setOnClickListener
            commitFieldsToSelectedEntry()
            val from = selectedEntryIndex
            val to = selectedEntryIndex - 1
            val item = entries.removeAt(from)
            entries.add(to, item)
            selectedEntryIndex = to
            refreshPrimaryEntriesSpinner()
            loadEntry(selectedEntryIndex)
        }

        binding.buttonMoveDown.setOnClickListener {
            if (selectedEntryIndex >= entries.lastIndex) return@setOnClickListener
            commitFieldsToSelectedEntry()
            val from = selectedEntryIndex
            val to = selectedEntryIndex + 1
            val item = entries.removeAt(from)
            entries.add(to, item)
            selectedEntryIndex = to
            refreshPrimaryEntriesSpinner()
            loadEntry(selectedEntryIndex)
        }

        binding.buttonRemove.setOnClickListener {
            if (entries.size <= 1) {
                Toast.makeText(this, R.string.primary_edit_need_one, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            commitFieldsToSelectedEntry()
            entries.removeAt(selectedEntryIndex)
            selectedEntryIndex = selectedEntryIndex.coerceIn(0, entries.lastIndex)
            refreshPrimaryEntriesSpinner()
            loadEntry(selectedEntryIndex)
        }

        binding.buttonAdd.setOnClickListener {
            val apiTypeIndex = binding.spinnerPrimaryApiTypes.selectedItemPosition
            val preset = availableApis.getOrNull(apiTypeIndex) ?: return@setOnClickListener
            commitFieldsToSelectedEntry()
            entries.add(SearchPresets.PrimaryApiEntry(preset.id, true, preset.name))
            selectedEntryIndex = entries.lastIndex
            refreshPrimaryEntriesSpinner()
            loadEntry(selectedEntryIndex)
        }

        binding.buttonSave.setOnClickListener {
            commitFieldsToSelectedEntry()
            SearchPrefs(this).primaryApiListText = SearchPresets.serializePrimaryApiList(entries)
            setResult(RESULT_OK)
            finish()
        }

        binding.buttonCancel.setOnClickListener { finish() }
    }

    private fun refreshPrimaryEntriesSpinner() {
        ignoreEntrySelectionChange = true

        val labels = entries.mapIndexed { idx, e ->
            "${idx + 1}. ${e.displayName}${if (e.enabled) "" else " (off)"}"
        }
        binding.spinnerPrimaryEntries.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            labels
        )

        val safe = selectedEntryIndex.coerceIn(0, entries.lastIndex.coerceAtLeast(0))
        binding.spinnerPrimaryEntries.setSelection(safe)
        ignoreEntrySelectionChange = false
    }

    private fun loadEntry(index: Int) {
        if (index !in entries.indices) return
        ignoreEntrySelectionChange = true
        val entry = entries[index]

        binding.checkPrimaryEnabled.isChecked = entry.enabled
        binding.editDisplayName.setText(entry.displayName)
        binding.editSearchCmd.setText(entry.cmd)

        val typeIndex = availableApis.indexOfFirst { it.id == entry.cmd }
        binding.spinnerPrimaryApiTypes.setSelection(if (typeIndex >= 0) typeIndex else 0)

        ignoreEntrySelectionChange = false
    }

    private fun commitFieldsToSelectedEntry() {
        if (selectedEntryIndex !in entries.indices) return

        val current = entries[selectedEntryIndex]
        val name = binding.editDisplayName.text?.toString()?.trim().orEmpty()
        val cmd = binding.editSearchCmd.text?.toString()?.trim().orEmpty()
        val enabled = binding.checkPrimaryEnabled.isChecked

        val defaultName = SearchPresets.findPrimaryPresetByCmd(cmd)?.name ?: cmd
        val displayName = if (name.isBlank()) defaultName else name

        entries[selectedEntryIndex] = current.copy(
            cmd = cmd,
            enabled = enabled,
            displayName = displayName,
        )
        refreshPrimaryEntriesSpinner()
        binding.spinnerPrimaryEntries.setSelection(selectedEntryIndex)
    }
}
