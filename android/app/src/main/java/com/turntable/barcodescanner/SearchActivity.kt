package com.turntable.barcodescanner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.turntable.barcodescanner.databinding.ActivitySearchBinding
import com.turntable.barcodescanner.redacted.RedactedExtras
import com.turntable.barcodescanner.redacted.RedactedSearchAssist
class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding

    /** Cover URL from primary/Redacted assist (no longer shown in UI; still used for %cover% substitution). */
    private var assistedCoverUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        setupToolbarHome(binding.toolbar)

        val barcode = intent.getStringExtra(EXTRA_BARCODE).orEmpty()
        binding.editBarcode.setText(barcode)
        val prefillTerms = intent.getStringExtra(EXTRA_PREFILL_SECONDARY_TERMS).orEmpty()
        if (prefillTerms.isNotBlank()) {
            binding.editSecondarySearchTerms.setText(prefillTerms)
        }

        val prefsEarly = SearchPrefs(this)
        val hasSecondary = !prefsEarly.secondarySearchUrl.isNullOrBlank()
        val hasRedactedEarly = !prefsEarly.redactedApiKey.isNullOrBlank()
        binding.secondarySearchTermsContainer.visibility =
            if (hasSecondary || hasRedactedEarly) View.VISIBLE else View.GONE

        binding.buttonSubmit.setOnClickListener { submit(barcode) }
        if (intent.getBooleanExtra(EXTRA_AUTOSUBMIT, false)) {
            binding.buttonSubmit.post { submit(barcode) }
        }

        binding.buttonRedactedSearch.visibility = if (hasRedactedEarly) View.VISIBLE else View.GONE
        binding.buttonRedactedSearch.setOnClickListener {
            val q = binding.editSecondarySearchTerms.text?.toString()?.trim().orEmpty()
            startActivity(
                Intent(this, RedactedBrowseActivity::class.java).apply {
                    if (q.isNotBlank()) putExtra(RedactedExtras.INITIAL_QUERY, q)
                },
            )
        }

        if (barcode.isNotBlank() && hasRedactedEarly) {
            binding.root.post { prefetchRedactedFromScan(barcode) }
        }
    }

    private fun redactedApiKeyOrNull(): String? =
        SearchPrefs(this).redactedApiKey?.trim()?.takeIf { it.isNotEmpty() }

    /** When a Redacted API key is set, Submit uses in-app browse instead of opening an external browser. */
    private fun preferRedactedOverBrowser(): Boolean = redactedApiKeyOrNull() != null

    private fun coverFromUi(): String? = assistedCoverUrl

    /** Cover image from Redacted paths (needs Authorization). */
    private fun applyRedactedCoverAssist(rawPathOrUrl: String) {
        val abs = RedactedSearchAssist.absoluteCoverUrl(rawPathOrUrl)
        assistedCoverUrl = abs
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Cover URL", abs))
        Toast.makeText(this, R.string.cover_url_copied, Toast.LENGTH_SHORT).show()
    }

    /**
     * Fills secondary terms from the first Redacted `browse` hit. Never opens a browser.
     * @param quietIfNoHit if true, no toast when there are zero results (e.g. scan prefetch).
     */
    private fun fillFromRedactedBrowse(
        barcode: String,
        searchStr: String,
        fallbackCover: String? = null,
        quietIfNoHit: Boolean = false,
    ) {
        val key = redactedApiKeyOrNull() ?: return
        val q = searchStr.trim().ifEmpty { barcode.trim() }
        if (q.isEmpty()) {
            Toast.makeText(this, R.string.secondary_no_artist_title, Toast.LENGTH_SHORT).show()
            return
        }
        Thread {
            val hit = RedactedSearchAssist.firstHit(key, q)
            runOnUiThread {
                val bcStore = binding.editBarcode.text?.toString()?.trim().orEmpty().ifBlank { barcode }
                if (hit != null) {
                    binding.editSecondarySearchTerms.setText(hit.secondaryTerms)
                    if (hit.coverPathOrUrl != null) {
                        applyRedactedCoverAssist(hit.coverPathOrUrl)
                    } else {
                        applyCoverAssist(fallbackCover)
                    }
                    val coverHist = hit.coverPathOrUrl?.let { RedactedSearchAssist.absoluteCoverUrl(it) }
                        ?: fallbackCover
                    SearchHistoryStore.add(this, bcStore, hit.secondaryTerms, coverHist)
                    Toast.makeText(this, R.string.search_filled_from_redacted, Toast.LENGTH_SHORT).show()
                } else {
                    if (quietIfNoHit) {
                        return@runOnUiThread
                    }
                    binding.editSecondarySearchTerms.setText(q)
                    applyCoverAssist(fallbackCover)
                    SearchHistoryStore.add(this, bcStore, q, fallbackCover)
                    Toast.makeText(this, R.string.search_no_redacted_hit, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun prefetchRedactedFromScan(barcode: String) {
        fillFromRedactedBrowse(barcode, barcode, fallbackCover = null, quietIfNoHit = true)
    }

    private fun submit(barcode: String) {
        val prefs = SearchPrefs(this)

        when (prefs.method) {
            SearchPrefs.METHOD_GET -> {
                val secondaryUrl = prefs.secondarySearchUrl?.takeIf { it.isNotBlank() }
                val secondaryPkg = prefs.secondaryBrowserPackage
                val secondaryQuery = binding.editSecondarySearchTerms.text?.toString()?.trim()
                if (secondaryUrl.isNullOrBlank()) {
                    if (preferRedactedOverBrowser() && barcode.isNotBlank()) {
                        fillFromRedactedBrowse(barcode, barcode, coverFromUi(), quietIfNoHit = false)
                        return
                    }
                    Toast.makeText(this, R.string.configure_secondary_url, Toast.LENGTH_LONG).show()
                    return
                }
                if (prefs.secondarySearchAutoFromMusicBrainz) {
                    fetchPrimaryInfoAndOpenSecondary(barcode, secondaryUrl, secondaryPkg, prefs)
                } else if (!secondaryQuery.isNullOrBlank()) {
                    if (preferRedactedOverBrowser()) {
                        fillFromRedactedBrowse(barcode, secondaryQuery, coverFromUi(), quietIfNoHit = false)
                    } else {
                        openSecondaryUrl(secondaryUrl, secondaryPkg, secondaryVarsFromUi(barcode))
                        finish()
                    }
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
                doPost(url, prefs, secondaryVarsFromUi(barcode))
            }
        }
    }

    private fun secondaryVarsFromUi(
        barcode: String,
        query: String = binding.editSecondarySearchTerms.text?.toString()?.trim().orEmpty(),
        coverUrl: String? = assistedCoverUrl,
    ): SecondarySearchVariables = SecondarySearchVariables(
        barcode = binding.editBarcode.text?.toString()?.trim().orEmpty().ifBlank { barcode },
        notes = "",
        category = "",
        query = query,
        coverUrl = coverUrl,
    )

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
        pkg: String?,
        vars: SecondarySearchVariables,
    ) {
        if (preferRedactedOverBrowser()) {
            fillFromRedactedBrowse(vars.barcode, vars.query, vars.coverUrl, quietIfNoHit = false)
            return
        }
        val url = SecondarySearchSubstitution.substituteUrl(secondaryUrl, vars)
        SearchHistoryStore.add(
            this,
            barcode = vars.barcode,
            title = vars.query,
            coverUrl = vars.coverUrl,
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
        Thread {
            PrimarySearchAssist.clearMusicBrainzSearchCache()
            var lookup: PrimaryLookupResult? = null
            for (cmd in orderedCmds) {
                try {
                    lookup = when (cmd) {
                        "discogs" -> PrimarySearchAssist.fetchDiscogs(barcode, null)
                        "theaudiodb" -> PrimarySearchAssist.fetchTheAudioDb(barcode, prefs.theAudioDbApiKey)
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
                if (preferRedactedOverBrowser()) {
                    val q = when {
                        !query.isNullOrBlank() -> query.trim()
                        else -> binding.editSecondarySearchTerms.text?.toString()?.trim().orEmpty()
                    }.ifBlank { barcode }
                    fillFromRedactedBrowse(barcode, q, coverUrl, quietIfNoHit = false)
                    return@runOnUiThread
                }
                applyCoverAssist(coverUrl)
                if (!query.isNullOrBlank()) {
                    openSecondaryUrl(
                        secondaryUrl,
                        pkg,
                        secondaryVarsFromUi(
                            barcode = barcode,
                            query = query,
                            coverUrl = coverUrl,
                        ),
                    )
                } else {
                    val manual = binding.editSecondarySearchTerms.text?.toString()?.trim()
                    if (!manual.isNullOrBlank()) {
                        openSecondaryUrl(secondaryUrl, pkg, secondaryVarsFromUi(barcode, query = manual))
                    } else {
                        Toast.makeText(this, R.string.secondary_no_artist_title, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
        }.start()
    }

    /** Store cover URL for substitution; copy to clipboard (yadg-style assist). */
    private fun applyCoverAssist(coverUrl: String?) {
        if (coverUrl.isNullOrBlank()) {
            assistedCoverUrl = null
            return
        }
        assistedCoverUrl = coverUrl
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Cover URL", coverUrl))
        Toast.makeText(this, R.string.cover_url_copied, Toast.LENGTH_SHORT).show()
    }

    private fun doPost(
        url: String,
        prefs: SearchPrefs,
        vars: SecondarySearchVariables,
    ) {
        Thread {
            try {
                val body = SecondarySearchSubstitution.substitutePostBody(
                    prefs.postBody ?: """{"code":"%s"}""",
                    vars,
                )
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
