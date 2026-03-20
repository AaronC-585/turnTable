package com.turntable.barcodescanner

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.turntable.barcodescanner.databinding.ActivityRedactedBrowseBinding
import com.turntable.barcodescanner.redacted.RedactedExtras
import com.turntable.barcodescanner.redacted.RedactedResult
import com.turntable.barcodescanner.redacted.RedactedUiHelper
import com.turntable.barcodescanner.redacted.TwoLineRow
import com.turntable.barcodescanner.redacted.TwoLineRowsAdapter
import org.json.JSONArray

class RedactedBrowseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRedactedBrowseBinding
    private lateinit var api: com.turntable.barcodescanner.redacted.RedactedApiClient
    private var currentPage = 1
    private var totalPages = 1
    private val groupIds = mutableListOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = RedactedUiHelper.requireApi(this) ?: return
        api = c
        binding = ActivityRedactedBrowseBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)

        val initial = intent.getStringExtra(RedactedExtras.INITIAL_QUERY).orEmpty()
        if (initial.isNotBlank()) binding.editSearch.setText(initial)

        val adapter = TwoLineRowsAdapter { pos ->
            val gid = groupIds.getOrNull(pos) ?: return@TwoLineRowsAdapter
            startActivity(
                Intent(this, RedactedTorrentGroupActivity::class.java)
                    .putExtra(RedactedExtras.GROUP_ID, gid)
            )
        }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.buttonSearch.setOnClickListener { currentPage = 1; load(adapter) }
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

        if (initial.isNotBlank()) {
            binding.root.post { load(adapter) }
        }
    }

    private fun load(adapter: TwoLineRowsAdapter) {
        val q = binding.editSearch.text?.toString()?.trim().orEmpty()
        binding.progress.visibility = View.VISIBLE
        Thread {
            val params = buildList {
                if (q.isNotBlank()) add("searchstr" to q)
                add("page" to currentPage.toString())
            }
            val result = api.browse(params)
            runOnUiThread {
                binding.progress.visibility = View.GONE
                when (result) {
                    is RedactedResult.Failure -> {
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
                                    append(" · id ").append(gid)
                                }
                                rows.add(TwoLineRow(name.ifBlank { "(no title)" }, sub))
                                groupIds.add(gid)
                            }
                        }
                        adapter.rows = rows
                        if (rows.isEmpty()) {
                            Toast.makeText(this, R.string.redacted_no_results, Toast.LENGTH_SHORT).show()
                        }
                    }
                    else -> {}
                }
            }
        }.start()
    }
}
