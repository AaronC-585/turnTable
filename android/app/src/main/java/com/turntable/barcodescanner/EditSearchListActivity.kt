package com.turntable.barcodescanner

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.turntable.barcodescanner.databinding.ActivityEditSearchListBinding

class EditSearchListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditSearchListBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditSearchListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val type = intent.getStringExtra(EXTRA_LIST_TYPE) ?: LIST_SECONDARY
        val prefs = SearchPrefs(this)

        when (type) {
            LIST_PRIMARY -> {
                binding.textTitle.setText(R.string.edit_list_title_primary)
                binding.textHelp.setText(R.string.edit_list_help_primary)
                binding.editList.setText(prefs.primaryListText ?: defaultPrimaryText())
            }
            else -> {
                binding.textTitle.setText(R.string.edit_list_title_secondary)
                binding.textHelp.setText(R.string.edit_list_help_secondary)
                binding.editList.setText(prefs.secondaryListText ?: defaultSecondaryText())
            }
        }

        binding.buttonReset.setOnClickListener {
            when (type) {
                LIST_PRIMARY -> binding.editList.setText(defaultPrimaryText())
                else -> binding.editList.setText(defaultSecondaryText())
            }
        }

        binding.buttonSave.setOnClickListener {
            val text = binding.editList.text?.toString().orEmpty()
            val ok = when (type) {
                LIST_PRIMARY -> SearchPresets.parsePrimaryListText(text) != null
                else -> SearchPresets.parseSecondaryListText(text) != null
            }
            if (!ok) {
                Toast.makeText(this, R.string.edit_list_invalid, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            when (type) {
                LIST_PRIMARY -> prefs.primaryListText = text
                else -> prefs.secondaryListText = text
            }
            setResult(RESULT_OK)
            finish()
        }

        binding.buttonCancel.setOnClickListener {
            finish()
        }
    }

    private fun defaultPrimaryText(): String =
        SearchPresets.primaryMusicInfoDefault.joinToString("\n") { "${it.id}|${it.name}" }

    private fun defaultSecondaryText(): String =
        SearchPresets.secondaryTrackersDefault.joinToString("\n") { "${it.id}|${it.name}|${it.url}" }

    companion object {
        const val EXTRA_LIST_TYPE = "list_type"
        const val LIST_PRIMARY = "primary"
        const val LIST_SECONDARY = "secondary"
    }
}

