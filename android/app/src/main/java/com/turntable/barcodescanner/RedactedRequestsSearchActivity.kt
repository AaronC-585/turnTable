package com.turntable.barcodescanner

import android.content.Intent
import android.os.Bundle
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import com.turntable.barcodescanner.databinding.ActivityRedactedRequestsSearchBinding
import com.turntable.barcodescanner.debug.AppEventLog
import com.turntable.barcodescanner.redacted.RedactedBrowseParamsCodec
import com.turntable.barcodescanner.redacted.RedactedExtras
import com.turntable.barcodescanner.redacted.RedactedUiHelper

/**
 * Request search form (same query shape as site `requests.php`). Results: [RedactedRequestsSearchResultsActivity].
 */
class RedactedRequestsSearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRedactedRequestsSearchBinding
    private lateinit var categoryChecks: Array<CheckBox>
    private lateinit var releaseBoxes: List<CheckBox>
    private lateinit var formatBoxes: List<CheckBox>
    private lateinit var bitrateBoxes: List<CheckBox>
    private lateinit var mediaBoxes: List<CheckBox>

    private lateinit var releaseValues: Array<String>
    private lateinit var formatValues: Array<String>
    private lateinit var bitrateValues: Array<String>
    private lateinit var mediaValues: Array<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RedactedUiHelper.requireApi(this) ?: return
        binding = ActivityRedactedRequestsSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)

        val catLabels = resources.getStringArray(R.array.redacted_requests_category_labels)
        categoryChecks = Array(catLabels.size) { i ->
            CheckBox(this).apply {
                text = catLabels[i]
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@RedactedRequestsSearchActivity, R.color.app_text_secondary))
                isChecked = true
                binding.rowCategories.addView(this)
            }
        }

        val relLabels = resources.getStringArray(R.array.redacted_browse_release_type).drop(1).toTypedArray()
        releaseValues = resources.getStringArray(R.array.redacted_browse_release_type_values).drop(1).toTypedArray()
        releaseBoxes = populateTwoColumnGrid(binding.containerReleaseTypes, relLabels)

        val fmtLabels = resources.getStringArray(R.array.redacted_browse_format).drop(1).toTypedArray()
        formatValues = resources.getStringArray(R.array.redacted_browse_format_values).drop(1).toTypedArray()
        formatBoxes = populateTwoColumnGrid(binding.containerFormats, fmtLabels)

        val brLabels = resources.getStringArray(R.array.redacted_browse_encoding).drop(1).toTypedArray()
        bitrateValues = resources.getStringArray(R.array.redacted_browse_encoding_values).drop(1).toTypedArray()
        bitrateBoxes = populateTwoColumnGrid(binding.containerBitrates, brLabels)

        val medLabels = resources.getStringArray(R.array.redacted_browse_media).drop(1).toTypedArray()
        mediaValues = resources.getStringArray(R.array.redacted_browse_media_values).drop(1).toTypedArray()
        mediaBoxes = populateTwoColumnGrid(binding.containerMedia, medLabels)

        wireAllToggle(binding.checkReleaseAll, releaseBoxes)
        wireAllToggle(binding.checkFormatAll, formatBoxes)
        wireAllToggle(binding.checkBitrateAll, bitrateBoxes)
        wireAllToggle(binding.checkMediaAll, mediaBoxes)

        binding.buttonSearch.setOnClickListener { openResults() }
    }

    private fun populateTwoColumnGrid(container: LinearLayout, labels: Array<String>): List<CheckBox> {
        val boxes = mutableListOf<CheckBox>()
        var i = 0
        while (i < labels.size) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            }
            val cb1 = makeFilterCheck(labels[i])
            boxes.add(cb1)
            row.addView(cb1)
            if (i + 1 < labels.size) {
                val cb2 = makeFilterCheck(labels[i + 1])
                boxes.add(cb2)
                row.addView(cb2)
            }
            i += 2
            container.addView(row)
        }
        return boxes
    }

    private fun makeFilterCheck(label: String): CheckBox =
        CheckBox(this).apply {
            text = label
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@RedactedRequestsSearchActivity, R.color.app_text_secondary))
            isChecked = true
        }

    private fun wireAllToggle(all: CheckBox, boxes: List<CheckBox>) {
        fun applyAllState() {
            val on = all.isChecked
            boxes.forEach { cb ->
                cb.isChecked = on
                cb.isEnabled = !on
            }
        }
        all.setOnCheckedChangeListener { _, _ -> applyAllState() }
        applyAllState()
    }

    private fun openResults() {
        val json = RedactedBrowseParamsCodec.encode(buildParams(page = 1))
        AppEventLog.log(AppEventLog.Category.REDACTED, "requests search (params length=${json.length})")
        startActivity(
            Intent(this, RedactedRequestsSearchResultsActivity::class.java)
                .putExtra(RedactedExtras.REQUESTS_SEARCH_PARAMS_JSON, json),
        )
    }

    private fun MutableList<Pair<String, String?>>.putNonBlank(key: String, edit: TextInputEditText) {
        val v = edit.text?.toString()?.trim()
        if (!v.isNullOrEmpty()) add(key to v)
    }

    private fun musicCategorySelected(): Boolean = categoryChecks.getOrNull(0)?.isChecked == true

    private fun buildParams(page: Int): List<Pair<String, String?>> = buildList {
        add("page" to page.toString())
        putNonBlank("search", binding.editSearch)
        if (binding.switchIncludeDesc.isChecked) {
            add("search_comments" to "1")
        }
        putNonBlank("tags", binding.editTags)
        val tagText = binding.editTags.text?.toString()?.trim()
        if (!tagText.isNullOrEmpty()) {
            val allTags = binding.radioTagsAll.isChecked
            add("tag_type" to if (allTags) "1" else "0")
            add("tags_type" to if (allTags) "1" else "0")
        }
        if (binding.switchIncludeFilled.isChecked) {
            add("show_filled" to "1")
        }
        if (binding.switchIncludeOld.isChecked) {
            add("include_old" to "1")
        }

        val allCats = categoryChecks.all { it.isChecked }
        if (!allCats) {
            categoryChecks.forEachIndexed { i, cb ->
                if (cb.isChecked) {
                    add("filter_cat[$i]" to i.toString())
                }
            }
        }

        if (!binding.checkReleaseAll.isChecked) {
            var idx = 0
            releaseBoxes.forEachIndexed { i, cb ->
                if (cb.isChecked) {
                    add("releases[$idx]" to releaseValues[i])
                    idx++
                }
            }
        }

        if (musicCategorySelected() || categoryChecks.getOrNull(3)?.isChecked == true ||
            categoryChecks.getOrNull(5)?.isChecked == true
        ) {
            if (!binding.checkFormatAll.isChecked) {
                if (binding.switchFormatStrict.isChecked) add("formats_strict" to "1")
                var fi = 0
                formatBoxes.forEachIndexed { i, cb ->
                    if (cb.isChecked) {
                        add("formats[$fi]" to formatValues[i])
                        fi++
                    }
                }
            }
            if (!binding.checkBitrateAll.isChecked) {
                if (binding.switchBitrateStrict.isChecked) add("bitrates_strict" to "1")
                var bi = 0
                bitrateBoxes.forEachIndexed { i, cb ->
                    if (cb.isChecked) {
                        add("bitrates[$bi]" to bitrateValues[i])
                        bi++
                    }
                }
            }
        }

        if (musicCategorySelected()) {
            if (!binding.checkMediaAll.isChecked) {
                if (binding.switchMediaStrict.isChecked) add("media_strict" to "1")
                var mi = 0
                mediaBoxes.forEachIndexed { i, cb ->
                    if (cb.isChecked) {
                        add("media[$mi]" to mediaValues[i])
                        mi++
                    }
                }
            }
        }
    }
}
