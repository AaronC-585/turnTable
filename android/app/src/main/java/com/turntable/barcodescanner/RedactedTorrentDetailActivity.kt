package com.turntable.barcodescanner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.turntable.barcodescanner.databinding.ActivityRedactedTorrentDetailBinding
import com.turntable.barcodescanner.redacted.RedactedExtras
import com.turntable.barcodescanner.redacted.RedactedFormatters
import com.turntable.barcodescanner.redacted.RedactedFreeleechTokens
import com.turntable.barcodescanner.redacted.RedactedGazelleTorrentParse
import com.turntable.barcodescanner.redacted.RedactedGazelleTorrentUser
import com.turntable.barcodescanner.redacted.RedactedResult
import com.turntable.barcodescanner.redacted.RedactedUiHelper
import org.json.JSONObject
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Single-torrent view: structured fields and collapsible sections (aligned with the album/group screen).
 */
class RedactedTorrentDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRedactedTorrentDetailBinding
    private lateinit var api: com.turntable.barcodescanner.redacted.RedactedApiClient
    private var torrentId: Int = 0
    private var groupId: Int = 0
    private var freeleechTokenCount: Int = 0

    private var sectionReleaseExpanded = true
    private var sectionTorrentExpanded = true
    private var sectionDescriptionExpanded = true
    private var sectionFilesExpanded = true
    private var sectionActionsExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = RedactedUiHelper.requireApi(this) ?: return
        api = c
        torrentId = intent.getIntExtra(RedactedExtras.TORRENT_ID, 0)
        if (torrentId <= 0) {
            Toast.makeText(this, R.string.redacted_invalid_id, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        binding = ActivityRedactedTorrentDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)
        supportActionBar?.title = getString(R.string.redacted_torrent_detail)

        wireCollapsibleSections()
        applySectionUi()

        binding.buttonOpenAlbumInApp.setOnClickListener {
            if (groupId <= 0) return@setOnClickListener
            startActivity(
                Intent(this, RedactedTorrentGroupActivity::class.java)
                    .putExtra(RedactedExtras.GROUP_ID, groupId),
            )
        }
        binding.buttonTorrentDownload.setOnClickListener { downloadTorrent(torrentId, false) }
        binding.buttonTorrentDownloadToken.setOnClickListener { confirmTokenDownload(torrentId) }
        binding.buttonTorrentQbt.setOnClickListener { sendTorrentToQbittorrent(torrentId, false) }
        binding.buttonTorrentQbtToken.setOnClickListener { confirmTokenSendToQbittorrent(torrentId) }
        binding.buttonTorrentEdit.setOnClickListener {
            startActivity(
                Intent(this, RedactedTorrentEditActivity::class.java)
                    .putExtra(RedactedExtras.TORRENT_ID, torrentId),
            )
        }
        binding.buttonTorrentOpenSite.setOnClickListener {
            RedactedUiHelper.openSite(this, "torrents.php?torrentid=$torrentId")
        }
        binding.buttonTorrentCopyPermalink.setOnClickListener {
            val url = "https://redacted.sh/torrents.php?torrentid=$torrentId"
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Permalink", url))
            Toast.makeText(this, R.string.redacted_permalink_copied, Toast.LENGTH_SHORT).show()
        }

        loadTorrent()
    }

    private fun syncCollapsible(content: View, chevron: TextView, expanded: Boolean) {
        content.visibility = if (expanded) View.VISIBLE else View.GONE
        chevron.rotation = if (expanded) 0f else -90f
    }

    private fun applySectionUi() {
        syncCollapsible(binding.contentSectionRelease, binding.chevronRelease, sectionReleaseExpanded)
        syncCollapsible(binding.contentSectionTorrent, binding.chevronTorrent, sectionTorrentExpanded)
        if (binding.sectionTorrentDescription.visibility == View.VISIBLE) {
            syncCollapsible(
                binding.contentSectionTorrentDescription,
                binding.chevronTorrentDescription,
                sectionDescriptionExpanded,
            )
        }
        if (binding.sectionTorrentFiles.visibility == View.VISIBLE) {
            syncCollapsible(
                binding.contentSectionTorrentFiles,
                binding.chevronTorrentFiles,
                sectionFilesExpanded,
            )
        }
        syncCollapsible(
            binding.contentSectionTorrentActions,
            binding.chevronTorrentActions,
            sectionActionsExpanded,
        )
    }

    private fun wireCollapsibleSections() {
        binding.headerSectionRelease.setOnClickListener {
            sectionReleaseExpanded = !sectionReleaseExpanded
            syncCollapsible(binding.contentSectionRelease, binding.chevronRelease, sectionReleaseExpanded)
        }
        binding.headerSectionTorrent.setOnClickListener {
            sectionTorrentExpanded = !sectionTorrentExpanded
            syncCollapsible(binding.contentSectionTorrent, binding.chevronTorrent, sectionTorrentExpanded)
        }
        binding.headerSectionTorrentDescription.setOnClickListener {
            sectionDescriptionExpanded = !sectionDescriptionExpanded
            syncCollapsible(
                binding.contentSectionTorrentDescription,
                binding.chevronTorrentDescription,
                sectionDescriptionExpanded,
            )
        }
        binding.headerSectionTorrentFiles.setOnClickListener {
            sectionFilesExpanded = !sectionFilesExpanded
            syncCollapsible(
                binding.contentSectionTorrentFiles,
                binding.chevronTorrentFiles,
                sectionFilesExpanded,
            )
        }
        binding.headerSectionTorrentActions.setOnClickListener {
            sectionActionsExpanded = !sectionActionsExpanded
            syncCollapsible(
                binding.contentSectionTorrentActions,
                binding.chevronTorrentActions,
                sectionActionsExpanded,
            )
        }
    }

    private fun loadTorrent() {
        binding.progressTorrentDetail.visibility = View.VISIBLE
        binding.textTorrentError.visibility = View.GONE
        Thread {
            val result = api.torrent(torrentId)
            val tokens = when (val idx = api.index()) {
                is RedactedResult.Success -> RedactedFreeleechTokens.countFromIndexRoot(idx.root)
                else -> 0
            }
            runOnUiThread {
                binding.progressTorrentDetail.visibility = View.GONE
                freeleechTokenCount = tokens
                when (result) {
                    is RedactedResult.Failure -> showError(result.message)
                    is RedactedResult.Success -> bindTorrent(result)
                    else -> showError(getString(R.string.redacted_unexpected))
                }
            }
        }.start()
    }

    private fun showError(message: String) {
        binding.textTorrentError.visibility = View.VISIBLE
        binding.textTorrentError.text = message
    }

    private fun bindTorrent(success: RedactedResult.Success) {
        val resp = success.response ?: success.root.optJSONObject("response") ?: success.root
        val group = resp.optJSONObject("group")
        val torrent = resp.optJSONObject("torrent")
        if (torrent == null) {
            showError(getString(R.string.redacted_unexpected))
            return
        }
        val tid = torrent.optInt("id", 0)
        if (tid > 0) torrentId = tid

        groupId = group?.optInt("id", 0) ?: 0
        val gname = group?.optString("name").orEmpty().ifBlank { "—" }
        val year = group?.optInt("year", 0) ?: 0
        binding.textReleaseTitle.text = if (year > 0) "$gname [$year]" else gname

        val artists = extractArtistNames(group?.optJSONObject("musicInfo"))
        if (artists.isNotEmpty()) {
            binding.textReleaseArtists.visibility = View.VISIBLE
            binding.textReleaseArtists.text = artists.joinToString(", ")
        } else {
            binding.textReleaseArtists.visibility = View.GONE
            binding.textReleaseArtists.text = ""
        }
        binding.buttonOpenAlbumInApp.visibility = if (groupId > 0) View.VISIBLE else View.GONE

        val seeding = RedactedGazelleTorrentUser.isUserSeedingTorrent(torrent)
        binding.imageTorrentSeedingAcorn.visibility = if (seeding) View.VISIBLE else View.GONE
        binding.textTorrentFormatLine.text = buildTorrentFormatLine(torrent, seeding)

        val size = RedactedFormatters.bytes(torrent.optLong("size"))
        val sn = torrent.optInt("snatched")
        val s = torrent.optInt("seeders")
        val l = torrent.optInt("leechers")
        binding.textTorrentStats.text = buildString {
            append(getString(R.string.redacted_torrent_detail_size_fmt, size))
            append("\n")
            append(getString(R.string.redacted_torrent_detail_peers_fmt, sn, s, l))
            val us = torrent.optString("userStatus").trim()
            if (us.isNotEmpty()) {
                append("\n")
                append(getString(R.string.redacted_torrent_detail_status_fmt, us))
            }
            formatTorrentTime(torrent)?.let { tStr ->
                append("\n")
                append(getString(R.string.redacted_torrent_detail_uploaded_fmt, tStr))
            }
        }

        val uploader = torrent.optJSONObject("user")
        val uName = when {
            uploader != null -> uploader.optString("username").ifBlank { uploader.optString("name") }
            else -> torrent.optString("username")
        }.trim()
        val uId = when {
            uploader != null -> uploader.optInt("id", 0)
            else -> torrent.optInt("userId", 0)
        }
        if (uName.isNotEmpty()) {
            binding.textTorrentUploader.visibility = View.VISIBLE
            binding.textTorrentUploader.text = if (uId > 0) {
                getString(R.string.redacted_uploader_line, uName, uId.toString())
            } else {
                getString(R.string.redacted_torrent_uploader_fmt, uName)
            }
        } else {
            binding.textTorrentUploader.visibility = View.GONE
        }

        val hash = torrent.optString("infoHash").trim()
        val tech = buildString {
            append(getString(R.string.redacted_torrent_id_line, torrentId))
            if (hash.isNotEmpty()) {
                append("\n")
                append(getString(R.string.redacted_torrent_hash_line, hash))
            }
        }
        if (tech.isNotBlank()) {
            binding.textTorrentTechnical.visibility = View.VISIBLE
            binding.textTorrentTechnical.text = tech
        } else {
            binding.textTorrentTechnical.visibility = View.GONE
        }

        val descRaw = torrent.optString("description").trim()
        val descPlain = RedactedGazelleTorrentParse.stripBbCodeForPreview(descRaw)
        if (descPlain.isNotBlank()) {
            binding.sectionTorrentDescription.visibility = View.VISIBLE
            binding.textTorrentDescription.text = descPlain
            sectionDescriptionExpanded = true
        } else {
            binding.sectionTorrentDescription.visibility = View.GONE
            binding.textTorrentDescription.text = ""
        }

        val files = RedactedGazelleTorrentParse.parseFileList(torrent.optString("fileList"))
        if (files.isNotEmpty()) {
            binding.sectionTorrentFiles.visibility = View.VISIBLE
            binding.textTorrentFileList.text = files.joinToString("\n") { f ->
                "${f.name} — ${RedactedFormatters.bytes(f.sizeBytes)}"
            }
            sectionFilesExpanded = true
        } else {
            binding.sectionTorrentFiles.visibility = View.GONE
            binding.textTorrentFileList.text = ""
        }

        val prefs = SearchPrefs(this)
        val qbt = prefs.isQbittorrentConfigured()
        binding.buttonTorrentDownloadToken.visibility =
            if (freeleechTokenCount > 0) View.VISIBLE else View.GONE
        binding.buttonTorrentQbt.visibility = if (qbt) View.VISIBLE else View.GONE
        binding.buttonTorrentQbtToken.visibility =
            if (qbt && freeleechTokenCount > 0) View.VISIBLE else View.GONE

        applySectionUi()
    }

    private fun formatTorrentTime(torrent: JSONObject): String? {
        val ts = torrent.optString("time").trim()
        if (ts.isNotEmpty() && ts.any { !it.isDigit() }) return ts
        val unix = ts.toLongOrNull() ?: torrent.optLong("time", 0L)
        if (unix <= 0L) return null
        val ms = if (unix < 1_000_000_000_000L) TimeUnit.SECONDS.toMillis(unix) else unix
        return android.text.format.DateFormat.getMediumDateFormat(this).format(Date(ms))
    }

    private fun extractArtistNames(musicInfo: JSONObject?): List<String> {
        val arr = musicInfo?.optJSONArray("artists") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val a = arr.optJSONObject(i) ?: continue
                val nm = a.optString("name").trim()
                if (nm.isNotEmpty()) add(nm)
            }
        }
    }

    private fun buildTorrentFormatLine(t: JSONObject, isUserSeeding: Boolean): String {
        val base = "${t.optString("format")} / ${t.optString("encoding")} / ${t.optString("media")}"
        val status = t.optString("userStatus").trim()
        if (status.isBlank()) return base
        if (isUserSeeding && RedactedGazelleTorrentUser.isUserSeeding(status)) return base
        return "$base — $status"
    }

    private fun confirmTokenDownload(torrentId: Int) {
        if (freeleechTokenCount <= 0) {
            Toast.makeText(this, R.string.redacted_no_fl_tokens, Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.redacted_use_token_confirm_title)
            .setMessage(R.string.redacted_use_token_confirm_message)
            .setPositiveButton(android.R.string.ok) { _, _ -> downloadTorrent(torrentId, true) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmTokenSendToQbittorrent(torrentId: Int) {
        if (freeleechTokenCount <= 0) {
            Toast.makeText(this, R.string.redacted_no_fl_tokens, Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.redacted_send_qbt_token_confirm_title)
            .setMessage(R.string.redacted_send_qbt_token_confirm_message)
            .setPositiveButton(android.R.string.ok) { _, _ -> sendTorrentToQbittorrent(torrentId, true) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun sendTorrentToQbittorrent(torrentId: Int, useToken: Boolean) {
        val prefs = SearchPrefs(this)
        val client = QbittorrentWebClient.fromPrefs(prefs)
        if (client == null) {
            Toast.makeText(this, R.string.qbt_not_configured, Toast.LENGTH_LONG).show()
            return
        }
        if (useToken && freeleechTokenCount <= 0) {
            Toast.makeText(this, R.string.redacted_no_fl_tokens, Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, R.string.qbt_sending, Toast.LENGTH_SHORT).show()
        Thread {
            when (val r = api.downloadTorrent(torrentId, useToken)) {
                is RedactedResult.Binary -> {
                    val result = client.addTorrentFile("redacted_$torrentId.torrent", r.bytes)
                    runOnUiThread {
                        result.fold(
                            onSuccess = {
                                Toast.makeText(this, R.string.qbt_sent, Toast.LENGTH_LONG).show()
                            },
                            onFailure = { e ->
                                Toast.makeText(
                                    this,
                                    getString(R.string.qbt_failed_fmt, e.message ?: e.javaClass.simpleName),
                                    Toast.LENGTH_LONG,
                                ).show()
                            },
                        )
                    }
                }
                is RedactedResult.Failure -> runOnUiThread {
                    Toast.makeText(this, r.message, Toast.LENGTH_LONG).show()
                }
                else -> runOnUiThread {
                    Toast.makeText(this, R.string.redacted_unexpected, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun downloadTorrent(torrentId: Int, useToken: Boolean) {
        if (useToken && freeleechTokenCount <= 0) {
            Toast.makeText(this, R.string.redacted_no_fl_tokens, Toast.LENGTH_LONG).show()
            return
        }
        Thread {
            val r = api.downloadTorrent(torrentId, useToken)
            runOnUiThread {
                when (r) {
                    is RedactedResult.Binary -> {
                        when (RedactedUiHelper.deliverDownloadedTorrent(this, torrentId, r.bytes)) {
                            RedactedUiHelper.TorrentDownloadOutcome.SavedToPreferredFolder ->
                                Toast.makeText(
                                    this,
                                    getString(R.string.redacted_torrent_saved_to_folder_fmt, torrentId),
                                    Toast.LENGTH_LONG,
                                ).show()
                            RedactedUiHelper.TorrentDownloadOutcome.Shared -> { }
                            RedactedUiHelper.TorrentDownloadOutcome.Failed ->
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
