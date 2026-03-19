package com.turntable.barcodescanner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.turntable.barcodescanner.databinding.ActivitySearchBinding
import com.turntable.barcodescanner.redacted.RedactedExtras
import java.net.HttpURLConnection
import java.net.URL

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val barcode = intent.getStringExtra(EXTRA_BARCODE).orEmpty()
        binding.editBarcode.setText(barcode)
        val prefillTerms = intent.getStringExtra(EXTRA_PREFILL_SECONDARY_TERMS).orEmpty()
        if (prefillTerms.isNotBlank()) {
            binding.editSecondarySearchTerms.setText(prefillTerms)
        }

        val hasSecondary = !SearchPrefs(this).secondarySearchUrl.isNullOrBlank()
        binding.secondarySearchTermsContainer.visibility = if (hasSecondary) View.VISIBLE else View.GONE

        binding.buttonSubmit.setOnClickListener { submit(barcode) }
        if (intent.getBooleanExtra(EXTRA_AUTOSUBMIT, false)) {
            binding.buttonSubmit.post { submit(barcode) }
        }

        val hasRedacted = !SearchPrefs(this).redactedApiKey.isNullOrBlank()
        binding.buttonRedactedSearch.visibility = if (hasRedacted) View.VISIBLE else View.GONE
        binding.buttonRedactedSearch.setOnClickListener {
            val q = binding.editSecondarySearchTerms.text?.toString()?.trim().orEmpty()
            startActivity(
                Intent(this, RedactedBrowseActivity::class.java).apply {
                    if (q.isNotBlank()) putExtra(RedactedExtras.INITIAL_QUERY, q)
                },
            )
        }
    }

    private fun submit(barcode: String) {
        val prefs = SearchPrefs(this)
        val notes = binding.editNotes.text?.toString().orEmpty()
        val category = binding.editCategory.text?.toString().orEmpty()

        when (prefs.method) {
            SearchPrefs.METHOD_GET -> {
                val secondaryUrl = prefs.secondarySearchUrl?.takeIf { it.isNotBlank() }
                if (secondaryUrl.isNullOrBlank()) {
                    Toast.makeText(this, R.string.configure_secondary_url, Toast.LENGTH_LONG).show()
                    return
                }
                val secondaryPkg = prefs.secondaryBrowserPackage
                val secondaryQuery = binding.editSecondarySearchTerms.text?.toString()?.trim()
                if (prefs.secondarySearchAutoFromMusicBrainz) {
                    fetchPrimaryInfoAndOpenSecondary(barcode, secondaryUrl, secondaryPkg, prefs)
                } else if (!secondaryQuery.isNullOrBlank()) {
                    openSecondaryUrl(secondaryUrl, secondaryQuery, secondaryPkg)
                    finish()
                } else {
                    Toast.makeText(this, R.string.secondary_no_artist_title, Toast.LENGTH_SHORT).show()
                }
            }
            SearchPrefs.METHOD_POST -> {
                val url = prefs.secondarySearchUrl
                if (url.isNullOrBlank()) {
                    Toast.makeText(this, R.string.configure_secondary_url, Toast.LENGTH_LONG).show()
                    return
                }
                doPost(url, barcode, notes, category, prefs)
            }
        }
    }

    private fun openInBrowser(url: String, pkg: String?, onDone: (success: Boolean) -> Unit) {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
            pkg?.let { setPackage(it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
            onDone(true)
        } catch (_: Exception) {
            val playStoreUrl = KnownBrowsers.findByPackage(pkg)?.playStoreUrl
                ?: "https://play.google.com/store/apps/details?id=${pkg?.let { java.net.URLEncoder.encode(it, "UTF-8") } ?: ""}"
            val displayName = KnownBrowsers.findByPackage(pkg)?.name ?: pkg ?: ""
            if (!pkg.isNullOrBlank()) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(playStoreUrl)))
                    Toast.makeText(this, getString(R.string.browser_open_play_store, displayName.ifBlank { pkg }), Toast.LENGTH_SHORT).show()
                    onDone(false)
                } catch (_: Exception) {
                    Toast.makeText(this, R.string.could_not_open_link, Toast.LENGTH_SHORT).show()
                    onDone(false)
                }
            } else {
                try {
                    intent.setPackage(null)
                    startActivity(intent)
                    onDone(true)
                } catch (_: Exception) {
                    Toast.makeText(this, R.string.could_not_open_link, Toast.LENGTH_SHORT).show()
                    onDone(false)
                }
            }
        }
    }

    private fun openSecondaryUrl(
        secondaryUrl: String,
        query: String,
        pkg: String?,
        coverUrl: String? = null,
    ) {
        fun enc(s: String) = java.net.URLEncoder.encode(s, Charsets.UTF_8.name())
        val parts = query.split(" - ", limit = 2)
        val artist = parts.getOrNull(0)?.trim().orEmpty()
        val album = parts.getOrNull(1)?.trim().orEmpty()
        val url = secondaryUrl
            .replace("%s", enc(query))
            .replace("%artist%", enc(if (artist.isNotBlank()) artist else query))
            .replace("%album%", enc(if (album.isNotBlank()) album else query))
        SearchHistoryStore.add(
            this,
            barcode = binding.editBarcode.text?.toString().orEmpty(),
            title = query,
            coverUrl = coverUrl,
        )
        openInBrowser(url, pkg) { finish() }
    }

    private fun fetchPrimaryInfoAndOpenSecondary(
        barcode: String,
        secondaryUrl: String,
        pkg: String?,
        prefs: SearchPrefs
    ) {
        val orderedCmds = SearchPresets.primaryApiCmdsOrderedEnabled(this)
        val discogsToken = prefs.discogsPersonalToken
        Thread {
            PrimarySearchAssist.clearMusicBrainzSearchCache()
            var lookup: PrimaryLookupResult? = null
            for (cmd in orderedCmds) {
                try {
                    lookup = when (cmd) {
                        "discogs" -> PrimarySearchAssist.fetchDiscogs(barcode, discogsToken)
                        "theaudiodb" -> PrimarySearchAssist.fetchTheAudioDb(barcode, prefs.theAudioDbApiKey)
                        "lastfm" -> PrimarySearchAssist.fetchLastFm(barcode, prefs.lastFmApiKey)
                        "musicbrainz" -> PrimarySearchAssist.fetchMusicBrainz(barcode)
                        else -> null
                    }
                    if (lookup != null) break
                } catch (_: Exception) {
                    // try next in list
                }
            }
            val query = lookup?.searchQuery
            val coverUrl = lookup?.coverImageUrl
            runOnUiThread {
                applyCoverAssist(coverUrl)
                if (!query.isNullOrBlank()) {
                    openSecondaryUrl(secondaryUrl, query, pkg, coverUrl)
                } else {
                    val manual = binding.editSecondarySearchTerms.text?.toString()?.trim()
                    if (!manual.isNullOrBlank()) {
                        openSecondaryUrl(secondaryUrl, manual, pkg, null)
                    } else {
                        Toast.makeText(this, R.string.secondary_no_artist_title, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
        }.start()
    }

    /** Fill cover URL field, optional preview, clipboard (yadg-style assist). */
    private fun applyCoverAssist(coverUrl: String?) {
        if (coverUrl.isNullOrBlank()) {
            binding.editCoverImageUrl.text?.clear()
            binding.imageCover.setImageDrawable(null)
            binding.cardCoverPreview.visibility = View.GONE
            return
        }
        binding.editCoverImageUrl.setText(coverUrl)
        binding.cardCoverPreview.visibility = View.VISIBLE
        loadCoverPreview(coverUrl)
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Cover URL", coverUrl))
        Toast.makeText(this, R.string.cover_url_copied, Toast.LENGTH_SHORT).show()
    }

    private fun loadCoverPreview(imageUrl: String) {
        Thread {
            var bmp: android.graphics.Bitmap? = null
            try {
                val conn = URL(imageUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 12000
                conn.readTimeout = 12000
                conn.setRequestProperty("User-Agent", "turnTable/1.0")
                if (conn.responseCode in 200..299) {
                    val bytes = conn.inputStream.use { it.readBytes() }
                    conn.disconnect()
                    if (bytes.isNotEmpty()) {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        bounds.inJustDecodeBounds = false
                        bounds.inSampleSize = computeInSampleSize(bounds, 512, 512)
                        bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    }
                } else {
                    conn.disconnect()
                }
            } catch (_: Exception) {
                bmp = null
            }
            runOnUiThread {
                if (bmp != null) {
                    binding.imageCover.setImageBitmap(bmp)
                } else {
                    binding.imageCover.setImageDrawable(null)
                }
            }
        }.start()
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

    private fun doPost(
        url: String,
        barcode: String,
        notes: String,
        category: String,
        prefs: SearchPrefs
    ) {
        Thread {
            try {
                val body = (prefs.postBody ?: """{"code":"$barcode"}""")
                    .replace("%s", barcode)
                    .replace("\$code", barcode)
                    .replace("\$notes", notes)
                    .replace("\$category", category)
                val contentType = prefs.postContentType ?: "application/json"
                val headers = prefs.postHeaders?.lines()?.filter { it.contains(":") }
                    ?.associate { line ->
                        val i = line.indexOf(':')
                        line.substring(0, i).trim() to line.substring(i + 1).trim()
                    } ?: emptyMap()

                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", contentType)
                headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                runOnUiThread {
                    Toast.makeText(
                        this,
                        if (code in 200..299) "Sent ($code)" else "Response: $code",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }.start()
    }

    companion object {
        const val EXTRA_BARCODE = "barcode"
        const val EXTRA_PREFILL_SECONDARY_TERMS = "prefill_secondary_terms"
        const val EXTRA_AUTOSUBMIT = "autosubmit"
    }
}
