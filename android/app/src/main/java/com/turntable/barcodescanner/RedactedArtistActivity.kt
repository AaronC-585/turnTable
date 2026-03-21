package com.turntable.barcodescanner

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.turntable.barcodescanner.databinding.ActivityRedactedArtistBinding
import com.turntable.barcodescanner.databinding.ItemRedactedArtistGroupBinding
import com.turntable.barcodescanner.debug.AppEventLog
import com.turntable.barcodescanner.redacted.RedactedAvatarLoader
import com.turntable.barcodescanner.redacted.RedactedBrowseParamsCodec
import com.turntable.barcodescanner.redacted.RedactedExtras
import com.turntable.barcodescanner.redacted.RedactedGazelleReleaseType
import com.turntable.barcodescanner.redacted.RedactedGazelleTorrentParse
import com.turntable.barcodescanner.redacted.RedactedResult
import com.turntable.barcodescanner.redacted.RedactedUiHelper
import org.json.JSONArray
import org.json.JSONObject

/**
 * Artist page layout modeled on the site: header, bracket-style actions, image, file-list search,
 * tags, similar artists, info body, discography jump bar, and releases grouped by release type.
 */
class RedactedArtistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRedactedArtistBinding
    private lateinit var api: com.turntable.barcodescanner.redacted.RedactedApiClient
    private var artistId: Int = 0
    private var artistName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = RedactedUiHelper.requireApi(this) ?: return
        api = c
        artistId = intent.getIntExtra(RedactedExtras.ARTIST_ID, 0)
        if (artistId <= 0) {
            Toast.makeText(this, R.string.redacted_invalid_id, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        binding = ActivityRedactedArtistBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)
        supportActionBar?.title = getString(R.string.redacted_artist_screen)

        binding.buttonFileListSearch.setOnClickListener { runFileListSearch() }

        loadArtist()
    }

    private fun loadArtist() {
        binding.progress.visibility = View.VISIBLE
        Thread {
            val result = api.artist(artistId)
            runOnUiThread {
                binding.progress.visibility = View.GONE
                when (result) {
                    is RedactedResult.Failure -> {
                        AppEventLog.log(AppEventLog.Category.ERROR, "artist load: ${result.message}")
                        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                    }
                    is RedactedResult.Success -> bindArtist(result.response)
                    else -> Toast.makeText(this, R.string.redacted_unexpected, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun bindArtist(body: JSONObject?) {
        if (body == null) {
            Toast.makeText(this, R.string.redacted_unexpected, Toast.LENGTH_SHORT).show()
            return
        }
        artistName = body.optString("name").trim()
        if (artistName.isNotEmpty()) {
            supportActionBar?.title = artistName
            binding.textArtistTitle.text = artistName
        } else {
            binding.textArtistTitle.text = getString(R.string.redacted_artist_screen)
        }

        buildActionRow()
        loadArtistImage(body.optString("image").trim().ifBlank { body.optString("img") })
        setupTags(body.optJSONArray("tags"))
        setupSimilarArtists(
            body.optJSONArray("similarArtists")
                ?: body.optJSONArray("similarArtist")
                ?: body.optJSONArray("similarartists"),
        )
        setupArtistBody(body.optString("body").trim())
        buildDiscography(optTorrentGroupArray(body))
    }

    private fun buildActionRow() {
        binding.rowArtistActions.removeAllViews()
        val accent = ContextCompat.getColor(this, R.color.app_accent)
        fun addBracketButton(label: String, onClick: () -> Unit) {
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = label
                setOnClickListener { onClick() }
                strokeColor = android.content.res.ColorStateList.valueOf(accent)
                setTextColor(accent)
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.marginEnd = (6 * resources.displayMetrics.density).toInt()
            binding.rowArtistActions.addView(btn, lp)
        }
        addBracketButton(getString(R.string.redacted_artist_open_site)) {
            RedactedUiHelper.openSite(this, "artist.php?id=$artistId")
        }
        addBracketButton(getString(R.string.redacted_artist_similar_in_app)) {
            startActivity(
                Intent(this, RedactedSimilarArtistsActivity::class.java)
                    .putExtra(RedactedExtras.ARTIST_ID, artistId),
            )
        }
        addBracketButton(getString(R.string.redacted_artist_add_request)) {
            RedactedUiHelper.openSite(this, "requests.php?action=new&artistid=$artistId")
        }
        addBracketButton(getString(R.string.redacted_artist_edit_site)) {
            RedactedUiHelper.openSite(this, "artist.php?action=edit&artistid=$artistId")
        }
        addBracketButton(getString(R.string.redacted_artist_history_site)) {
            RedactedUiHelper.openSite(this, "artist.php?action=history&artistid=$artistId")
        }
        addBracketButton(getString(R.string.redacted_artist_info)) {
            scrollToViewInArtistPage(binding.textArtistBody)
        }
        addBracketButton(getString(R.string.redacted_artist_comments_site)) {
            RedactedUiHelper.openSite(this, "artist.php?id=$artistId#artistcomments")
        }
    }

    /** Scroll [NestedScrollView] so [target] (a descendant) is near the top. */
    private fun scrollToViewInArtistPage(target: View) {
        if (target.visibility != View.VISIBLE) {
            Toast.makeText(this, R.string.redacted_artist_info, Toast.LENGTH_SHORT).show()
            return
        }
        val scroll = binding.scrollMain
        binding.scrollMain.post {
            var y = 0
            var v: View? = target
            while (v != null && v !== scroll) {
                y += v.top
                val parent = v.parent
                if (parent === scroll) break
                v = parent as? View
            }
            scroll.smoothScrollTo(0, y.coerceAtLeast(0))
        }
    }

    private fun loadArtistImage(raw: String) {
        if (raw.isBlank()) {
            binding.cardArtistImage.visibility = View.GONE
            return
        }
        binding.cardArtistImage.visibility = View.VISIBLE
        binding.imageArtist.setImageDrawable(null)
        Thread {
            val bmp = RedactedAvatarLoader.loadBitmap(raw, api.redactedAuthorizationValue(), 880)
            runOnUiThread {
                if (bmp != null) {
                    binding.imageArtist.setImageBitmap(bmp)
                } else {
                    binding.cardArtistImage.visibility = View.GONE
                }
            }
        }.start()
    }

    private fun runFileListSearch() {
        val q = binding.editFileListQuery.text?.toString()?.trim().orEmpty()
        if (q.isEmpty()) {
            Toast.makeText(this, R.string.redacted_artist_file_list_hint, Toast.LENGTH_SHORT).show()
            return
        }
        val name = artistName.ifBlank { binding.textArtistTitle.text?.toString()?.trim().orEmpty() }
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.redacted_unexpected, Toast.LENGTH_SHORT).show()
            return
        }
        val params = listOf(
            "action" to "advanced",
            "searchsubmit" to "1",
            "artistname" to name,
            "filelist" to q,
        )
        startActivity(
            Intent(this, RedactedBrowseResultsActivity::class.java)
                .putExtra(RedactedExtras.BROWSE_PARAMS_JSON, RedactedBrowseParamsCodec.encode(params)),
        )
    }

    private fun setupTags(arr: JSONArray?) {
        val tags = parseTags(arr)
        binding.chipGroupTags.removeAllViews()
        if (tags.isEmpty()) {
            binding.labelTags.visibility = View.GONE
            binding.chipGroupTags.visibility = View.GONE
            return
        }
        binding.labelTags.visibility = View.VISIBLE
        binding.chipGroupTags.visibility = View.VISIBLE
        val nameForBrowse = artistName.ifBlank { binding.textArtistTitle.text?.toString()?.trim().orEmpty() }
        for ((tag, count) in tags) {
            val label = if (count > 0) "$tag ($count)" else tag
            val chip = Chip(this).apply {
                text = label
                isClickable = true
                setOnClickListener {
                    if (nameForBrowse.isEmpty()) return@setOnClickListener
                    val params = listOf(
                        "action" to "advanced",
                        "searchsubmit" to "1",
                        "artistname" to nameForBrowse,
                        "taglist" to tag.replace(' ', '.'),
                    )
                    startActivity(
                        Intent(this@RedactedArtistActivity, RedactedBrowseResultsActivity::class.java)
                            .putExtra(RedactedExtras.BROWSE_PARAMS_JSON, RedactedBrowseParamsCodec.encode(params)),
                    )
                }
            }
            binding.chipGroupTags.addView(chip)
        }
    }

    private fun setupSimilarArtists(arr: JSONArray?) {
        val items = parseSimilar(arr)
        binding.containerSimilarArtists.removeAllViews()
        if (items.isEmpty()) {
            binding.labelSimilar.visibility = View.GONE
            binding.containerSimilarArtists.visibility = View.GONE
            return
        }
        binding.labelSimilar.visibility = View.VISIBLE
        binding.containerSimilarArtists.visibility = View.VISIBLE
        val accent = ContextCompat.getColor(this, R.color.app_accent)
        for ((sid, sname) in items) {
            val tv = TextView(this).apply {
                text = sname
                setTextColor(accent)
                textSize = 15f
                setPadding(0, (6 * resources.displayMetrics.density).toInt(), 0, (6 * resources.displayMetrics.density).toInt())
                setOnClickListener {
                    startActivity(
                        Intent(this@RedactedArtistActivity, RedactedArtistActivity::class.java)
                            .putExtra(RedactedExtras.ARTIST_ID, sid),
                    )
                }
            }
            binding.containerSimilarArtists.addView(tv)
        }
    }

    private fun setupArtistBody(raw: String) {
        if (raw.isBlank()) {
            binding.labelArtistInfo.visibility = View.GONE
            binding.textArtistBody.visibility = View.GONE
            binding.textArtistBody.text = ""
            return
        }
        binding.labelArtistInfo.visibility = View.VISIBLE
        binding.textArtistBody.visibility = View.VISIBLE
        binding.textArtistBody.text = RedactedGazelleTorrentParse.stripBbCodeForPreview(raw)
    }

    private fun buildDiscography(torrentGroup: JSONArray) {
        binding.containerDiscography.removeAllViews()
        binding.rowJumpReleaseTypes.removeAllViews()
        binding.scrollJumpReleaseTypes.visibility = View.GONE
        binding.labelDiscographyJump.visibility = View.GONE

        val groups = mutableListOf<JSONObject>()
        for (i in 0 until torrentGroup.length()) {
            torrentGroup.optJSONObject(i)?.let { groups.add(it) }
        }
        if (groups.isEmpty()) {
            val empty = TextView(this).apply {
                text = getString(R.string.redacted_artist_no_releases)
                setTextColor(ContextCompat.getColor(context, R.color.app_text_secondary))
                textSize = 14f
            }
            binding.containerDiscography.addView(empty)
            return
        }

        val byType = LinkedHashMap<Int, MutableList<JSONObject>>()
        for (g in groups) {
            val rt = groupReleaseType(g)
            byType.getOrPut(rt) { mutableListOf() }.add(g)
        }
        for (list in byType.values) {
            list.sortWith(
                compareByDescending<JSONObject> { groupYear(it) }
                    .thenBy { groupTitle(it) },
            )
        }

        val sortedTypes = byType.keys.sorted()
        val sectionAnchors = LinkedHashMap<Int, View>()

        binding.labelDiscographyJump.visibility = View.VISIBLE
        binding.scrollJumpReleaseTypes.visibility = View.VISIBLE

        val jumpAccent = ContextCompat.getColor(this, R.color.app_accent)
        for (rt in sortedTypes) {
            val label = RedactedGazelleReleaseType.label(this, rt)
            val header = TextView(this).apply {
                text = label
                textSize = 17f
                setTextColor(ContextCompat.getColor(context, R.color.app_text_primary))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, (8 * resources.displayMetrics.density).toInt())
            }
            sectionAnchors[rt] = header
            binding.containerDiscography.addView(header)

            val jumpBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = getString(R.string.redacted_artist_jump_fmt, label)
                setTextColor(jumpAccent)
                strokeColor = android.content.res.ColorStateList.valueOf(jumpAccent)
                setOnClickListener {
                    val v = sectionAnchors[rt] ?: return@setOnClickListener
                    scrollToViewInArtistPage(v)
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.marginEnd = (6 * resources.displayMetrics.density).toInt()
            binding.rowJumpReleaseTypes.addView(jumpBtn, lp)

            for (g in byType[rt].orEmpty()) {
                binding.containerDiscography.addView(inflateGroupRow(g))
            }
        }
    }

    private fun inflateGroupRow(g: JSONObject): View {
        val vb = ItemRedactedArtistGroupBinding.inflate(LayoutInflater.from(this))
        val gid = groupId(g)
        val year = groupYear(g)
        val title = groupTitle(g)
        val titleLine = if (year > 0) "$year — $title" else title
        vb.textGroupTitle.text = titleLine
        val tagLine = formatGroupTags(g)
        if (tagLine.isNotEmpty()) {
            vb.textGroupTags.visibility = View.VISIBLE
            vb.textGroupTags.text = tagLine
        }
        val (snatches, seeders, leechers) = aggregateTorrentStats(g)
        vb.textGroupStats.text = getString(R.string.redacted_artist_group_stats, snatches, seeders, leechers)
        bindGroupCoverThumbnail(vb.imageGroupCover, g, titleLine)
        vb.root.setOnClickListener {
            if (gid <= 0) return@setOnClickListener
            startActivity(
                Intent(this, RedactedTorrentGroupActivity::class.java)
                    .putExtra(RedactedExtras.GROUP_ID, gid),
            )
        }
        return vb.root
    }

    /**
     * First available cover URL from artist [torrentgroup] JSON (same ideas as browse `cover` / group wiki image).
     */
    private fun groupCoverUrl(g: JSONObject): String? {
        fun firstString(vararg keys: String): String? {
            for (k in keys) {
                val s = g.optString(k).trim()
                if (s.isNotEmpty()) return s
            }
            return null
        }
        firstString("cover", "wikiImage", "image", "coverUrl", "artworkUrl")?.let { return it }

        val cov = g.optJSONArray("covers")
        if (cov != null && cov.length() > 0) {
            when (val el = cov.opt(0)) {
                is String -> el.trim().takeIf { it.isNotEmpty() }?.let { return it }
                is JSONObject -> {
                    val u = el.optString("image").ifBlank {
                        el.optString("url").ifBlank { el.optString("thumb") }
                    }.trim()
                    if (u.isNotEmpty()) return u
                }
            }
        }
        g.optJSONArray("coverArt")?.optJSONObject(0)?.let { o ->
            val u = o.optString("image").ifBlank { o.optString("url") }.trim()
            if (u.isNotEmpty()) return u
        }
        g.optJSONArray("alternateCovers")?.optJSONObject(0)?.let { o ->
            val u = o.optString("image").ifBlank { o.optString("url") }.trim()
            if (u.isNotEmpty()) return u
        }
        return null
    }

    private fun bindGroupCoverThumbnail(iv: ImageView, g: JSONObject, titleForA11y: String) {
        val raw = groupCoverUrl(g)?.trim().orEmpty()
        if (raw.isEmpty()) {
            iv.visibility = View.GONE
            iv.setImageDrawable(null)
            iv.tag = null
            return
        }
        iv.visibility = View.VISIBLE
        iv.setImageDrawable(null)
        iv.tag = raw
        iv.contentDescription = getString(R.string.redacted_cover) + ": " + titleForA11y
        val auth = api.redactedAuthorizationValue()
        Thread {
            val bmp = RedactedAvatarLoader.loadBitmap(raw, auth, maxSidePx = 288)
            runOnUiThread {
                if (iv.tag != raw) return@runOnUiThread
                if (bmp != null) {
                    iv.setImageBitmap(bmp)
                    iv.visibility = View.VISIBLE
                } else {
                    iv.setImageDrawable(null)
                    iv.visibility = View.GONE
                }
            }
        }.start()
    }

    private fun optTorrentGroupArray(body: JSONObject): JSONArray =
        body.optJSONArray("torrentgroup")
            ?: body.optJSONArray("torrentGroup")
            ?: body.optJSONArray("torrentgroups")
            ?: JSONArray()

    private fun groupId(g: JSONObject) = g.optInt("groupId", g.optInt("id", 0))

    private fun groupTitle(g: JSONObject) =
        g.optString("groupName").trim().ifBlank { g.optString("name").trim() }

    private fun groupYear(g: JSONObject) = g.optInt("year", 0)

    private fun groupReleaseType(g: JSONObject) =
        g.optInt("releaseType", g.optInt("release_type", 1))

    private fun torrentArray(g: JSONObject) =
        g.optJSONArray("torrent") ?: g.optJSONArray("torrents")

    private fun aggregateTorrentStats(g: JSONObject): Triple<Int, Int, Int> {
        val arr = torrentArray(g) ?: return Triple(0, 0, 0)
        var maxSeed = 0
        var maxLeech = 0
        var totalSnatch = 0
        for (i in 0 until arr.length()) {
            val t = arr.optJSONObject(i) ?: continue
            maxSeed = maxOf(maxSeed, t.optInt("seeders"))
            maxLeech = maxOf(maxLeech, t.optInt("leechers"))
            totalSnatch += t.optInt("snatched")
        }
        return Triple(totalSnatch, maxSeed, maxLeech)
    }

    private fun formatGroupTags(g: JSONObject): String {
        val arr = g.optJSONArray("tags") ?: return ""
        return buildList {
            for (i in 0 until arr.length()) {
                when (val el = arr.opt(i)) {
                    is String -> if (el.isNotBlank()) add(el.trim())
                    is JSONObject -> {
                        val n = el.optString("name").trim()
                        if (n.isNotEmpty()) add(n)
                    }
                }
            }
        }.joinToString(", ")
    }

    private fun parseTags(arr: JSONArray?): List<Pair<String, Int>> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                when (val el = arr.opt(i)) {
                    is JSONObject -> {
                        val name = el.optString("name").trim()
                        if (name.isNotEmpty()) add(name to el.optInt("count", 0))
                    }
                    is String -> if (el.isNotBlank()) add(el.trim() to 0)
                }
            }
        }
    }

    private fun parseSimilar(arr: JSONArray?): List<Pair<Int, String>> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optInt("artistid", o.optInt("id", 0))
                val name = o.optString("name").ifBlank { o.optString("artistName") }.trim()
                if (id > 0 && name.isNotEmpty()) add(id to name)
            }
        }
    }
}
