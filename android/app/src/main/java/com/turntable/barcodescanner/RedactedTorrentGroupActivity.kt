package com.turntable.barcodescanner

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.turntable.barcodescanner.databinding.ActivityRedactedTorrentGroupBinding
import com.turntable.barcodescanner.redacted.RedactedExtras
import com.turntable.barcodescanner.redacted.RedactedFormatters
import com.turntable.barcodescanner.redacted.RedactedResult
import com.turntable.barcodescanner.redacted.RedactedUiHelper
import com.turntable.barcodescanner.redacted.TwoLineRow
import com.turntable.barcodescanner.redacted.TwoLineRowsAdapter
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class RedactedTorrentGroupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRedactedTorrentGroupBinding
    private lateinit var api: com.turntable.barcodescanner.redacted.RedactedApiClient
    private var groupId: Int = 0
    private val torrentIds = mutableListOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = RedactedUiHelper.requireApi(this) ?: return
        api = c
        groupId = intent.getIntExtra(RedactedExtras.GROUP_ID, 0)
        if (groupId <= 0) {
            Toast.makeText(this, R.string.redacted_invalid_group, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        binding = ActivityRedactedTorrentGroupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val adapter = TwoLineRowsAdapter { pos ->
            val tid = torrentIds.getOrNull(pos) ?: return@TwoLineRowsAdapter
            showTorrentActions(tid)
        }
        binding.recyclerTorrents.layoutManager = LinearLayoutManager(this)
        binding.recyclerTorrents.adapter = adapter

        binding.buttonOpenSite.setOnClickListener {
            RedactedUiHelper.openSite(this, "torrents.php?id=$groupId")
        }
        binding.buttonAddTag.setOnClickListener { promptAddTag() }
        binding.buttonEditGroup.setOnClickListener {
            startActivity(
                Intent(this, RedactedGroupEditActivity::class.java)
                    .putExtra(RedactedExtras.GROUP_ID, groupId)
            )
        }

        loadGroup(adapter)
    }

    private fun loadGroup(adapter: TwoLineRowsAdapter) {
        binding.progress.visibility = View.VISIBLE
        Thread {
            val result = api.torrentGroup(groupId)
            runOnUiThread {
                binding.progress.visibility = View.GONE
                when (result) {
                    is RedactedResult.Failure -> Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                    is RedactedResult.Success -> bindGroup(result.response, adapter)
                    else -> {}
                }
            }
        }.start()
    }

    private fun bindGroup(resp: JSONObject?, adapter: TwoLineRowsAdapter) {
        if (resp == null) return
        val group = resp.optJSONObject("group") ?: return
        val name = group.optString("name")
        val year = group.optInt("year", 0)
        supportActionBar?.title = name.ifBlank { getString(R.string.redacted_torrents) }
        val wikiImage = group.optString("wikiImage").takeIf { it.startsWith("http") }
        if (!wikiImage.isNullOrBlank()) {
            loadCoverImage(wikiImage)
        } else {
            binding.imageCover.visibility = View.GONE
        }
        val artists = extractArtists(group.optJSONObject("musicInfo"))
        val meta = buildString {
            append(name)
            if (year > 0) append(" (").append(year).append(")")
            append("\n")
            append(getString(R.string.redacted_group_id_fmt, group.optInt("id")))
            append("\n")
            append(artists)
            append("\n")
            append(group.optString("recordLabel"))
            if (group.optString("catalogueNumber").isNotBlank()) {
                append(" · ").append(group.optString("catalogueNumber"))
            }
        }
        binding.textMeta.text = meta

        val torrents: JSONArray? = resp.optJSONArray("torrents")
        val rows = mutableListOf<TwoLineRow>()
        torrentIds.clear()
        if (torrents != null) {
            for (i in 0 until torrents.length()) {
                val t = torrents.optJSONObject(i) ?: continue
                val tid = t.optInt("id")
                val line = "${t.optString("format")} / ${t.optString("encoding")} / ${t.optString("media")}"
                val sub = "${RedactedFormatters.bytes(t.optLong("size"))} · ↑${t.optInt("seeders")} ↓${t.optInt("leechers")} · id $tid"
                rows.add(TwoLineRow(line, sub))
                torrentIds.add(tid)
            }
        }
        adapter.rows = rows
    }

    private fun extractArtists(musicInfo: JSONObject?): String {
        if (musicInfo == null) return ""
        val arr = musicInfo.optJSONArray("artists") ?: return ""
        return buildString {
            for (i in 0 until arr.length()) {
                val a = arr.optJSONObject(i) ?: continue
                if (isNotEmpty()) append(", ")
                append(a.optString("name"))
            }
        }
    }

    private fun promptAddTag() {
        val input = EditText(this)
        input.hint = getString(R.string.redacted_tag_prompt)
        AlertDialog.Builder(this)
            .setTitle(R.string.redacted_add_tag)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val tags = input.text?.toString()?.trim().orEmpty()
                if (tags.isBlank()) return@setPositiveButton
                Thread {
                    val r = api.addTag(groupId, tags)
                    runOnUiThread {
                        when (r) {
                            is RedactedResult.Failure -> Toast.makeText(this, r.message, Toast.LENGTH_LONG).show()
                            is RedactedResult.Success -> Toast.makeText(this, R.string.redacted_ok, Toast.LENGTH_SHORT).show()
                            else -> {}
                        }
                    }
                }.start()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showTorrentActions(torrentId: Int) {
        val items = arrayOf(
            getString(R.string.redacted_download),
            getString(R.string.redacted_details),
            getString(R.string.redacted_use_token),
            getString(R.string.redacted_edit_torrent_short),
        )
        AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> downloadTorrent(torrentId, false)
                    1 -> startActivity(
                        Intent(this, RedactedTorrentDetailActivity::class.java)
                            .putExtra(RedactedExtras.TORRENT_ID, torrentId)
                    )
                    2 -> downloadTorrent(torrentId, true)
                    3 -> startActivity(
                        Intent(this, RedactedTorrentEditActivity::class.java)
                            .putExtra(RedactedExtras.TORRENT_ID, torrentId)
                    )
                }
            }
            .show()
    }

    private fun loadCoverImage(imageUrl: String) {
        Thread {
            var ok = false
            try {
                val conn = URL(imageUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", "turnTable/1.0")
                conn.inputStream.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        runOnUiThread {
                            binding.imageCover.setImageBitmap(bmp)
                            binding.imageCover.visibility = View.VISIBLE
                        }
                        ok = true
                    }
                }
            } catch (_: Exception) { /* ignore */ }
            if (!ok) {
                runOnUiThread { binding.imageCover.visibility = View.GONE }
            }
        }.start()
    }

    private fun downloadTorrent(torrentId: Int, useToken: Boolean) {
        Thread {
            val r = api.downloadTorrent(torrentId, useToken)
            runOnUiThread {
                when (r) {
                    is RedactedResult.Binary -> {
                        if (!RedactedUiHelper.shareTorrentFile(this, torrentId, r.bytes)) {
                            Toast.makeText(this, R.string.redacted_could_not_share, Toast.LENGTH_SHORT).show()
                        }
                    }
                    is RedactedResult.Failure -> Toast.makeText(this, r.message, Toast.LENGTH_LONG).show()
                    else -> Toast.makeText(this, R.string.redacted_unexpected, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
