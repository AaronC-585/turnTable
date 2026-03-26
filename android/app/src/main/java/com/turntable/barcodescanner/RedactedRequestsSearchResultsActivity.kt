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
import com.turntable.barcodescanner.redacted.RedactedRequestTime
import com.turntable.barcodescanner.redacted.RedactedResult
import com.turntable.barcodescanner.redacted.RedactedUiHelper
import org.json.JSONArray
import org.json.JSONObject

/**
 * Paged request search results. Uses the same shell as [RedactedBrowseResultsActivity].
 */
class RedactedRequestsSearchResultsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRedactedBrowseResultsBinding
    private lateinit var api: com.turntable.barcodescanner.redacted.RedactedApiClient
    private lateinit var baseParams: List<Pair<String, String?>>
    private var currentPage = 1
    private var totalPages = 1
    private val requestIds = mutableListOf<Int>()
    private var allowAutoOpenSingleResult = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = RedactedUiHelper.requireApi(this) ?: return
        api = c
        val json = intent.getStringExtra(RedactedExtras.REQUESTS_SEARCH_PARAMS_JSON).orEmpty()
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

        val adapter = RedactedRequestResultsAdapter(this)
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.buttonPrev.setOnClickListener {
            if (currentPage > 1) {
                currentPage--
                load(adapter)
            }
        }
        binding.buttonNext.setOnClickListener {
            if (currentPage < totalPages) {
                currentPage++
                load(adapter)
            }
        }

        load(adapter)
    }

    private fun load(adapter: RedactedRequestResultsAdapter) {
        binding.progress.visibility = View.VISIBLE
        val params = RedactedBrowseParamsCodec.withPage(baseParams, currentPage)
        Thread {
            val result = api.requestsSearch(params)
            runOnUiThread {
                binding.progress.visibility = View.GONE
                when (result) {
                    is RedactedResult.Failure -> {
                        AppEventLog.log(AppEventLog.Category.ERROR, "requests search failed: ${result.message}")
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
                        val rows = mutableListOf<RequestSearchRow>()
                        requestIds.clear()
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val o = arr.optJSONObject(i) ?: continue
                                val rid = o.optInt("requestId")
                                if (rid <= 0) continue
                                rows.add(parseRow(o))
                                requestIds.add(rid)
                            }
                        }
                        adapter.rows = rows
                        AppEventLog.log(
                            AppEventLog.Category.REDACTED,
                            "requests results page=$currentPage rows=${rows.size} totalPages=$totalPages",
                        )
                        if (rows.isEmpty()) {
                            Toast.makeText(this, R.string.redacted_no_results, Toast.LENGTH_SHORT).show()
                        }
                        val mayAuto = allowAutoOpenSingleResult
                        allowAutoOpenSingleResult = false
                        if (mayAuto && rows.size == 1) {
                            val rid = requestIds.getOrNull(0) ?: 0
                            if (rid > 0) {
                                startActivity(
                                    Intent(this, RedactedRequestDetailActivity::class.java)
                                        .putExtra(RedactedExtras.REQUEST_ID, rid),
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

    private fun parseRow(o: JSONObject): RequestSearchRow {
        val title = o.optString("title").trim()
        val year = o.optInt("year", 0)
        val titleLine = buildString {
            append(title.ifBlank { "(no title)" })
            if (year > 0) append(" [").append(year).append("]")
        }
        val tagArr = o.optJSONArray("tags")
        val tagsLine = buildString {
            if (tagArr != null) {
                for (i in 0 until tagArr.length()) {
                    if (i > 0) append(", ")
                    append(tagArr.optString(i))
                }
            }
        }
        val bounty = o.optLong("bounty", o.optLong("totalBounty"))
        val votes = o.optInt("voteCount")
        val filled = o.optBoolean("isFilled")
        val filler = o.optString("fillerName").trim()
        val requestor = o.optString("requestorName").trim()
        return RequestSearchRow(
            requestId = o.optInt("requestId"),
            titleLine = titleLine,
            tagsLine = tagsLine,
            votes = votes,
            bountyBytes = bounty,
            filledYes = filled,
            fillerName = filler,
            requestorName = requestor,
            createdRel = RedactedRequestTime.relative(o.optString("timeAdded")),
            lastVoteRel = RedactedRequestTime.relative(o.optString("lastVote")),
        )
    }

    private fun unwrapResponse(root: JSONObject): JSONObject {
        val inner = root.optJSONObject("response")
        return if (inner != null && (inner.has("results") || inner.has("pages"))) inner else root
    }
}
