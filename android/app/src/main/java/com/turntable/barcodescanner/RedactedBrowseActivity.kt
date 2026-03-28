package com.turntable.barcodescanner

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.turntable.barcodescanner.databinding.ActivityRedactedBrowseBinding
import com.turntable.barcodescanner.redacted.RedactedBrowseParamsCodec
import com.turntable.barcodescanner.redacted.RedactedExtras
import com.turntable.barcodescanner.debug.AppEventLog
import com.turntable.barcodescanner.redacted.RedactedUiHelper

/**
 * First step of in-app torrent search: filter form only. Results open in [RedactedBrowseResultsActivity].
 */
class RedactedBrowseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRedactedBrowseBinding

    private lateinit var orderByValues: Array<String>
    private lateinit var orderWayValues: Array<String>
    private lateinit var encodingValues: Array<String>
    private lateinit var formatValues: Array<String>
    private lateinit var mediaValues: Array<String>
    private lateinit var releaseTypeValues: Array<String>
    private lateinit var hasLogValues: Array<String>
    private lateinit var freeTorrentValues: Array<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RedactedUiHelper.requireApi(this) ?: return
        binding = ActivityRedactedBrowseBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)

        binding.browseTabs.addTab(binding.browseTabs.newTab().setText(R.string.redacted_browse_header))
        binding.browseTabs.addTab(binding.browseTabs.newTab().setText(R.string.search_tab_collage))
        binding.browseTabs.getTabAt(0)?.select()
        val collageOrderByValues = resources.getStringArray(R.array.redacted_collages_order_by_values)
        val collageOrderWayValues = resources.getStringArray(R.array.redacted_collages_order_way_values)
        val collageForm = binding.panelCollageBrowse
        RedactedCollagesSearchForm.setupOrderChoices(collageForm, collageOrderByValues, collageOrderWayValues)
        collageForm.buttonSearch.setOnClickListener { openCollageSearchResults() }
        binding.browseTabs.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    syncBrowseTabVisibility(tab.position)
                }

                override fun onTabUnselected(tab: TabLayout.Tab) {}
                override fun onTabReselected(tab: TabLayout.Tab) {}
            },
        )
        syncBrowseTabVisibility(0)

        orderByValues = resources.getStringArray(R.array.redacted_browse_order_by_values)
        orderWayValues = resources.getStringArray(R.array.redacted_browse_order_way_values)
        encodingValues = resources.getStringArray(R.array.redacted_browse_encoding_values)
        formatValues = resources.getStringArray(R.array.redacted_browse_format_values)
        mediaValues = resources.getStringArray(R.array.redacted_browse_media_values)
        releaseTypeValues = resources.getStringArray(R.array.redacted_browse_release_type_values)
        hasLogValues = resources.getStringArray(R.array.redacted_browse_haslog_values)
        freeTorrentValues = resources.getStringArray(R.array.redacted_browse_freetorrent_values)

        bindExpandableBrowseFilters()

        binding.toggleSearchMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val advanced = checkedId == R.id.btnModeAdvanced
            binding.panelBasic.visibility = if (advanced) View.GONE else View.VISIBLE
            binding.panelAdvanced.visibility = if (advanced) View.VISIBLE else View.GONE
        }

        val advArtist = intent.getStringExtra(RedactedExtras.BROWSE_ADVANCED_ARTIST_NAME)?.trim().orEmpty()
        val advFilelist = intent.getStringExtra(RedactedExtras.BROWSE_ADVANCED_FILELIST)?.trim().orEmpty()
        val hasAdvPrefill = advArtist.isNotEmpty() || advFilelist.isNotEmpty()

        if (hasAdvPrefill) {
            binding.toggleSearchMode.check(R.id.btnModeAdvanced)
            binding.panelBasic.visibility = View.GONE
            binding.panelAdvanced.visibility = View.VISIBLE
            if (advArtist.isNotEmpty()) binding.editArtistName.setText(advArtist)
            if (advFilelist.isNotEmpty()) binding.editFileList.setText(advFilelist)
        } else {
            val initial = intent.getStringExtra(RedactedExtras.INITIAL_QUERY).orEmpty()
            if (initial.isNotBlank()) {
                binding.editSearchStr.setText(initial)
                applyInitialQueryToAdvancedFields(initial)
            }
        }

        binding.buttonSearch.setOnClickListener { openResults() }
        binding.buttonReset.setOnClickListener { resetForm() }

        if (hasAdvPrefill && intent.getBooleanExtra(RedactedExtras.BROWSE_AUTO_SUBMIT_RESULTS, true)) {
            binding.root.post { openResults() }
        } else if (!hasAdvPrefill) {
            val initial = intent.getStringExtra(RedactedExtras.INITIAL_QUERY).orEmpty()
            if (initial.isNotBlank()) {
                binding.root.post { openResults() }
            }
        }
    }

    private fun syncBrowseTabVisibility(position: Int) {
        binding.panelTorrentBrowse.visibility = if (position == 0) View.VISIBLE else View.GONE
        binding.panelCollageBrowse.root.visibility = if (position == 1) View.VISIBLE else View.GONE
        if (position == 1) {
            applyBrowseTermsToCollageSearchIfEmpty()
        }
    }

    /** Prefill collage search field from torrent basic/advanced fields when user opens the Collage tab. */
    private fun collagePrefillFromBrowse(): String {
        val fromBasic = binding.editSearchStr.text?.toString()?.trim().orEmpty()
        if (fromBasic.isNotEmpty()) return fromBasic
        val artist = binding.editArtistName.text?.toString()?.trim().orEmpty()
        val group = binding.editGroupName.text?.toString()?.trim().orEmpty()
        return when {
            artist.isNotEmpty() && group.isNotEmpty() -> "$artist $group"
            artist.isNotEmpty() -> artist
            group.isNotEmpty() -> group
            else -> ""
        }
    }

    private fun applyBrowseTermsToCollageSearchIfEmpty() {
        val collageTerms = binding.panelCollageBrowse.editSearch.text?.toString()?.trim().orEmpty()
        if (collageTerms.isNotEmpty()) return
        val initial = collagePrefillFromBrowse()
        if (initial.isNotEmpty()) {
            binding.panelCollageBrowse.editSearch.setText(initial)
        }
    }

    private fun openCollageSearchResults() {
        val form = binding.panelCollageBrowse
        val json = RedactedBrowseParamsCodec.encode(RedactedCollagesSearchForm.buildParams(form, page = 1))
        AppEventLog.log(AppEventLog.Category.REDACTED, "browse (Collage tab) collages search (params length=${json.length})")
        startActivity(
            Intent(this, RedactedCollagesSearchResultsActivity::class.java)
                .putExtra(RedactedExtras.COLLAGES_SEARCH_PARAMS_JSON, json),
        )
    }

    private fun bindExpandableBrowseFilters() {
        ExpandableBulletChoice.bindFromArray(binding.expandEncoding, null, R.array.redacted_browse_encoding, 0)
        ExpandableBulletChoice.bindFromArray(binding.expandFormat, null, R.array.redacted_browse_format, 0)
        ExpandableBulletChoice.bindFromArray(binding.expandMedia, null, R.array.redacted_browse_media, 0)
        ExpandableBulletChoice.bindFromArray(binding.expandReleaseType, null, R.array.redacted_browse_release_type, 0)
        ExpandableBulletChoice.bindFromArray(binding.expandHasLog, null, R.array.redacted_browse_haslog, 0)
        ExpandableBulletChoice.bindFromArray(binding.expandFreeTorrent, null, R.array.redacted_browse_freetorrent, 0)
        ExpandableBulletChoice.bindFromArray(
            binding.expandOrderBy,
            null,
            R.array.redacted_browse_order_by,
            1.coerceAtMost(orderByValues.size - 1),
        )
        ExpandableBulletChoice.bindFromArray(
            binding.expandOrderWay,
            null,
            R.array.redacted_browse_order_way,
            1.coerceAtMost(orderWayValues.size - 1),
        )
    }

    private fun openResults() {
        val json = RedactedBrowseParamsCodec.encode(buildBrowseParams(page = 1))
        AppEventLog.log(AppEventLog.Category.REDACTED, "browse search (params length=${json.length})")
        startActivity(
            Intent(this, RedactedBrowseResultsActivity::class.java)
                .putExtra(RedactedExtras.BROWSE_PARAMS_JSON, json),
        )
    }

    /**
     * Fills advanced fields when opening with [RedactedExtras.INITIAL_QUERY] (e.g. from Search).
     * Splits on `" - "` like RedactedSearchAssist secondary terms when present.
     */
    private fun applyInitialQueryToAdvancedFields(initial: String) {
        val s = initial.trim()
        if (s.isEmpty()) return
        val sep = " - "
        val idx = s.indexOf(sep)
        if (idx >= 0) {
            binding.editArtistName.setText(s.substring(0, idx).trim())
            binding.editGroupName.setText(s.substring(idx + sep.length).trim())
        } else {
            binding.editArtistName.setText(s)
        }
    }

    private fun resetForm() {
        binding.editSearchStr.text?.clear()
        binding.editArtistName.text?.clear()
        binding.editGroupName.text?.clear()
        binding.editRecordLabel.text?.clear()
        binding.editCatalogueNumber.text?.clear()
        binding.editYear.text?.clear()
        binding.editRemasterTitle.text?.clear()
        binding.editRemasterYear.text?.clear()
        binding.editFileList.text?.clear()
        binding.editTorrentDescription.text?.clear()
        binding.editTaglist.text?.clear()
        binding.radioTagsAny.isChecked = true
        binding.switchGroupResults.isChecked = false
        binding.checkCat1.isChecked = false
        binding.checkCat2.isChecked = false
        binding.checkCat3.isChecked = false
        binding.checkCat4.isChecked = false
        binding.checkCat5.isChecked = false
        binding.checkCat6.isChecked = false
        binding.checkCat7.isChecked = false
        binding.toggleSearchMode.check(R.id.btnModeAdvanced)
        binding.switchHasCue.isChecked = false
        binding.switchScene.isChecked = false
        binding.switchVanityHouse.isChecked = false
        bindExpandableBrowseFilters()
    }

    private fun MutableList<Pair<String, String?>>.putNonBlank(key: String, edit: TextInputEditText) {
        val v = edit.text?.toString()?.trim()
        if (!v.isNullOrEmpty()) add(key to v)
    }

    private fun buildBrowseParams(page: Int): List<Pair<String, String?>> = buildList {
        add("page" to page.toString())
        when (binding.toggleSearchMode.checkedButtonId) {
            R.id.btnModeBasic -> {
                val s = binding.editSearchStr.text?.toString()?.trim()
                if (!s.isNullOrEmpty()) add("searchstr" to s)
            }
            R.id.btnModeAdvanced -> {
                add("searchsubmit" to "1")
                putNonBlank("artistname", binding.editArtistName)
                putNonBlank("groupname", binding.editGroupName)
                putNonBlank("recordlabel", binding.editRecordLabel)
                putNonBlank("cataloguenumber", binding.editCatalogueNumber)
                putNonBlank("year", binding.editYear)
                putNonBlank("remastertitle", binding.editRemasterTitle)
                putNonBlank("remasteryear", binding.editRemasterYear)
                putNonBlank("filelist", binding.editFileList)
                putNonBlank("description", binding.editTorrentDescription)
                binding.expandEncoding.listExpandChoices.apiValue(encodingValues)?.let { add("encoding" to it) }
                binding.expandFormat.listExpandChoices.apiValue(formatValues)?.let { add("format" to it) }
                binding.expandMedia.listExpandChoices.apiValue(mediaValues)?.let { add("media" to it) }
                binding.expandReleaseType.listExpandChoices.apiValue(releaseTypeValues)?.let { add("releasetype" to it) }
                binding.expandHasLog.listExpandChoices.apiValue(hasLogValues)?.let { add("haslog" to it) }
                if (binding.switchHasCue.isChecked) add("hascue" to "1")
                if (binding.switchScene.isChecked) add("scene" to "1")
                if (binding.switchVanityHouse.isChecked) add("vanityhouse" to "1")
                binding.expandFreeTorrent.listExpandChoices.apiValue(freeTorrentValues)?.let { add("freetorrent" to it) }
            }
        }

        putNonBlank("taglist", binding.editTaglist)
        val tags = binding.editTaglist.text?.toString()?.trim()
        if (!tags.isNullOrEmpty()) {
            add("tags_type" to if (binding.radioTagsAll.isChecked) "1" else "0")
        }

        binding.expandOrderBy.listExpandChoices.apiValue(orderByValues)?.let { add("order_by" to it) }
        binding.expandOrderWay.listExpandChoices.apiValue(orderWayValues)?.let { add("order_way" to it) }

        if (binding.switchGroupResults.isChecked) add("group_results" to "1")

        val checks = listOf(
            binding.checkCat1 to "filter_cat[1]",
            binding.checkCat2 to "filter_cat[2]",
            binding.checkCat3 to "filter_cat[3]",
            binding.checkCat4 to "filter_cat[4]",
            binding.checkCat5 to "filter_cat[5]",
            binding.checkCat6 to "filter_cat[6]",
            binding.checkCat7 to "filter_cat[7]",
        )
        for ((cb, key) in checks) {
            if (cb.isChecked) add(key to "1")
        }
    }
}
