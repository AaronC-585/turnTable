package com.turntable.barcodescanner

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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

        val form = binding.collagesForm
        RedactedCollagesSearchForm.setupOrderChoices(form, orderByValues, orderWayValues)

        val initial = intent.getStringExtra(RedactedExtras.INITIAL_QUERY).orEmpty()
        if (initial.isNotBlank()) {
            form.editSearch.setText(initial)
        }

        form.buttonSearch.setOnClickListener { openResults() }
    }

    private fun openResults() {
        val form = binding.collagesForm
        val json = RedactedBrowseParamsCodec.encode(RedactedCollagesSearchForm.buildParams(form, page = 1))
        AppEventLog.log(AppEventLog.Category.REDACTED, "collages search (params length=${json.length})")
        startActivity(
            Intent(this, RedactedCollagesSearchResultsActivity::class.java)
                .putExtra(RedactedExtras.COLLAGES_SEARCH_PARAMS_JSON, json),
        )
    }
}
