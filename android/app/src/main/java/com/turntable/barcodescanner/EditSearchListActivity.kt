package com.turntable.barcodescanner

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.turntable.barcodescanner.databinding.ActivityEditSearchListBinding

class EditSearchListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditSearchListBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Primary (1st) search is edited only in EditPrimaryApiListActivity, not via CLI.
        if (intent.getStringExtra(EXTRA_LIST_TYPE) == "primary") {
            finish()
            return
        }
        binding = ActivityEditSearchListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        setupToolbarHome(binding.toolbar)

        val prefs = SearchPrefs(this)
        binding.textHelp.setText(R.string.edit_list_help_secondary)
        binding.editList.setText(prefs.secondaryListText ?: defaultSecondaryText())

        binding.buttonReset.setOnClickListener {
            binding.editList.setText(defaultSecondaryText())
        }

        binding.buttonSave.setOnClickListener {
            val text = binding.editList.text?.toString().orEmpty()
            if (SearchPresets.parseSecondaryListText(text) == null) {
                Toast.makeText(this, R.string.edit_list_invalid, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            prefs.secondaryListText = text
            setResult(RESULT_OK)
            finish()
        }

        binding.buttonCancel.setOnClickListener {
            finish()
        }
    }

    private fun defaultSecondaryText(): String =
        SearchPresets.secondaryTrackersDefault.joinToString("\n") { "${it.id}|${it.name}|${it.url}" }

    companion object {
        const val EXTRA_LIST_TYPE = "list_type"
        const val LIST_SECONDARY = "secondary"
    }
}

