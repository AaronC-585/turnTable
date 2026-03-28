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
                        val built = buildCollageTorrentGroupRows(resp)
                        val rows = built.first
                        groupIds.clear()
                        groupIds.addAll(built.second)
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
            ?: body.optJSONArray("torrentGroups")

    private fun torrentGroupIdListArray(body: JSONObject): JSONArray? =
        body.optJSONArray("torrentGroupIDList") ?: body.optJSONArray("torrentGroupIdList")

    private fun idListElementToKey(arr: JSONArray, i: Int): String? {
        if (arr.isNull(i)) return null
        return try {
            arr.getInt(i).toString()
        } catch (_: Exception) {
            arr.optString(i).trim().takeIf { it.isNotEmpty() }
        }
    }

    private fun torrentGroupIdFromJson(o: JSONObject): Int? {
        fun fromField(name: String): Int? {
            if (!o.has(name) || o.isNull(name)) return null
            return when (val v = o.opt(name)) {
                is Number -> v.toInt().takeIf { it > 0 }
                is String -> v.trim().toIntOrNull()?.takeIf { it > 0 }
                else -> o.optString(name).trim().toIntOrNull()?.takeIf { it > 0 }
            }
        }
        return fromField("id") ?: fromField("groupId")
    }

    private fun indexTorrentGroupsById(arr: JSONArray?): Map<String, JSONObject> {
        if (arr == null) return emptyMap()
        val m = LinkedHashMap<String, JSONObject>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val key = torrentGroupIdFromJson(o)?.toString() ?: continue
            m[key] = o
        }
        return m
    }

    private fun collageGroupCoverUrl(o: JSONObject): String? {
        for (k in listOf("wikiImage", "cover", "image", "picture", "thumb", "coverUrl")) {
            val s = o.optString(k).trim()
            if (s.isNotEmpty()) return s
        }
        return null
    }

    private fun primaryArtistLineFromCollageGroup(o: JSONObject): String {
        o.optString("artist").trim().takeIf { it.isNotEmpty() }?.let { return it }
        val mi = o.optJSONObject("musicInfo") ?: return ""
        fun names(key: String): List<String> {
            val arr = mi.optJSONArray(key) ?: return emptyList()
            return buildList {
                for (j in 0 until arr.length()) {
                    arr.optJSONObject(j)?.optString("name")?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
                }
            }
        }
        val ordered = LinkedHashSet<String>()
        for (key in listOf("artists", "dj", "composers", "conductor", "with", "remixedBy", "producer")) {
            names(key).forEach { ordered.add(it) }
        }
        return ordered.joinToString(", ")
    }

    private fun groupJsonIndicatesUserSeeding(o: JSONObject): Boolean {
        if (RedactedGazelleTorrentUser.jsonIndicatesUserSeeding(o)) return true
        val torrents = o.optJSONArray("torrents") ?: return false
        for (j in 0 until torrents.length()) {
            torrents.optJSONObject(j)?.let { t ->
                if (RedactedGazelleTorrentUser.jsonIndicatesUserSeeding(t)) return true
            }
        }
        return false
    }

    private fun parseCollageTorrentGroupObject(o: JSONObject): Pair<Int, TwoLineRow>? {
        val gid = torrentGroupIdFromJson(o) ?: return null
        val title = o.optString("name").trim()
            .ifBlank { o.optString("groupName").trim() }
            .ifBlank { "(no title)" }
        val artistLine = primaryArtistLineFromCollageGroup(o)
        val year = o.optString("year").trim().toIntOrNull()
            ?: o.optInt("groupYear", 0)
        val sub = buildString {
            append(artistLine)
            if (year > 0) {
                if (isNotEmpty()) append(" · ")
                append(year)
            }
        }
        val cover = collageGroupCoverUrl(o)
        val row = TwoLineRow(
            title = title,
            subtitle = sub,
            coverUrl = cover,
            showSeedingUtorrentIcon = groupJsonIndicatesUserSeeding(o),
        )
        return gid to row
    }

    /** Rows and parallel [groupIds] for click handling; order follows [torrentGroupIDList] when present. */
    private fun buildCollageTorrentGroupRows(resp: JSONObject): Pair<List<TwoLineRow>, List<Int>> {
        val groupsArr = torrentGroupsArray(resp)
        val byId = indexTorrentGroupsById(groupsArr)
        val idList = torrentGroupIdListArray(resp)
        val rows = mutableListOf<TwoLineRow>()
        val ids = mutableListOf<Int>()

        if (idList != null && idList.length() > 0) {
            for (i in 0 until idList.length()) {
                val key = idListElementToKey(idList, i) ?: continue
                val gid = key.toIntOrNull() ?: continue
                if (gid <= 0) continue
                val o = byId[key]
                if (o != null) {
                    val parsed = parseCollageTorrentGroupObject(o) ?: continue
                    rows.add(parsed.second)
                    ids.add(gid)
                } else {
                    rows.add(
                        TwoLineRow(
                            title = getString(R.string.redacted_browse_group_id_row_title, gid),
                            subtitle = "",
                            coverUrl = null,
                            showSeedingUtorrentIcon = false,
                        ),
                    )
                    ids.add(gid)
                }
            }
            return rows to ids
        }

        if (groupsArr != null) {
            for (i in 0 until groupsArr.length()) {
                val o = groupsArr.optJSONObject(i) ?: continue
                val parsed = parseCollageTorrentGroupObject(o) ?: continue
                rows.add(parsed.second)
                ids.add(parsed.first)
            }
        }
        return rows to ids
    }
}
