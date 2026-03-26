package com.turntable.barcodescanner

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.turntable.barcodescanner.databinding.ActivityRedactedCollagesSearchBinding
import com.turntable.barcodescanner.debug.AppEventLog
import com.turntable.barcodescanner.redacted.RedactedBrowseParamsCodec
import com.turntable.barcodescanner.redacted.RedactedExtras
import com.turntable.barcodescanner.redacted.RedactedUiHelper

/**
 * Collage search form (same query shape as `collages.php`). Results: [RedactedCollagesSearchResultsActivity].
 */
class RedactedCollagesSearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRedactedCollagesSearchBinding
    private lateinit var orderByValues: Array<String>
    private lateinit var orderWayValues: Array<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RedactedUiHelper.requireApi(this) ?: return
        binding = ActivityRedactedCollagesSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)

        orderByValues = resources.getStringArray(R.array.redacted_collages_order_by_values)
        orderWayValues = resources.getStringArray(R.array.redacted_collages_order_way_values)

        ExpandableBulletChoice.bindFromArray(
            binding.expandOrderBy,
            null,
            R.array.redacted_collages_order_by,
            1.coerceAtMost(orderByValues.size - 1),
        )
        ExpandableBulletChoice.bindFromArray(
            binding.expandOrderWay,
            null,
            R.array.redacted_collages_order_way,
            1.coerceAtMost(orderWayValues.size - 1),
        )

        val initial = intent.getStringExtra(RedactedExtras.INITIAL_QUERY).orEmpty()
        if (initial.isNotBlank()) {
            binding.editSearch.setText(initial)
        }

        binding.buttonSearch.setOnClickListener { openResults() }
    }

    private fun openResults() {
        val json = RedactedBrowseParamsCodec.encode(buildParams(page = 1))
        AppEventLog.log(AppEventLog.Category.REDACTED, "collages search (params length=${json.length})")
        startActivity(
            Intent(this, RedactedCollagesSearchResultsActivity::class.java)
                .putExtra(RedactedExtras.COLLAGES_SEARCH_PARAMS_JSON, json),
        )
    }

    private fun MutableList<Pair<String, String?>>.putNonBlank(key: String, edit: TextInputEditText) {
        val v = edit.text?.toString()?.trim()
        if (!v.isNullOrEmpty()) add(key to v)
    }

    private fun buildParams(page: Int): List<Pair<String, String?>> = buildList {
        add("page" to page.toString())
        putNonBlank("search", binding.editSearch)
        putNonBlank("tags", binding.editTags)
        val tagText = binding.editTags.text?.toString()?.trim()
        if (!tagText.isNullOrEmpty()) {
            add("tags_type" to if (binding.radioTagsAll.isChecked) "1" else "0")
        }
        val type = if (binding.radioSearchNames.isChecked) "name" else "description"
        add("type" to type)

        val checks = listOf(
            binding.checkCat0,
            binding.checkCat1,
            binding.checkCat2,
            binding.checkCat3,
            binding.checkCat4,
            binding.checkCat5,
            binding.checkCat6,
            binding.checkCat7,
        )
        for (i in checks.indices) {
            if (checks[i].isChecked) {
                add("cats[$i]" to "1")
            }
        }

        binding.expandOrderBy.listExpandChoices.apiValue(orderByValues)?.let { add("order" to it) }
        binding.expandOrderWay.listExpandChoices.apiValue(orderWayValues)?.let { add("sort" to it) }
    }
}
