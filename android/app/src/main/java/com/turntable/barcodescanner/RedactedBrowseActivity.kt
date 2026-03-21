package com.turntable.barcodescanner

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.turntable.barcodescanner.databinding.ActivityRedactedBrowseBinding
import com.turntable.barcodescanner.redacted.RedactedBrowseParamsCodec
import com.turntable.barcodescanner.redacted.RedactedExtras
import com.turntable.barcodescanner.redacted.RedactedUiHelper

/**
 * First step of in-app torrent search: filter form only. Results open in [RedactedBrowseResultsActivity].
 */
class RedactedBrowseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRedactedBrowseBinding

    private lateinit var orderByValues: Array<String>
    private lateinit var orderWayValues: Array<String>
    private lateinit var encodingValues: Array<String>
    private lateinit var formatValues: Array<String>
    private lateinit var mediaValues: Array<String>
    private lateinit var releaseTypeValues: Array<String>
    private lateinit var hasLogValues: Array<String>
    private lateinit var yesNoValues: Array<String>
    private lateinit var freeTorrentValues: Array<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RedactedUiHelper.requireApi(this) ?: return
        binding = ActivityRedactedBrowseBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)

        orderByValues = resources.getStringArray(R.array.redacted_browse_order_by_values)
        orderWayValues = resources.getStringArray(R.array.redacted_browse_order_way_values)
        encodingValues = resources.getStringArray(R.array.redacted_browse_encoding_values)
        formatValues = resources.getStringArray(R.array.redacted_browse_format_values)
        mediaValues = resources.getStringArray(R.array.redacted_browse_media_values)
        releaseTypeValues = resources.getStringArray(R.array.redacted_browse_release_type_values)
        hasLogValues = resources.getStringArray(R.array.redacted_browse_haslog_values)
        yesNoValues = resources.getStringArray(R.array.redacted_browse_yesno_values)
        freeTorrentValues = resources.getStringArray(R.array.redacted_browse_freetorrent_values)

        bindSpinner(binding.spinnerOrderBy, R.array.redacted_browse_order_by)
        bindSpinner(binding.spinnerOrderWay, R.array.redacted_browse_order_way)
        bindSpinner(binding.spinnerEncoding, R.array.redacted_browse_encoding)
        bindSpinner(binding.spinnerFormat, R.array.redacted_browse_format)
        bindSpinner(binding.spinnerMedia, R.array.redacted_browse_media)
        bindSpinner(binding.spinnerReleaseType, R.array.redacted_browse_release_type)
        bindSpinner(binding.spinnerHasLog, R.array.redacted_browse_haslog)
        bindSpinner(binding.spinnerHasCue, R.array.redacted_browse_yesno)
        bindSpinner(binding.spinnerScene, R.array.redacted_browse_yesno)
        bindSpinner(binding.spinnerVanityHouse, R.array.redacted_browse_yesno)
        bindSpinner(binding.spinnerFreeTorrent, R.array.redacted_browse_freetorrent)

        binding.spinnerOrderBy.setSelection(1.coerceAtMost(orderByValues.size - 1))
        binding.spinnerOrderWay.setSelection(1.coerceAtMost(orderWayValues.size - 1))

        binding.toggleSearchMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val advanced = checkedId == R.id.btnModeAdvanced
            binding.panelBasic.visibility = if (advanced) View.GONE else View.VISIBLE
            binding.panelAdvanced.visibility = if (advanced) View.VISIBLE else View.GONE
        }

        val initial = intent.getStringExtra(RedactedExtras.INITIAL_QUERY).orEmpty()
        if (initial.isNotBlank()) {
            binding.editSearchStr.setText(initial)
        }

        binding.buttonSearch.setOnClickListener { openResults() }
        binding.buttonReset.setOnClickListener { resetForm() }

        if (initial.isNotBlank()) {
            binding.root.post { openResults() }
        }
    }

    private fun openResults() {
        val json = RedactedBrowseParamsCodec.encode(buildBrowseParams(page = 1))
        startActivity(
            Intent(this, RedactedBrowseResultsActivity::class.java)
                .putExtra(RedactedExtras.BROWSE_PARAMS_JSON, json),
        )
    }

    private fun bindSpinner(spinner: Spinner, arrayRes: Int) {
        val adapter = ArrayAdapter.createFromResource(
            this,
            arrayRes,
            android.R.layout.simple_spinner_dropdown_item,
        )
        spinner.adapter = adapter
    }

    private fun resetForm() {
        binding.editSearchStr.text?.clear()
        binding.editArtistName.text?.clear()
        binding.editGroupName.text?.clear()
        binding.editRecordLabel.text?.clear()
        binding.editCatalogueNumber.text?.clear()
        binding.editYear.text?.clear()
        binding.editRemasterTitle.text?.clear()
        binding.editRemasterYear.text?.clear()
        binding.editFileList.text?.clear()
        binding.editTorrentDescription.text?.clear()
        binding.editTaglist.text?.clear()
        binding.radioTagsAny.isChecked = true
        binding.switchGroupResults.isChecked = false
        binding.checkCat1.isChecked = false
        binding.checkCat2.isChecked = false
        binding.checkCat3.isChecked = false
        binding.checkCat4.isChecked = false
        binding.checkCat5.isChecked = false
        binding.checkCat6.isChecked = false
        binding.checkCat7.isChecked = false
        binding.toggleSearchMode.check(R.id.btnModeBasic)
        binding.spinnerOrderBy.setSelection(1.coerceAtMost(orderByValues.size - 1))
        binding.spinnerOrderWay.setSelection(1.coerceAtMost(orderWayValues.size - 1))
        binding.spinnerEncoding.setSelection(0)
        binding.spinnerFormat.setSelection(0)
        binding.spinnerMedia.setSelection(0)
        binding.spinnerReleaseType.setSelection(0)
        binding.spinnerHasLog.setSelection(0)
        binding.spinnerHasCue.setSelection(0)
        binding.spinnerScene.setSelection(0)
        binding.spinnerVanityHouse.setSelection(0)
        binding.spinnerFreeTorrent.setSelection(0)
    }

    private fun Spinner.apiValue(values: Array<String>): String? {
        val i = selectedItemPosition
        if (i < 0 || i >= values.size) return null
        return values[i].takeIf { it.isNotEmpty() }
    }

    private fun MutableList<Pair<String, String?>>.putNonBlank(key: String, edit: TextInputEditText) {
        val v = edit.text?.toString()?.trim()
        if (!v.isNullOrEmpty()) add(key to v)
    }

    private fun buildBrowseParams(page: Int): List<Pair<String, String?>> = buildList {
        add("page" to page.toString())
        when (binding.toggleSearchMode.checkedButtonId) {
            R.id.btnModeBasic -> {
                val s = binding.editSearchStr.text?.toString()?.trim()
                if (!s.isNullOrEmpty()) add("searchstr" to s)
            }
            R.id.btnModeAdvanced -> {
                putNonBlank("artistname", binding.editArtistName)
                putNonBlank("groupname", binding.editGroupName)
                putNonBlank("recordlabel", binding.editRecordLabel)
                putNonBlank("cataloguenumber", binding.editCatalogueNumber)
                putNonBlank("year", binding.editYear)
                putNonBlank("remastertitle", binding.editRemasterTitle)
                putNonBlank("remasteryear", binding.editRemasterYear)
                putNonBlank("filelist", binding.editFileList)
                putNonBlank("description", binding.editTorrentDescription)
                binding.spinnerEncoding.apiValue(encodingValues)?.let { add("encoding" to it) }
                binding.spinnerFormat.apiValue(formatValues)?.let { add("format" to it) }
                binding.spinnerMedia.apiValue(mediaValues)?.let { add("media" to it) }
                binding.spinnerReleaseType.apiValue(releaseTypeValues)?.let { add("releasetype" to it) }
                binding.spinnerHasLog.apiValue(hasLogValues)?.let { add("haslog" to it) }
                binding.spinnerHasCue.apiValue(yesNoValues)?.let { add("hascue" to it) }
                binding.spinnerScene.apiValue(yesNoValues)?.let { add("scene" to it) }
                binding.spinnerVanityHouse.apiValue(yesNoValues)?.let { add("vanityhouse" to it) }
                binding.spinnerFreeTorrent.apiValue(freeTorrentValues)?.let { add("freetorrent" to it) }
            }
        }

        putNonBlank("taglist", binding.editTaglist)
        val tags = binding.editTaglist.text?.toString()?.trim()
        if (!tags.isNullOrEmpty()) {
            add("tags_type" to if (binding.radioTagsAll.isChecked) "1" else "0")
        }

        binding.spinnerOrderBy.apiValue(orderByValues)?.let { add("order_by" to it) }
        binding.spinnerOrderWay.apiValue(orderWayValues)?.let { add("order_way" to it) }

        if (binding.switchGroupResults.isChecked) add("group_results" to "1")

        val checks = listOf(
            binding.checkCat1 to "filter_cat[1]",
            binding.checkCat2 to "filter_cat[2]",
            binding.checkCat3 to "filter_cat[3]",
            binding.checkCat4 to "filter_cat[4]",
            binding.checkCat5 to "filter_cat[5]",
            binding.checkCat6 to "filter_cat[6]",
            binding.checkCat7 to "filter_cat[7]",
        )
        for ((cb, key) in checks) {
            if (cb.isChecked) add(key to "1")
        }
    }
}
