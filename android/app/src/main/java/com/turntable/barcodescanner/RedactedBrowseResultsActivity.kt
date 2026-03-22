package com.turntable.barcodescanner

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.turntable.barcodescanner.databinding.ActivityRedactedBrowseResultsBinding
import com.turntable.barcodescanner.redacted.RedactedBrowseParamsCodec
import com.turntable.barcodescanner.redacted.RedactedExtras
import com.turntable.barcodescanner.redacted.RedactedGazelleTorrentUser
import com.turntable.barcodescanner.redacted.RedactedResult
import com.turntable.barcodescanner.redacted.RedactedUiHelper
import com.turntable.barcodescanner.redacted.TwoLineRow
import com.turntable.barcodescanner.debug.AppEventLog
import com.turntable.barcodescanner.redacted.TwoLineRowsAdapter
import org.json.JSONArray

/**
 * Second step of torrent search: paged list of `browse` results (form is [RedactedBrowseActivity]).
 */
class RedactedBrowseResultsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRedactedBrowseResultsBinding
    private lateinit var api: com.turntable.barcodescanner.redacted.RedactedApiClient
    private lateinit var baseParams: List<Pair<String, String?>>
    private var currentPage = 1
    private var totalPages = 1
    private val groupIds = mutableListOf<Int>()
    /** Only the first successful load may auto-open a single result (not when changing pages). */
    private var allowAutoOpenSingleResult = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = RedactedUiHelper.requireApi(this) ?: return
        api = c
        val json = intent.getStringExtra(RedactedExtras.BROWSE_PARAMS_JSON).orEmpty()
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

        val adapter = TwoLineRowsAdapter { pos ->
            val gid = groupIds.getOrNull(pos) ?: return@TwoLineRowsAdapter
            startActivity(
                Intent(this, RedactedTorrentGroupActivity::class.java)
                    .putExtra(RedactedExtras.GROUP_ID, gid),
            )
        }
        adapter.redactedAuthorizationKey = api.redactedAuthorizationValue()
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

    private fun load(adapter: TwoLineRowsAdapter) {
        binding.progress.visibility = View.VISIBLE
        val params = RedactedBrowseParamsCodec.withPage(baseParams, currentPage)
        Thread {
            val result = api.browse(params)
            runOnUiThread {
                binding.progress.visibility = View.GONE
                when (result) {
                    is RedactedResult.Failure -> {
                        AppEventLog.log(AppEventLog.Category.ERROR, "browse failed: ${result.message}")
                        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                    }
                    is RedactedResult.Success -> {
                        val resp = result.response ?: return@runOnUiThread
                        totalPages = resp.optInt("pages", 1).coerceAtLeast(1)
                        currentPage = resp.optInt("currentPage", currentPage).coerceAtLeast(1)
                        binding.textPage.text = getString(
                            R.string.redacted_page_fmt,
                            currentPage,
                            totalPages,
                        )
                        val arr: JSONArray? = resp.optJSONArray("results")
                        val rows = mutableListOf<TwoLineRow>()
                        groupIds.clear()
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val o = arr.optJSONObject(i) ?: continue
                                val gid = o.optInt("groupId")
                                val name = o.optString("groupName")
                                val artist = o.optString("artist")
                                val year = o.optInt("groupYear", 0)
                                val sub = buildString {
                                    append(artist)
                                    if (year > 0) append(" · ").append(year)
                                }
                                val cover = o.optString("cover").trim().takeIf { it.isNotEmpty() }
                                rows.add(
                                    TwoLineRow(
                                        name.ifBlank { "(no title)" },
                                        sub,
                                        coverUrl = cover,
                                        showSeedingUtorrentIcon = RedactedGazelleTorrentUser.jsonIndicatesUserSeeding(o),
                                    ),
                                )
                                groupIds.add(gid)
                            }
                        }
                        adapter.rows = rows
                        AppEventLog.log(
                            AppEventLog.Category.REDACTED,
                            "browse results page=$currentPage rows=${rows.size} totalPages=$totalPages",
                        )
                        if (rows.isEmpty()) {
                            Toast.makeText(this, R.string.redacted_no_results, Toast.LENGTH_SHORT).show()
                        }
                        val mayAuto = allowAutoOpenSingleResult
                        allowAutoOpenSingleResult = false
                        if (mayAuto && rows.size == 1) {
                            val gid = groupIds.getOrNull(0) ?: 0
                            if (gid > 0) {
                                startActivity(
                                    Intent(this, RedactedTorrentGroupActivity::class.java)
                                        .putExtra(RedactedExtras.GROUP_ID, gid),
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
}
