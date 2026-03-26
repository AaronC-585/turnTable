package com.turntable.barcodescanner

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.turntable.barcodescanner.databinding.ActivityRedactedBrowseResultsBinding
import com.turntable.barcodescanner.debug.AppEventLog
import com.turntable.barcodescanner.redacted.RedactedBrowseParamsCodec
import com.turntable.barcodescanner.redacted.RedactedExtras
import com.turntable.barcodescanner.redacted.RedactedResult
import com.turntable.barcodescanner.redacted.RedactedUiHelper
import com.turntable.barcodescanner.redacted.TwoLineRow
import com.turntable.barcodescanner.redacted.TwoLineRowsAdapter
import org.json.JSONArray
import org.json.JSONObject

/**
 * Paged list of `collages` search results (form is [RedactedCollagesSearchActivity]).
 * Uses the same screen layout as [RedactedBrowseResultsActivity].
 */
class RedactedCollagesSearchResultsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRedactedBrowseResultsBinding
    private lateinit var api: com.turntable.barcodescanner.redacted.RedactedApiClient
    private lateinit var baseParams: List<Pair<String, String?>>
    private var currentPage = 1
    private var totalPages = 1
    private val collageIds = mutableListOf<Int>()
    private var allowAutoOpenSingleResult = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = RedactedUiHelper.requireApi(this) ?: return
        api = c
        val json = intent.getStringExtra(RedactedExtras.COLLAGES_SEARCH_PARAMS_JSON).orEmpty()
        if (json.isBlank()) {
            Toast.makeText(this, R.string.redacted_browse_missing_params, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        baseParams = try {
            RedactedBrowseParamsCodec.decode(json)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.redacted_browse_missing_params, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        currentPage = baseParams.firstOrNull { it.first == "page" }?.second?.toIntOrNull() ?: 1

        binding = ActivityRedactedBrowseResultsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)

        val categoryLabels = resources.getStringArray(R.array.redacted_collages_category_labels)

        val adapter = TwoLineRowsAdapter { pos ->
            val cid = collageIds.getOrNull(pos) ?: return@TwoLineRowsAdapter
            startActivity(
                Intent(this, RedactedCollageActivity::class.java)
                    .putExtra(RedactedExtras.COLLAGE_ID, cid),
            )
        }
        adapter.redactedAuthorizationKey = api.redactedAuthorizationValue()
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.buttonPrev.setOnClickListener {
            if (currentPage > 1) {
                currentPage--
                load(adapter, categoryLabels)
            }
        }
        binding.buttonNext.setOnClickListener {
            if (currentPage < totalPages) {
                currentPage++
                load(adapter, categoryLabels)
            }
        }

        load(adapter, categoryLabels)
    }

    private fun load(adapter: TwoLineRowsAdapter, categoryLabels: Array<String>) {
        binding.progress.visibility = View.VISIBLE
        val params = RedactedBrowseParamsCodec.withPage(baseParams, currentPage)
        Thread {
            val result = api.collagesSearch(params)
            runOnUiThread {
                binding.progress.visibility = View.GONE
                when (result) {
                    is RedactedResult.Failure -> {
                        AppEventLog.log(AppEventLog.Category.ERROR, "collages search failed: ${result.message}")
                        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                    }
                    is RedactedResult.Success -> {
                        val resp = result.response ?: return@runOnUiThread
                        val body = unwrapResponse(resp)
                        totalPages = body.optInt("pages", 1).coerceAtLeast(1)
                        currentPage = body.optInt("currentPage", currentPage).coerceAtLeast(1)
                        binding.textPage.text = getString(
                            R.string.redacted_page_fmt,
                            currentPage,
                            totalPages,
                        )
                        val arr: JSONArray? = body.optJSONArray("results")
                        val rows = mutableListOf<TwoLineRow>()
                        collageIds.clear()
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val o = arr.optJSONObject(i) ?: continue
                                val cid = o.optInt("collageId")
                                    .takeIf { it > 0 }
                                    ?: o.optInt("id").takeIf { it > 0 }
                                    ?: continue
                                val name = o.optString("name").trim().ifBlank { "(no title)" }
                                val catId = o.optInt("category_id", o.optInt("categoryId"))
                                val catLabel = categoryLabels.getOrNull(catId).orEmpty()
                                    .ifBlank { o.optString("categoryName") }
                                val subs = o.optInt("subscriber_total", o.optInt("subscribers"))
                                val sub = buildString {
                                    if (catLabel.isNotBlank()) append(catLabel)
                                    if (subs > 0) {
                                        if (isNotEmpty()) append(" · ")
                                        append(subs).append(" ").append(getString(R.string.redacted_collages_subscribers_suffix))
                                    }
                                }
                                rows.add(TwoLineRow(name, sub))
                                collageIds.add(cid)
                            }
                        }
                        adapter.rows = rows
                        AppEventLog.log(
                            AppEventLog.Category.REDACTED,
                            "collages results page=$currentPage rows=${rows.size} totalPages=$totalPages",
                        )
                        if (rows.isEmpty()) {
                            Toast.makeText(this, R.string.redacted_no_results, Toast.LENGTH_SHORT).show()
                        }
                        val mayAuto = allowAutoOpenSingleResult
                        allowAutoOpenSingleResult = false
                        if (mayAuto && rows.size == 1) {
                            val cid = collageIds.getOrNull(0) ?: 0
                            if (cid > 0) {
                                startActivity(
                                    Intent(this, RedactedCollageActivity::class.java)
                                        .putExtra(RedactedExtras.COLLAGE_ID, cid),
                                )
                                finish()
                                return@runOnUiThread
                            }
                        }
                    }
                    else -> {}
                }
            }
        }.start()
    }

    /** Some endpoints wrap payload in `response`. */
    private fun unwrapResponse(root: JSONObject): JSONObject {
        val inner = root.optJSONObject("response")
        return if (inner != null && (inner.has("results") || inner.has("pages"))) inner else root
    }
}
