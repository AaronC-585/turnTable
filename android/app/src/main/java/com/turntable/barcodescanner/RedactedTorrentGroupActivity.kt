package com.turntable.barcodescanner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.turntable.barcodescanner.databinding.ActivityRedactedTorrentGroupBinding
import com.turntable.barcodescanner.SearchPrefs
import com.turntable.barcodescanner.debug.AppEventLog
import com.turntable.barcodescanner.debug.OutgoingUrlLog
import com.turntable.barcodescanner.redacted.RedactedAvatarLoader
import com.turntable.barcodescanner.redacted.RedactedExtras
import com.turntable.barcodescanner.redacted.RedactedFormatters
import com.turntable.barcodescanner.redacted.RedactedFreeleechTokens
import com.turntable.barcodescanner.redacted.RedactedGazelleEdition
import com.turntable.barcodescanner.redacted.RedactedGazelleTorrentParse
import com.turntable.barcodescanner.redacted.RedactedGazelleTorrentUser
import com.turntable.barcodescanner.redacted.RedactedResult
import com.turntable.barcodescanner.redacted.RedactedTorrentGroupRowsAdapter
import com.turntable.barcodescanner.redacted.RedactedUiHelper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicInteger

private data class AlbumCoverEntry(val url: String, val label: String)

/** One credited artist from [group.musicInfo.artists]; [id] null if API omitted it. */
private data class ArtistRef(val id: Int?, val name: String)

class RedactedTorrentGroupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRedactedTorrentGroupBinding
    private lateinit var api: com.turntable.barcodescanner.redacted.RedactedApiClient
    private var groupId: Int = 0
    /** Bumped before each load so stale [loadCoverImage] threads skip UI updates. */
    private val albumLoadGeneration = AtomicInteger(0)
    /** Covers for this group (wiki + alternates from API). */
    private var albumCoverEntries: List<AlbumCoverEntry> = emptyList()
    private val torrentIds = mutableListOf<Int>()
    /** Parallel to [torrentIds] — full torrent JSON from [torrentgroup]. */
    private val torrentObjects = mutableListOf<JSONObject>()
    /** From [index] `userstats` while loading this group; FL token UI only if &gt; 0. */
    private var freeleechTokenCount: Int = 0

    /** Collapsible sections (Redacted-style boxes). */
    private var sectionAlbumExpanded = true
    private var sectionTorrentsExpanded = true
    private var sectionWikiExpanded = true
    private var sectionActionsExpanded = false

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

        val adapter = RedactedTorrentGroupRowsAdapter(
            onTorrentClick = { showTorrentActions(it) },
            onEditionDoubleTap = { showEditionDownloadMenu(it) },
        )
        binding.recyclerTorrents.layoutManager = LinearLayoutManager(this)
        binding.recyclerTorrents.adapter = adapter
        binding.textAlbumHeader.movementMethod = LinkMovementMethod.getInstance()

        wireTorrentGroupCollapsibleSections()
        applyTorrentGroupSectionUi()

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

        applyAlbumThumbSizePx()
        setupAlbumThumbDoubleTap()
        loadGroup(adapter)
    }

    private fun syncCollapsibleSection(content: View, chevron: TextView, expanded: Boolean) {
        content.visibility = if (expanded) View.VISIBLE else View.GONE
        chevron.rotation = if (expanded) 0f else -90f
    }

    private fun applyTorrentGroupSectionUi() {
        syncCollapsibleSection(
            binding.contentSectionAlbum,
            binding.chevronAlbum,
            sectionAlbumExpanded,
        )
        syncCollapsibleSection(
            binding.contentSectionTorrents,
            binding.chevronTorrents,
            sectionTorrentsExpanded,
        )
        if (binding.sectionWiki.visibility == View.VISIBLE) {
            syncCollapsibleSection(
                binding.contentSectionWiki,
                binding.chevronWiki,
                sectionWikiExpanded,
            )
        }
        syncCollapsibleSection(
            binding.contentSectionActions,
            binding.chevronActions,
            sectionActionsExpanded,
        )
    }

    private fun wireTorrentGroupCollapsibleSections() {
        binding.headerSectionAlbum.setOnClickListener {
            sectionAlbumExpanded = !sectionAlbumExpanded
            syncCollapsibleSection(
                binding.contentSectionAlbum,
                binding.chevronAlbum,
                sectionAlbumExpanded,
            )
        }
        binding.headerSectionTorrents.setOnClickListener {
            sectionTorrentsExpanded = !sectionTorrentsExpanded
            syncCollapsibleSection(
                binding.contentSectionTorrents,
                binding.chevronTorrents,
                sectionTorrentsExpanded,
            )
        }
        binding.headerSectionWiki.setOnClickListener {
            sectionWikiExpanded = !sectionWikiExpanded
            syncCollapsibleSection(
                binding.contentSectionWiki,
                binding.chevronWiki,
                sectionWikiExpanded,
            )
        }
        binding.headerSectionActions.setOnClickListener {
            sectionActionsExpanded = !sectionActionsExpanded
            syncCollapsibleSection(
                binding.contentSectionActions,
                binding.chevronActions,
                sectionActionsExpanded,
            )
        }
    }

    private fun setupAlbumThumbDoubleTap() {
        binding.imageAlbumThumb.isClickable = true
        val detector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    showCoverGalleryDialog()
                    return true
                }
            },
        )
        binding.imageAlbumThumb.setOnTouchListener { _, ev -> detector.onTouchEvent(ev) }
    }

    /** Physical ~1in square for header cover (decode + view). */
    private fun applyAlbumThumbSizePx() {
        val side = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_IN,
            1f,
            resources.displayMetrics,
        ).toInt().coerceAtLeast(1)
        val lp = binding.imageAlbumThumb.layoutParams
        lp.width = side
        lp.height = side
        binding.imageAlbumThumb.layoutParams = lp
    }

    /**
     * Drop large in-memory state before fetching/rendering album data (cover bitmap, wiki text,
     * torrent JSON mirrors, list rows) and hint GC so the new page has headroom.
     */
    private fun prepareAlbumPageMemory(adapter: RedactedTorrentGroupRowsAdapter) {
        albumLoadGeneration.incrementAndGet()
        val iv = binding.imageAlbumThumb
        val prev = iv.drawable
        iv.setImageDrawable(null)
        if (prev is BitmapDrawable) {
            val bmp = prev.bitmap
            if (bmp != null && !bmp.isRecycled) {
                bmp.recycle()
            }
        }
        iv.visibility = View.GONE
        binding.textAlbumHeader.text = ""
        binding.textMeta.text = ""
        binding.textWikiBody.text = ""
        binding.sectionWiki.visibility = View.GONE
        sectionAlbumExpanded = true
        sectionTorrentsExpanded = true
        sectionWikiExpanded = true
        sectionActionsExpanded = false
        torrentIds.clear()
        torrentObjects.clear()
        adapter.rows = emptyList()
        albumCoverEntries = emptyList()
        binding.recyclerTorrents.recycledViewPool.clear()
        applyTorrentGroupSectionUi()
        Thread {
            Runtime.getRuntime().runFinalization()
            System.gc()
        }.start()
    }

    private fun loadGroup(adapter: RedactedTorrentGroupRowsAdapter) {
        prepareAlbumPageMemory(adapter)
        freeleechTokenCount = 0
        binding.progress.visibility = View.VISIBLE
        Thread {
            val result = api.torrentGroup(groupId)
            val tokens = when (val idx = api.index()) {
                is RedactedResult.Success -> RedactedFreeleechTokens.countFromIndexRoot(idx.root)
                else -> 0
            }
            runOnUiThread {
                binding.progress.visibility = View.GONE
                when (result) {
                    is RedactedResult.Failure -> {
                        freeleechTokenCount = 0
                        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                    }
                    is RedactedResult.Success -> {
                        freeleechTokenCount = tokens
                        bindGroup(result.response, adapter)
                    }
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
        AppEventLog.log(
            AppEventLog.Category.REDACTED,
            "torrent group loaded \"${name.ifBlank { "—" }}\"${if (year > 0) " ($year)" else ""}",
        )
        supportActionBar?.title = getString(R.string.redacted_album_toolbar_title)
        albumCoverEntries = extractAlbumCoverEntries(group)
        if (albumCoverEntries.isNotEmpty()) {
            loadAlbumThumb(albumCoverEntries.first().url)
        } else {
            binding.imageAlbumThumb.visibility = View.GONE
        }
        val artistRefs = extractArtistRefs(group.optJSONObject("musicInfo"))
        val artistsPlain = artistRefs.joinToString(", ") { it.name }.ifBlank { "—" }
        val gidForHistory = group.optInt("id")
        if (gidForHistory > 0) {
            val historySub = buildString {
                append(artistsPlain)
                if (year > 0) append(" · ").append(year)
            }
            RedactedGroupHistoryStore.add(
                this,
                groupId = gidForHistory,
                groupName = name.ifBlank { "—" },
                subtitle = historySub,
                coverUrl = albumCoverEntries.firstOrNull()?.url,
            )
        }
        val catLine = buildString {
            if (year > 0) append(year)
            val cat = group.optString("catalogueNumber").trim()
            val label = group.optString("recordLabel").trim()
            if (isNotEmpty() && (cat.isNotEmpty() || label.isNotEmpty())) append(" · ")
            when {
                cat.isNotEmpty() && label.isNotEmpty() -> append(label).append(" · ").append(cat)
                cat.isNotEmpty() -> append(cat)
                label.isNotEmpty() -> append(label)
            }
        }
        binding.textAlbumHeader.text = buildAlbumHeaderSpannable(artistRefs, name, catLine)
        binding.textMeta.text = ""
        binding.textMeta.visibility = View.GONE

        val wikiBody = group.optString("wikiBody").trim()
        val wikiPlain = RedactedGazelleTorrentParse.stripBbCodeForPreview(wikiBody)
        if (wikiPlain.isNotBlank()) {
            binding.sectionWiki.visibility = View.VISIBLE
            binding.textWikiBody.text = wikiPlain
            sectionWikiExpanded = true
        } else {
            binding.sectionWiki.visibility = View.GONE
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
            if (bucket.isEmpty()) continue
            val header = RedactedGazelleEdition.buildEditionHeaderTitle(group, bucket.first())
            val bucketIndices = mutableListOf<Int>()
            for (t in bucket) {
                bucketIndices.add(torrentIds.size)
                torrentIds.add(t.optInt("id"))
                torrentObjects.add(t)
            }
            val editionAnchor = rows.size
            rows.add(
                RedactedTorrentGroupRowsAdapter.Row.Edition(
                    title = header,
                    bucketTorrentIndices = bucketIndices.toList(),
                    editionAnchorIndex = editionAnchor,
                ),
            )
            for ((i, t) in bucket.withIndex()) {
                val listIndex = bucketIndices[i]
                val seeding = RedactedGazelleTorrentUser.isUserSeedingTorrent(t)
                rows.add(
                    RedactedTorrentGroupRowsAdapter.Row.Torrent(
                        formatLine = buildTorrentTitleLine(t, seeding),
                        sizeText = RedactedFormatters.bytes(t.optLong("size")),
                        snatched = t.optInt("snatched"),
                        seeders = t.optInt("seeders"),
                        leechers = t.optInt("leechers"),
                        listIndex = listIndex,
                        isUserSeeding = seeding,
                    ),
                )
            }
        }
        adapter.rows = rows
        applyTorrentGroupSectionUi()
    }

    private fun buildTorrentTitleLine(t: JSONObject, isUserSeeding: Boolean = false): String {
        val base = "${t.optString("format")} / ${t.optString("encoding")} / ${t.optString("media")}"
        val status = t.optString("userStatus").trim()
        if (status.isBlank()) return base
        // Acorn in the row denotes seeding; avoid duplicating “Seeding” in the title line.
        if (isUserSeeding && RedactedGazelleTorrentUser.isUserSeeding(status)) return base
        return "$base — $status"
    }

    private fun extractArtistRefs(musicInfo: JSONObject?): List<ArtistRef> {
        val arr = musicInfo?.optJSONArray("artists") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val a = arr.optJSONObject(i) ?: continue
                val nm = a.optString("name").trim()
                if (nm.isBlank()) continue
                add(ArtistRef(parseArtistIdFromJson(a), nm))
            }
        }
    }

    /**
     * Gazelle torrent group `musicInfo.artists[]` usually has numeric `id`; some payloads use `artistId`.
     */
    private fun parseArtistIdFromJson(o: JSONObject): Int? {
        fun fromKey(key: String): Int? {
            if (!o.has(key)) return null
            val s = o.optString(key, "").trim()
            if (s.isNotEmpty()) return s.toIntOrNull()?.takeIf { it > 0 }
            val n = o.optInt(key, 0)
            return n.takeIf { it > 0 }
        }
        return fromKey("id") ?: fromKey("artistId")
    }

    private fun buildAlbumHeaderSpannable(
        artists: List<ArtistRef>,
        albumName: String,
        catLine: String,
    ): CharSequence {
        val sb = SpannableStringBuilder()
        if (artists.isEmpty()) {
            sb.append("—")
        } else {
            val linkColor = ContextCompat.getColor(this, R.color.app_accent)
            for ((i, ref) in artists.withIndex()) {
                if (i > 0) sb.append(", ")
                val start = sb.length
                sb.append(ref.name)
                val end = sb.length
                val aid = ref.id
                if (aid != null) {
                    sb.setSpan(
                        object : ClickableSpan() {
                            override fun onClick(widget: View) {
                                startActivity(
                                    Intent(this@RedactedTorrentGroupActivity, RedactedArtistActivity::class.java)
                                        .putExtra(RedactedExtras.ARTIST_ID, aid),
                                )
                            }

                            override fun updateDrawState(ds: TextPaint) {
                                super.updateDrawState(ds)
                                ds.isUnderlineText = true
                                ds.color = linkColor
                            }
                        },
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            }
        }
        sb.append("\n").append(albumName.ifBlank { "—" })
        sb.append("\n").append(catLine.ifBlank { "—" })
        return sb
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

        if (SearchPrefs(this).isQbittorrentConfigured()) {
            labels.add(getString(R.string.redacted_send_qbittorrent))
            actions.add { sendTorrentToQbittorrent(torrentId, false) }
            if (freeleechTokenCount > 0) {
                labels.add(getString(R.string.redacted_send_qbittorrent_with_token))
                actions.add { confirmTokenSendToQbittorrent(torrentId) }
            }
        }

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
        if (freeleechTokenCount > 0) {
            labels.add(getString(R.string.redacted_use_token))
            actions.add { confirmTokenDownload(torrentId) }
        }
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

    /** Double-tap edition row: pick format, optional FL token, download / edit / permalink. */
    private fun showEditionDownloadMenu(bucketListIndices: List<Int>) {
        if (bucketListIndices.isEmpty()) return
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        val fmtLabel = TextView(this).apply {
            text = getString(R.string.redacted_edition_pick_format)
            setTextColor(ContextCompat.getColor(this@RedactedTorrentGroupActivity, R.color.app_text_primary))
        }
        val spinner = Spinner(this)
        val formatLabels = bucketListIndices.map { idx ->
            torrentObjects.getOrNull(idx)?.let { t ->
                buildTorrentTitleLine(t, RedactedGazelleTorrentUser.isUserSeedingTorrent(t))
            } ?: "—"
        }
        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            formatLabels,
        )
        val useTokenSwitch = SwitchMaterial(this).apply {
            text = getString(R.string.redacted_use_token)
        }
        val showFlSwitch = freeleechTokenCount > 0
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val dl = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.redacted_download_selected)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val edit = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.redacted_edit_torrent_short)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val permalink = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.redacted_permalink)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val showQbt = SearchPrefs(this).isQbittorrentConfigured()
        val qbtSend = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.redacted_send_qbittorrent)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        root.addView(fmtLabel)
        val lpSpinner = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        lpSpinner.topMargin = pad / 2
        root.addView(spinner, lpSpinner)
        if (showFlSwitch) {
            val lpSw = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lpSw.topMargin = pad / 2
            root.addView(useTokenSwitch, lpSw)
        }
        btnRow.addView(dl)
        btnRow.addView(edit)
        val lpRow = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        lpRow.topMargin = pad
        root.addView(btnRow, lpRow)
        if (showQbt) {
            val lpQbt = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lpQbt.topMargin = pad / 2
            root.addView(qbtSend, lpQbt)
        }
        val lpPl = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        lpPl.topMargin = pad / 2
        root.addView(permalink, lpPl)

        fun selectedListIndex(): Int =
            bucketListIndices[spinner.selectedItemPosition.coerceIn(bucketListIndices.indices)]
        fun selectedTorrentId(): Int? = torrentIds.getOrNull(selectedListIndex())

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.redacted_edition_dl_menu_title)
            .setView(root)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dl.setOnClickListener {
            val tid = selectedTorrentId() ?: return@setOnClickListener
            dialog.dismiss()
            if (showFlSwitch && useTokenSwitch.isChecked) {
                confirmTokenDownload(tid)
            } else {
                downloadTorrent(tid, false)
            }
        }
        edit.setOnClickListener {
            val tid = selectedTorrentId() ?: return@setOnClickListener
            dialog.dismiss()
            startActivity(
                Intent(this, RedactedTorrentEditActivity::class.java)
                    .putExtra(RedactedExtras.TORRENT_ID, tid),
            )
        }
        permalink.setOnClickListener {
            val tid = selectedTorrentId() ?: return@setOnClickListener
            val url = "https://redacted.sh/torrents.php?torrentid=$tid"
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Permalink", url))
            Toast.makeText(this, R.string.redacted_permalink_copied, Toast.LENGTH_SHORT).show()
        }
        if (showQbt) {
            qbtSend.setOnClickListener {
                val tid = selectedTorrentId() ?: return@setOnClickListener
                dialog.dismiss()
                val ut = showFlSwitch && useTokenSwitch.isChecked
                sendTorrentToQbittorrent(tid, ut)
            }
        }
        dialog.show()
    }

    private fun extractAlbumCoverEntries(group: JSONObject): List<AlbumCoverEntry> {
        val map = LinkedHashMap<String, String>()
        fun addUrl(raw: String?, label: String) {
            val t = raw?.trim().orEmpty()
            if (t.isEmpty()) return
            val url = when {
                t.startsWith("http", ignoreCase = true) -> t
                t.startsWith("//") -> "https:$t"
                else -> "https://redacted.sh/${t.trimStart('/')}"
            }
            if (!map.containsKey(url)) map[url] = label
        }
        addUrl(group.optString("wikiImage").takeIf { it.isNotBlank() }, getString(R.string.redacted_cover_primary))
        val proxy = group.optString("proxyImage").trim()
        if (proxy.startsWith("http", ignoreCase = true) || proxy.startsWith("//")) {
            addUrl(proxy, getString(R.string.redacted_cover_proxy))
        }
        val coverKeys = listOf("covers", "coverArt", "alternateCovers", "images")
        for (key in coverKeys) {
            val arr = group.optJSONArray(key) ?: continue
            for (i in 0 until arr.length()) {
                when (val el = arr.opt(i)) {
                    is String -> addUrl(el, getString(R.string.redacted_cover_alt_fmt, i + 1))
                    is JSONObject -> {
                        val u = el.optString("image").ifBlank {
                            el.optString("url").ifBlank { el.optString("thumb") }
                        }
                        val lbl = el.optString("summary").ifBlank {
                            el.optString("name").ifBlank { el.optString("title") }
                        }.ifBlank { getString(R.string.redacted_cover_alt_fmt, i + 1) }
                        addUrl(u, lbl)
                    }
                }
            }
        }
        return map.map { AlbumCoverEntry(it.key, it.value) }
    }

    /**
     * Site-relative and redacted.sh URLs use [RedactedAvatarLoader] when an API key is set;
     * external HTTP(S) uses a plain connection.
     */
    private fun loadCoverBitmapFromUrl(imageUrl: String, maxSidePx: Int): Bitmap? {
        val key = SearchPrefs(this).redactedApiKey?.trim().orEmpty()
        if (key.isNotEmpty()) {
            RedactedAvatarLoader.loadBitmap(imageUrl, key, maxSidePx)?.let { return it }
        }
        val url = when {
            imageUrl.startsWith("http", ignoreCase = true) -> imageUrl.trim()
            imageUrl.startsWith("//") -> "https:${imageUrl.trim()}"
            imageUrl.isBlank() -> return null
            else -> "https://redacted.sh/${imageUrl.trim().trimStart('/')}"
        }
        if (url.contains("redacted.sh", ignoreCase = true)) return null
        return try {
            OutgoingUrlLog.log("GET", url)
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "turnTable/1.0")
            val bytes = conn.inputStream.use { it.readBytes() }
            decodeSampledBitmapFromBytes(bytes, maxSidePx)
        } catch (_: Exception) {
            null
        }
    }

    private fun showCoverGalleryDialog() {
        if (albumCoverEntries.isEmpty()) {
            Toast.makeText(this, R.string.redacted_covers_none, Toast.LENGTH_SHORT).show()
            return
        }
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()
        val imgMaxH = (360 * density).toInt()

        val labelTv = TextView(this).apply {
            setPadding(pad, 0, pad, pad / 2)
            setTextColor(ContextCompat.getColor(this@RedactedTorrentGroupActivity, R.color.app_text_primary))
            textSize = 14f
        }
        val counterTv = TextView(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, 0, pad, pad / 2)
            setTextColor(ContextCompat.getColor(this@RedactedTorrentGroupActivity, R.color.app_text_secondary))
        }
        val imageView = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            maxHeight = imgMaxH
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(pad, pad / 2, pad, pad)
        }
        val prev = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.redacted_cover_prev)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val next = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.redacted_cover_next)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(prev)
        row.addView(next)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(labelTv)
            addView(counterTv)
            addView(imageView)
            addView(row)
        }
        val scroll = ScrollView(this).apply {
            addView(column)
        }

        var index = 0
        val galleryLoadGen = AtomicInteger(0)
        fun applyIndex(newIdx: Int) {
            val n = albumCoverEntries.size
            index = newIdx.coerceIn(0, (n - 1).coerceAtLeast(0))
            prev.isEnabled = index > 0
            next.isEnabled = index < n - 1
            counterTv.text = getString(R.string.redacted_cover_counter_fmt, index + 1, n)
            labelTv.text = albumCoverEntries[index].label
            val g = galleryLoadGen.incrementAndGet()
            imageView.setImageDrawable(null)
            val url = albumCoverEntries[index].url
            Thread {
                val bmp = loadCoverBitmapFromUrl(url, 1600)
                runOnUiThread {
                    if (g != galleryLoadGen.get()) {
                        if (bmp != null && !bmp.isRecycled) bmp.recycle()
                        return@runOnUiThread
                    }
                    if (bmp != null) imageView.setImageBitmap(bmp)
                }
            }.start()
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.redacted_cover_gallery_title)
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .create()

        prev.setOnClickListener { applyIndex(index - 1) }
        next.setOnClickListener { applyIndex(index + 1) }
        dialog.setOnDismissListener {
            galleryLoadGen.incrementAndGet()
            val d = imageView.drawable
            imageView.setImageDrawable(null)
            if (d is BitmapDrawable) {
                val b = d.bitmap
                if (b != null && !b.isRecycled) b.recycle()
            }
        }
        dialog.show()
        applyIndex(0)
    }

    private fun loadAlbumThumb(imageUrl: String) {
        val gen = albumLoadGeneration.get()
        val maxSide = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_IN,
            1f,
            resources.displayMetrics,
        ).toInt().coerceAtLeast(1)
        Thread {
            val bmp = loadCoverBitmapFromUrl(imageUrl, maxSide)
            if (bmp != null) {
                runOnUiThread {
                    if (gen != albumLoadGeneration.get()) {
                        if (!bmp.isRecycled) bmp.recycle()
                        return@runOnUiThread
                    }
                    binding.imageAlbumThumb.setImageBitmap(bmp)
                    binding.imageAlbumThumb.visibility = View.VISIBLE
                }
            } else {
                runOnUiThread {
                    if (gen == albumLoadGeneration.get()) {
                        binding.imageAlbumThumb.visibility = View.GONE
                    }
                }
            }
        }.start()
    }

    private fun decodeSampledBitmapFromBytes(bytes: ByteArray, maxSidePx: Int): android.graphics.Bitmap? {
        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        bounds.inJustDecodeBounds = false
        bounds.inSampleSize = computeInSampleSize(bounds, maxSidePx, maxSidePx)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    }

    private fun computeInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        var inSampleSize = 1
        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
            var halfH = options.outHeight / 2
            var halfW = options.outWidth / 2
            while (halfH / inSampleSize >= reqHeight && halfW / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
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

    /** Fetches `.torrent` from Redacted then POSTs to qBittorrent Web API ([QbittorrentWebClient]). */
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
                            RedactedUiHelper.TorrentDownloadOutcome.Shared -> { /* chooser opened */ }
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
