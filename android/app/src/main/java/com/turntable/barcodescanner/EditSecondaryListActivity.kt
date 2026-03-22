package com.turntable.barcodescanner

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.turntable.barcodescanner.databinding.ActivityEditSecondaryListBinding

class EditSecondaryListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditSecondaryListBinding
    private val list = mutableListOf<SearchPresets.Preset>()
    private var selectedIndex = 0

    private val httpMethods = listOf(SearchPrefs.METHOD_GET, SearchPrefs.METHOD_POST)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditSecondaryListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        setupToolbarHome(binding.toolbar)

        binding.textHelp.setText(R.string.edit_secondary_list_help)

        val prefs = SearchPrefs(this)

        binding.editContentType.setText(prefs.postContentType ?: "application/json")
        binding.editPostBody.setText(prefs.postBody ?: """{"code":"%s"}""")
        binding.editPostHeaders.setText(prefs.postHeaders ?: "")

        val methodIndex = httpMethods.indexOf(prefs.method).coerceAtLeast(0)
        ListViewSingleChoice.bindStrings(binding.listMethod, httpMethods, methodIndex) { position ->
            updatePostOptionsVisibility(httpMethods[position] == SearchPrefs.METHOD_POST)
        }
        updatePostOptionsVisibility(httpMethods[methodIndex] == SearchPrefs.METHOD_POST)

        binding.textSecondaryVariablesHelp.text = buildString {
            appendLine(getString(R.string.secondary_variables_get_header))
            SecondarySearchSubstitution.urlVariableRows.forEach { (k, d) ->
                append(k).append(" → ").appendLine(d)
            }
            appendLine()
            appendLine(getString(R.string.secondary_variables_post_header))
            SecondarySearchSubstitution.postVariableRows.forEach { (k, d) ->
                append(k).append(" → ").appendLine(d)
            }
        }

        val saved = SearchPresets.parseSecondaryListText(prefs.secondaryListText)
        list.clear()
        list.addAll(saved ?: SearchPresets.secondaryTrackersDefault)

        bindPresetList()

        binding.editName.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) saveCurrentToIndex(selectedIndex) }
        binding.editUrl.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) saveCurrentToIndex(selectedIndex) }

        binding.buttonMoveUp.setOnClickListener {
            saveCurrentToIndex(selectedIndex)
            if (selectedIndex > 0) {
                list.removeAt(selectedIndex).let { list.add(selectedIndex - 1, it) }
                selectedIndex--
                refreshPresetListAndSelection()
                showPresetAt(selectedIndex)
            }
        }
        binding.buttonMoveDown.setOnClickListener {
            saveCurrentToIndex(selectedIndex)
            if (selectedIndex < list.size - 1) {
                list.removeAt(selectedIndex).let { list.add(selectedIndex + 1, it) }
                selectedIndex++
                refreshPresetListAndSelection()
                showPresetAt(selectedIndex)
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
            refreshPresetListAndSelection()
            showPresetAt(selectedIndex)
        }
        binding.buttonAdd.setOnClickListener {
            saveCurrentToIndex(selectedIndex)
            list.add(SearchPresets.Preset(SearchPresets.CUSTOM_ID, "New search", "https://example.com/search?q=%s"))
            selectedIndex = list.size - 1
            refreshPresetListAndSelection()
            showPresetAt(selectedIndex)
        }

        binding.buttonSave.setOnClickListener {
            val name = binding.editName.text?.toString()?.trim().orEmpty()
            val url = binding.editUrl.text.normalizeUrlInput()
            if (name.isBlank() || url.isBlank() || !url.contains("%s")) {
                Toast.makeText(this, R.string.secondary_edit_invalid, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (selectedIndex in list.indices) list[selectedIndex] = list[selectedIndex].copy(name = name, url = url)
            prefs.secondaryListText = SearchPresets.serializeSecondaryList(list)
            val mPos = ListViewSingleChoice.selectedIndex(binding.listMethod).coerceIn(0, httpMethods.lastIndex)
            prefs.method = httpMethods[mPos]
            prefs.postContentType = binding.editContentType.text?.toString()?.trim()
            prefs.postBody = binding.editPostBody.text?.toString()?.trim()
            prefs.postHeaders = binding.editPostHeaders.text?.toString()?.trim()
            setResult(RESULT_OK)
            finish()
        }
        binding.buttonCancel.setOnClickListener { finish() }

        showPresetAt(0)
    }

    private val prefs: SearchPrefs get() = SearchPrefs(this)

    private fun bindPresetList() {
        ListViewSingleChoice.bindStrings(binding.listPresets, list.map { it.name }, selectedIndex.coerceIn(0, list.lastIndex.coerceAtLeast(0))) { position ->
            saveCurrentToIndex(selectedIndex)
            selectedIndex = position.coerceIn(0, list.size - 1)
            showPresetAt(selectedIndex)
        }
    }

    private fun saveCurrentToIndex(index: Int) {
        if (index !in list.indices) return
        val name = binding.editName.text?.toString()?.trim().orEmpty()
        val url = binding.editUrl.text.normalizeUrlInput()
        list[index] = list[index].copy(name = name.ifBlank { list[index].name }, url = url.ifBlank { list[index].url })
    }

    private fun showPresetAt(index: Int) {
        if (index !in list.indices) return
        binding.editName.setText(list[index].name)
        binding.editUrl.setText(list[index].url)
        binding.listPresets.post {
            if (index in list.indices) {
                binding.listPresets.setItemChecked(index, true)
            }
        }
    }

    private fun refreshPresetListAndSelection() {
        bindPresetList()
    }

    private fun updatePostOptionsVisibility(show: Boolean) {
        binding.postOptionsContainer.visibility = if (show) View.VISIBLE else View.GONE
    }
}
