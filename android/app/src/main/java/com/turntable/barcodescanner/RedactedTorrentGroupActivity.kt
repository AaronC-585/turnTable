package com.turntable.barcodescanner

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.turntable.barcodescanner.databinding.ActivityRedactedTorrentGroupBinding
import com.turntable.barcodescanner.redacted.RedactedExtras
import com.turntable.barcodescanner.redacted.RedactedFormatters
import com.turntable.barcodescanner.redacted.RedactedGazelleEdition
import com.turntable.barcodescanner.redacted.RedactedGazelleTorrentParse
import com.turntable.barcodescanner.redacted.RedactedResult
import com.turntable.barcodescanner.redacted.RedactedTorrentGroupRowsAdapter
import com.turntable.barcodescanner.redacted.RedactedUiHelper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class RedactedTorrentGroupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRedactedTorrentGroupBinding
    private lateinit var api: com.turntable.barcodescanner.redacted.RedactedApiClient
    private var groupId: Int = 0
    private val torrentIds = mutableListOf<Int>()
    /** Parallel to [torrentIds] — full torrent JSON from [torrentgroup]. */
    private val torrentObjects = mutableListOf<JSONObject>()

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
        setupToolbarHome(binding.toolbar)

        val adapter = RedactedTorrentGroupRowsAdapter { torrentIdx -> showTorrentActions(torrentIdx) }
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

        binding.buttonRequestFormat.setOnClickListener {
            RedactedUiHelper.openSite(this, "requests.php?action=new&groupid=$groupId")
        }
        binding.buttonViewHistory.setOnClickListener {
            RedactedUiHelper.openSite(this, "torrents.php?action=history&groupid=$groupId")
        }
        binding.buttonGroupLog.setOnClickListener {
            RedactedUiHelper.openSite(this, "torrents.php?action=grouplog&groupid=$groupId")
        }
        binding.buttonAddToCollageFromGroup.setOnClickListener {
            startActivity(
                Intent(this, RedactedAddToCollageActivity::class.java)
                    .putExtra(RedactedExtras.GROUP_IDS_CSV, groupId.toString())
            )
        }
        binding.buttonCommentsOnSite.setOnClickListener {
            RedactedUiHelper.openSite(this, "torrents.php?id=$groupId#comments")
        }

        loadGroup(adapter)
    }

    private fun loadGroup(adapter: RedactedTorrentGroupRowsAdapter) {
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

    private fun bindGroup(resp: JSONObject?, adapter: RedactedTorrentGroupRowsAdapter) {
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

        val wikiBody = group.optString("wikiBody").trim()
        val wikiPlain = RedactedGazelleTorrentParse.stripBbCodeForPreview(wikiBody)
        if (wikiPlain.isNotBlank()) {
            binding.labelGroupInfo.visibility = View.VISIBLE
            binding.textWikiBody.visibility = View.VISIBLE
            binding.textWikiBody.text = wikiPlain
        } else {
            binding.labelGroupInfo.visibility = View.GONE
            binding.textWikiBody.visibility = View.GONE
            binding.textWikiBody.text = ""
        }

        val torrents: JSONArray? = resp.optJSONArray("torrents")
        val torrentList = buildList {
            if (torrents != null) {
                for (i in 0 until torrents.length()) {
                    torrents.optJSONObject(i)?.let { add(it) }
                }
            }
        }
        torrentIds.clear()
        torrentObjects.clear()
        val rows = mutableListOf<RedactedTorrentGroupRowsAdapter.Row>()
        val buckets = RedactedGazelleEdition.groupTorrentsByEdition(group, torrentList)
        for (bucket in buckets) {
            val header = RedactedGazelleEdition.buildEditionHeaderTitle(group, bucket.first())
            rows.add(RedactedTorrentGroupRowsAdapter.Row(title = header, subtitle = "", torrentIndex = null))
            for (t in bucket) {
                val tid = t.optInt("id")
                val line = buildTorrentTitleLine(t)
                val sub = buildTorrentSubtitle(t, tid)
                val idx = torrentIds.size
                torrentIds.add(tid)
                torrentObjects.add(t)
                rows.add(RedactedTorrentGroupRowsAdapter.Row(title = line, subtitle = sub, torrentIndex = idx))
            }
        }
        adapter.rows = rows
    }

    private fun buildTorrentTitleLine(t: JSONObject): String {
        val base = "${t.optString("format")} / ${t.optString("encoding")} / ${t.optString("media")}"
        val status = t.optString("userStatus").trim()
        return if (status.isNotBlank()) "$base — $status" else base
    }

    private fun buildTorrentSubtitle(t: JSONObject, tid: Int): String {
        val snatched = t.optInt("snatched")
        val parts = buildString {
            append(RedactedFormatters.bytes(t.optLong("size")))
            append(" · ↑").append(t.optInt("seeders"))
            append(" ↓").append(t.optInt("leechers"))
            append(" · ").append(getString(R.string.redacted_snatched_abbr, snatched))
            append(" · id ").append(tid)
        }
        val uid = t.optInt("userId")
        val uname = t.optString("username").trim()
        val time = t.optString("time").trim()
        val who = when {
            uname.isNotBlank() -> uname
            uid > 0 -> getString(R.string.redacted_user_id) + " $uid"
            else -> ""
        }
        return if (who.isNotBlank() && time.isNotBlank()) {
            parts + "\n" + getString(R.string.redacted_uploader_line, who, time)
        } else if (time.isNotBlank()) {
            parts + "\n" + time
        } else if (who.isNotBlank()) {
            parts + "\n" + who
        } else {
            parts
        }
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

    private fun showTorrentActions(position: Int) {
        val torrentId = torrentIds.getOrNull(position) ?: return
        val t = torrentObjects.getOrNull(position) ?: return
        val files = RedactedGazelleTorrentParse.parseFileList(t.optString("fileList"))
        val hasFiles = files.isNotEmpty()
        val descRaw = t.optString("description").trim()
        val hasDesc = descRaw.isNotBlank()

        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        labels.add(getString(R.string.redacted_download))
        actions.add { downloadTorrent(torrentId, false) }

        if (hasFiles) {
            labels.add(getString(R.string.redacted_file_list_title))
            actions.add { showFileListDialog(files) }
        }
        if (hasDesc) {
            labels.add(getString(R.string.redacted_release_description))
            actions.add {
                showScrollTextDialog(
                    getString(R.string.redacted_release_description),
                    RedactedGazelleTorrentParse.stripBbCodeForPreview(descRaw),
                )
            }
        }

        labels.add(getString(R.string.redacted_details))
        actions.add {
            startActivity(
                Intent(this, RedactedTorrentDetailActivity::class.java)
                    .putExtra(RedactedExtras.TORRENT_ID, torrentId)
            )
        }
        labels.add(getString(R.string.redacted_use_token))
        actions.add { confirmTokenDownload(torrentId) }
        labels.add(getString(R.string.redacted_edit_torrent_short))
        actions.add {
            startActivity(
                Intent(this, RedactedTorrentEditActivity::class.java)
                    .putExtra(RedactedExtras.TORRENT_ID, torrentId)
            )
        }

        AlertDialog.Builder(this)
            .setItems(labels.toTypedArray()) { _, which ->
                actions.getOrNull(which)?.invoke()
            }
            .show()
    }

    private fun showFileListDialog(files: List<RedactedGazelleTorrentParse.ListedFile>) {
        val body = buildString {
            for (f in files) {
                append(f.name)
                append(" — ")
                append(RedactedFormatters.bytes(f.sizeBytes))
                append('\n')
            }
        }.trimEnd()
        showScrollTextDialog(getString(R.string.redacted_file_list_title), body)
    }

    private fun showScrollTextDialog(title: CharSequence, body: CharSequence) {
        val padPx = (20 * resources.displayMetrics.density).toInt()
        val scroll = ScrollView(this)
        val tv = TextView(this).apply {
            text = body
            setTextIsSelectable(true)
            textSize = 13f
            setPadding(padPx, padPx, padPx, padPx)
            setTextColor(ContextCompat.getColor(this@RedactedTorrentGroupActivity, R.color.app_text_primary))
        }
        scroll.addView(tv)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
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

    private fun confirmTokenDownload(torrentId: Int) {
        AlertDialog.Builder(this)
            .setTitle(R.string.redacted_use_token_confirm_title)
            .setMessage(R.string.redacted_use_token_confirm_message)
            .setPositiveButton(android.R.string.ok) { _, _ -> downloadTorrent(torrentId, true) }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
