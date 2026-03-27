package com.turntable.barcodescanner

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.turntable.barcodescanner.databinding.ActivityRedactedBrowseResultsBinding
import com.turntable.barcodescanner.debug.AppEventLog
import com.turntable.barcodescanner.redacted.RedactedExtras
import com.turntable.barcodescanner.redacted.RedactedGazelleTorrentUser
import com.turntable.barcodescanner.redacted.RedactedResult
import com.turntable.barcodescanner.redacted.RedactedUiHelper
import com.turntable.barcodescanner.redacted.TwoLineRow
import com.turntable.barcodescanner.redacted.TwoLineRowsAdapter
import org.json.JSONArray
import org.json.JSONObject

/**
 * Collage detail: lists torrent groups with the same layout as [RedactedBrowseResultsActivity].
 */
class RedactedCollageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRedactedBrowseResultsBinding
    private lateinit var api: com.turntable.barcodescanner.redacted.RedactedApiClient
    private val groupIds = mutableListOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = RedactedUiHelper.requireApi(this) ?: return
        api = c
        val collageId = intent.getIntExtra(RedactedExtras.COLLAGE_ID, 0)
        if (collageId <= 0) {
            Toast.makeText(this, R.string.redacted_invalid_id, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding = ActivityRedactedBrowseResultsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)
        supportActionBar?.title = getString(R.string.redacted_collage)

        binding.paginationBar.visibility = View.GONE

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

        load(collageId, adapter)
    }

    private fun load(collageId: Int, adapter: TwoLineRowsAdapter) {
        binding.progress.visibility = View.VISIBLE
        Thread {
            val result = api.collage(collageId, showOnlyGroups = false)
            runOnUiThread {
                binding.progress.visibility = View.GONE
                when (result) {
                    is RedactedResult.Failure -> {
                        AppEventLog.log(AppEventLog.Category.ERROR, "collage failed: ${result.message}")
                        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                    }
                    is RedactedResult.Success -> {
                        val resp = result.response ?: return@runOnUiThread
                        val name = resp.optString("name").trim()
                        if (name.isNotEmpty()) {
                            supportActionBar?.title = name
                        }
                        val arr = torrentGroupsArray(resp)
                        val rows = mutableListOf<TwoLineRow>()
                        groupIds.clear()
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val o = arr.optJSONObject(i) ?: continue
                                val gid = o.optInt("groupId")
                                val nameG = o.optString("groupName")
                                val artist = o.optString("artist")
                                val year = o.optInt("groupYear", 0)
                                val sub = buildString {
                                    append(artist)
                                    if (year > 0) append(" · ").append(year)
                                }
                                val cover = o.optString("cover").trim().takeIf { it.isNotEmpty() }
                                rows.add(
                                    TwoLineRow(
                                        nameG.ifBlank { "(no title)" },
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
                            "collage id=$collageId rows=${rows.size}",
                        )
                        if (rows.isEmpty()) {
                            Toast.makeText(this, R.string.redacted_no_results, Toast.LENGTH_SHORT).show()
                        }
                    }
                    else -> {}
                }
            }
        }.start()
    }

    private fun torrentGroupsArray(body: JSONObject): JSONArray? =
        body.optJSONArray("torrentGroup")
            ?: body.optJSONArray("torrentgroup")
            ?: body.optJSONArray("torrentgroups")
}
