package com.turntable.barcodescanner

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.turntable.barcodescanner.databinding.ActivityRedactedBookmarksBinding
import com.turntable.barcodescanner.databinding.ActivityRedactedSimpleListBinding
import com.turntable.barcodescanner.databinding.ActivityRedactedTop10Binding
import com.turntable.barcodescanner.databinding.ActivityRedactedUserTorrentsBinding
import com.turntable.barcodescanner.redacted.RedactedExtras
import com.turntable.barcodescanner.redacted.RedactedGazelleTorrentUser
import com.turntable.barcodescanner.redacted.RedactedResult
import com.turntable.barcodescanner.redacted.RedactedUiHelper
import com.turntable.barcodescanner.redacted.TwoLineRow
import com.turntable.barcodescanner.redacted.TwoLineRowsAdapter
import org.json.JSONArray
import org.json.JSONObject

class RedactedTop10Activity : AppCompatActivity() {
    private lateinit var binding: ActivityRedactedTop10Binding
    private lateinit var api: com.turntable.barcodescanner.redacted.RedactedApiClient

    private val top10Types = listOf("torrents", "tags", "users")
    private val top10Limits = listOf("10", "100", "250")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = RedactedUiHelper.requireApi(this) ?: return
        api = c
        binding = ActivityRedactedTop10Binding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)

        ExpandableBulletChoice.bindLabelList(
            binding.expandTop10Type,
            getString(R.string.redacted_top10_type_header),
            top10Types,
            0,
        )
        ExpandableBulletChoice.bindLabelList(
            binding.expandTop10Limit,
            getString(R.string.redacted_top10_limit_header),
            top10Limits,
            0,
        )

        val adapter = TwoLineRowsAdapter { /* read-only list */ }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.buttonLoad.setOnClickListener { loadTop10(adapter) }
    }

    private fun loadTop10(adapter: TwoLineRowsAdapter) {
        val type = top10Types[
            ListViewSingleChoice.selectedIndex(binding.expandTop10Type.listExpandChoices).coerceIn(top10Types.indices),
        ]
        val limit = top10Limits[
            ListViewSingleChoice.selectedIndex(binding.expandTop10Limit.listExpandChoices).coerceIn(top10Limits.indices),
        ].toIntOrNull() ?: 10
        binding.progress.visibility = View.VISIBLE
        Thread {
            val r = api.top10(type, limit)
            runOnUiThread {
                binding.progress.visibility = View.GONE
                when (r) {
                    is RedactedResult.Failure -> Toast.makeText(this, r.message, Toast.LENGTH_LONG).show()
                    is RedactedResult.Success -> adapter.rows = parseTop10(r.responseArray)
                    else -> {}
                }
            }
        }.start()
    }

    private fun parseTop10(arr: JSONArray?): List<TwoLineRow> {
        if (arr == null) return emptyList()
        val out = mutableListOf<TwoLineRow>()
        for (i in 0 until arr.length()) {
            val block = arr.optJSONObject(i) ?: continue
            val cap = block.optString("caption")
            if (cap.isNotBlank()) out.add(TwoLineRow("— $cap —", ""))
            val res = block.optJSONArray("results") ?: continue
            for (j in 0 until res.length()) {
                val o = res.optJSONObject(j) ?: continue
                out.add(top10Row(o))
            }
        }
        return out
    }

    private fun top10Row(o: JSONObject): TwoLineRow {
        return when {
            o.has("groupName") -> {
                val yr = when {
                    o.optInt("year") > 0 -> o.optInt("year")
                    o.optInt("groupYear") > 0 -> o.optInt("groupYear")
                    else -> 0
                }
                val sn = o.optInt("snatched", -1)
                val sub = buildString {
                    if (yr > 0) append(yr)
                    if (sn >= 0) {
                        if (isNotEmpty()) append(" · ")
                        append(sn).append(" snatches")
                    }
                }
                TwoLineRow(
                    "${o.optString("artist")} — ${o.optString("groupName")}",
                    sub,
                    showSeedingUtorrentIcon = RedactedGazelleTorrentUser.jsonIndicatesUserSeeding(o),
                )
            }
            o.has("username") -> TwoLineRow(
                o.optString("username"),
                o.optString("class"),
            )
            o.has("name") && o.has("tagId") -> TwoLineRow(
                o.optString("name"),
                "",
            )
            else -> TwoLineRow(o.toString().take(120), "")
        }
    }
}

class RedactedBookmarksActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRedactedBookmarksBinding
    private lateinit var api: com.turntable.barcodescanner.redacted.RedactedApiClient
    private val groupIds = mutableListOf<Int>()
    private val artistIds = mutableListOf<Int>()
    private val bookmarkTypes = listOf("torrents", "artists")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = RedactedUiHelper.requireApi(this) ?: return
        api = c
        binding = ActivityRedactedBookmarksBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)

        ExpandableBulletChoice.bindLabelList(
            binding.expandBookmarkType,
            getString(R.string.redacted_bookmarks_type_header),
            bookmarkTypes,
            0,
        )

        val adapter = TwoLineRowsAdapter { pos ->
            when (ListViewSingleChoice.selectedIndex(binding.expandBookmarkType.listExpandChoices)) {
                0 -> {
                    val gid = groupIds.getOrNull(pos) ?: return@TwoLineRowsAdapter
                    startActivity(
                        Intent(this, RedactedTorrentGroupActivity::class.java)
                            .putExtra(RedactedExtras.GROUP_ID, gid),
                    )
                }
                1 -> {
                    val aid = artistIds.getOrNull(pos) ?: return@TwoLineRowsAdapter
                    startActivity(
                        Intent(this, RedactedArtistActivity::class.java)
                            .putExtra(RedactedExtras.ARTIST_ID, aid),
                    )
                }
            }
        }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.buttonLoad.setOnClickListener { loadBookmarks(adapter) }
    }

    private fun loadBookmarks(adapter: TwoLineRowsAdapter) {
        val type = bookmarkTypes[
            ListViewSingleChoice.selectedIndex(binding.expandBookmarkType.listExpandChoices).coerceIn(bookmarkTypes.indices),
        ]
        binding.progress.visibility = View.VISIBLE
        Thread {
            val r = api.bookmarks(type)
            runOnUiThread {
                binding.progress.visibility = View.GONE
                when (r) {
                    is RedactedResult.Failure -> Toast.makeText(this, r.message, Toast.LENGTH_LONG).show()
                    is RedactedResult.Success -> {
                        val resp = r.response ?: return@runOnUiThread
                        val rows = mutableListOf<TwoLineRow>()
                        groupIds.clear()
                        artistIds.clear()
                        if (type == "torrents") {
                            val bm = resp.optJSONArray("bookmarks")
                            if (bm != null) {
                                for (i in 0 until bm.length()) {
                                    val o = bm.optJSONObject(i) ?: continue
                                    val gid = o.optInt("id")
                                    rows.add(
                                        TwoLineRow(
                                            o.optString("name"),
                                            "${o.optInt("year")} · $gid",
                                            showSeedingUtorrentIcon = RedactedGazelleTorrentUser.jsonIndicatesUserSeeding(o),
                                        ),
                                    )
                                    groupIds.add(gid)
                                }
                            }
                        } else {
                            val ar = resp.optJSONArray("artists")
                            if (ar != null) {
                                for (i in 0 until ar.length()) {
                                    val o = ar.optJSONObject(i) ?: continue
                                    val aid = o.optInt("artistId")
                                    rows.add(TwoLineRow(o.optString("artistName"), ""))
                                    artistIds.add(aid)
                                }
                            }
                        }
                        adapter.rows = rows
                    }
                    else -> {}
                }
            }
        }.start()
    }
}

class RedactedRequestsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRedactedSimpleListBinding
    private lateinit var api: com.turntable.barcodescanner.redacted.RedactedApiClient
    private var currentPage = 1
    private var totalPages = 1
    private val requestIds = mutableListOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = RedactedUiHelper.requireApi(this) ?: return
        api = c
        binding = ActivityRedactedSimpleListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.redacted_requests)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)
        binding.inputQueryLayout.hint = getString(R.string.redacted_requests_search_hint)

        val adapter = TwoLineRowsAdapter { pos ->
            val rid = requestIds.getOrNull(pos) ?: return@TwoLineRowsAdapter
            startActivity(
                Intent(this, RedactedRequestDetailActivity::class.java)
                    .putExtra(RedactedExtras.REQUEST_ID, rid),
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
    }

    private fun load(adapter: TwoLineRowsAdapter) {
        val q = binding.editQuery.text?.toString()?.trim().orEmpty()
        binding.progress.visibility = View.VISIBLE
        Thread {
            val r = api.requests(search = q.takeIf { it.isNotBlank() }, page = currentPage)
            runOnUiThread {
                binding.progress.visibility = View.GONE
                when (r) {
                    is RedactedResult.Failure -> Toast.makeText(this, r.message, Toast.LENGTH_LONG).show()
                    is RedactedResult.Success -> {
                        val resp = r.response ?: return@runOnUiThread
                        totalPages = resp.optInt("pages", 1).coerceAtLeast(1)
                        currentPage = resp.optInt("currentPage", currentPage).coerceAtLeast(1)
                        binding.textPage.text = getString(R.string.redacted_page_fmt, currentPage, totalPages)
                        val arr = resp.optJSONArray("results")
                        val rows = mutableListOf<TwoLineRow>()
                        requestIds.clear()
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val o = arr.optJSONObject(i) ?: continue
                                val rid = o.optInt("requestId")
                                rows.add(
                                    TwoLineRow(
                                        o.optString("title"),
                                        "${o.optInt("year")} · votes ${o.optInt("voteCount")}",
                                    ),
                                )
                                requestIds.add(rid)
                            }
                        }
                        adapter.rows = rows
                    }
                    else -> {}
                }
            }
        }.start()
    }
}

class RedactedUserTorrentsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRedactedUserTorrentsBinding
    private lateinit var api: com.turntable.barcodescanner.redacted.RedactedApiClient
    private val groupIds = mutableListOf<Int>()
    private val userTorrentTypes = listOf("seeding", "leeching", "uploaded", "snatched")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = RedactedUiHelper.requireApi(this) ?: return
        api = c
        binding = ActivityRedactedUserTorrentsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)

        ExpandableBulletChoice.bindLabelList(
            binding.expandUserTorrentType,
            getString(R.string.redacted_user_torrents_type_header),
            userTorrentTypes,
            0,
        )

        val adapter = TwoLineRowsAdapter { pos ->
            val gid = groupIds.getOrNull(pos) ?: return@TwoLineRowsAdapter
            startActivity(
                Intent(this, RedactedTorrentGroupActivity::class.java)
                    .putExtra(RedactedExtras.GROUP_ID, gid),
            )
        }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.buttonLoad.setOnClickListener { load(adapter) }

        Thread {
            val idx = api.index()
            val uid = (idx as? RedactedResult.Success)?.response?.optInt("id") ?: 0
            runOnUiThread {
                if (uid > 0) binding.editUserId.setText(uid.toString())
            }
        }.start()
    }

    private fun load(adapter: TwoLineRowsAdapter) {
        val uid = binding.editUserId.text?.toString()?.toIntOrNull() ?: 0
        if (uid <= 0) {
            Toast.makeText(this, R.string.redacted_invalid_id, Toast.LENGTH_SHORT).show()
            return
        }
        val type = userTorrentTypes[
            ListViewSingleChoice.selectedIndex(binding.expandUserTorrentType.listExpandChoices).coerceIn(userTorrentTypes.indices),
        ]
        binding.progress.visibility = View.VISIBLE
        Thread {
            val r = api.userTorrents(uid, type, limit = 100)
            runOnUiThread {
                binding.progress.visibility = View.GONE
                when (r) {
                    is RedactedResult.Failure -> Toast.makeText(this, r.message, Toast.LENGTH_LONG).show()
                    is RedactedResult.Success -> {
                        val resp = r.response ?: return@runOnUiThread
                        val arr = resp.optJSONArray(type) ?: JSONArray()
                        val rows = mutableListOf<TwoLineRow>()
                        groupIds.clear()
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i) ?: continue
                            val gid = o.optString("groupId").toIntOrNull() ?: o.optInt("groupId")
                            val name = o.optString("name").ifBlank { o.optString("groupName") }
                            val artist = o.optString("artistName").ifBlank { o.optString("artist") }
                            rows.add(
                                TwoLineRow(
                                    title = name,
                                    subtitle = artist,
                                    showSeedingUtorrentIcon = RedactedGazelleTorrentUser.jsonIndicatesUserSeeding(o),
                                ),
                            )
                            if (gid > 0) groupIds.add(gid)
                        }
                        adapter.rows = rows
                    }
                    else -> {}
                }
            }
        }.start()
    }
}
