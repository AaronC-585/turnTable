package com.turntable.barcodescanner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayout
import com.turntable.barcodescanner.databinding.ActivitySearchBinding
import com.turntable.barcodescanner.debug.AppEventLog
import com.turntable.barcodescanner.debug.OutgoingUrlLog
import com.turntable.barcodescanner.redacted.RedactedBrowseParamsCodec
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
        if (hasRedactedEarly) {
            val orderByValues = resources.getStringArray(R.array.redacted_collages_order_by_values)
            val orderWayValues = resources.getStringArray(R.array.redacted_collages_order_way_values)
            val collageForm = binding.panelCollageSearch
            RedactedCollagesSearchForm.setupOrderChoices(collageForm, orderByValues, orderWayValues)
            if (prefillTerms.isNotBlank()) {
                collageForm.editSearch.setText(prefillTerms)
            }
            collageForm.buttonSearch.setOnClickListener { openCollageSearchResults() }
            binding.searchTabs.visibility = View.VISIBLE
            binding.searchTabs.addTab(binding.searchTabs.newTab().setText(R.string.search_pane_title))
            binding.searchTabs.addTab(binding.searchTabs.newTab().setText(R.string.search_tab_collage_disabled))
            binding.searchTabs.addOnTabSelectedListener(
                object : TabLayout.OnTabSelectedListener {
                    override fun onTabSelected(tab: TabLayout.Tab) {
                        if (tab.position == 1) {
                            binding.searchTabs.selectTab(binding.searchTabs.getTabAt(0), true)
                            return
                        }
                        syncSearchTabVisibility(tab.position)
                    }

                    override fun onTabUnselected(tab: TabLayout.Tab) {}
                    override fun onTabReselected(tab: TabLayout.Tab) {}
                },
            )
            binding.searchTabs.post { disableSearchCollageTabView() }
            syncSearchTabVisibility(binding.searchTabs.selectedTabPosition.coerceAtLeast(0))
        }
        binding.buttonRedactedSearch.setOnClickListener {
            val q = binding.editSecondarySearchTerms.text?.toString()?.trim().orEmpty()
            AppEventLog.log(AppEventLog.Category.REDACTED, "Search screen → Redacted browse${if (q.isNotBlank()) " query=\"$q\"" else ""}")
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

    private fun disableSearchCollageTabView() {
        val strip = binding.searchTabs.getChildAt(0) as? ViewGroup ?: return
        if (strip.childCount <= 1) return
        val collageTabView = strip.getChildAt(1)
        collageTabView.isEnabled = false
        collageTabView.isClickable = false
        collageTabView.alpha = 0.38f
        collageTabView.contentDescription = getString(R.string.search_tab_collage_disabled)
    }

    private fun syncSearchTabVisibility(position: Int) {
        val showCollage = position == 1
        binding.panelSearchMain.visibility = if (!showCollage) View.VISIBLE else View.GONE
        binding.panelCollageSearch.root.visibility = if (showCollage) View.VISIBLE else View.GONE
        if (showCollage) {
            val sec = binding.editSecondarySearchTerms.text?.toString()?.trim().orEmpty()
            val collageTerms = binding.panelCollageSearch.editSearch.text?.toString()?.trim().orEmpty()
            if (sec.isNotEmpty() && collageTerms.isEmpty()) {
                binding.panelCollageSearch.editSearch.setText(sec)
            }
        }
    }

    private fun openCollageSearchResults() {
        val form = binding.panelCollageSearch
        val json = RedactedBrowseParamsCodec.encode(RedactedCollagesSearchForm.buildParams(form, page = 1))
        AppEventLog.log(AppEventLog.Category.REDACTED, "collages search from SearchActivity (params length=${json.length})")
        startActivity(
            Intent(this, RedactedCollagesSearchResultsActivity::class.java)
                .putExtra(RedactedExtras.COLLAGES_SEARCH_PARAMS_JSON, json),
        )
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
                    AppEventLog.log(AppEventLog.Category.REDACTED, "browse assist hit q=\"$q\" → terms=\"${hit.secondaryTerms}\"")
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
                        AppEventLog.log(AppEventLog.Category.REDACTED, "browse assist no hit q=\"$q\" (quiet)")
                        return@runOnUiThread
                    }
                    AppEventLog.log(AppEventLog.Category.REDACTED, "browse assist no hit q=\"$q\"")
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
        AppEventLog.log(
            AppEventLog.Category.SEARCH,
            "submit method=${prefs.method} barcode=${binding.editBarcode.text?.toString()?.trim().orEmpty().ifBlank { barcode }}",
        )

        when (prefs.method) {
            SearchPrefs.METHOD_GET -> {
                val secondaryUrl = prefs.secondarySearchUrl?.takeIf { it.isNotBlank() }
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
                    fetchPrimaryInfoAndOpenSecondary(barcode, secondaryUrl, prefs)
                } else if (!secondaryQuery.isNullOrBlank()) {
                    if (preferRedactedOverBrowser()) {
                        fillFromRedactedBrowse(barcode, secondaryQuery, coverFromUi(), quietIfNoHit = false)
                    } else {
                        openSecondaryUrl(secondaryUrl, secondaryVarsFromUi(barcode))
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

    private fun openSecondaryUrl(
        secondaryUrl: String,
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
        BrowserLaunch.openHttpUrl(this, url) { finish() }
    }

    private fun fetchPrimaryInfoAndOpenSecondary(
        barcode: String,
        secondaryUrl: String,
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
                        secondaryVarsFromUi(
                            barcode = barcode,
                            query = query,
                            coverUrl = coverUrl,
                        ),
                    )
                } else {
                    val manual = binding.editSecondarySearchTerms.text?.toString()?.trim()
                    if (!manual.isNullOrBlank()) {
                        openSecondaryUrl(secondaryUrl, secondaryVarsFromUi(barcode, query = manual))
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

                OutgoingUrlLog.log("POST", url)
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", contentType)
                headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                runOnUiThread {
                    AppEventLog.log(AppEventLog.Category.SEARCH, "POST secondary HTTP $code")
                    Toast.makeText(
                        this,
                        if (code in 200..299) "Sent ($code)" else "Response: $code",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    AppEventLog.log(AppEventLog.Category.ERROR, "POST secondary failed: ${e.message}")
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
