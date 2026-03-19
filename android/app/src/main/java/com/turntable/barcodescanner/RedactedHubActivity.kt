package com.turntable.barcodescanner

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.turntable.barcodescanner.databinding.ActivityRedactedHubBinding
import com.turntable.barcodescanner.redacted.RedactedUiHelper
import com.turntable.barcodescanner.redacted.TwoLineRow
import com.turntable.barcodescanner.redacted.TwoLineRowsAdapter

/**
 * Entry hub for all Redacted JSON API screens. Requires API key in [SearchPrefs].
 */
class RedactedHubActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRedactedHubBinding
    private val intents = mutableListOf<Intent>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (RedactedUiHelper.requireApi(this) == null) return
        binding = ActivityRedactedHubBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        intents.clear()
        val rows = buildList {
            addRow("Account (index)", "Username, ratio, passkey, notifications") {
                Intent(this@RedactedHubActivity, RedactedAccountActivity::class.java)
            }
            addRow("Torrent search", "Browse / searchstr") {
                Intent(this@RedactedHubActivity, RedactedBrowseActivity::class.java)
            }
            addRow("Top 10", "torrents, tags, users") {
                Intent(this@RedactedHubActivity, RedactedTop10Activity::class.java)
            }
            addRow("Bookmarks", "torrents, artists, requests") {
                Intent(this@RedactedHubActivity, RedactedBookmarksActivity::class.java)
            }
            addRow("Requests", "Search and open request details") {
                Intent(this@RedactedHubActivity, RedactedRequestsActivity::class.java)
            }
            addRow("My torrents", "Seeding, leeching, uploaded, snatched") {
                Intent(this@RedactedHubActivity, RedactedUserTorrentsActivity::class.java)
            }
            addRow("Inbox", "PM conversations") {
                Intent(this@RedactedHubActivity, RedactedInboxActivity::class.java)
            }
            addRow("Send PM", "API key required") {
                Intent(this@RedactedHubActivity, RedactedSendPmActivity::class.java)
            }
            addRow("Forums", "Categories and threads") {
                Intent(this@RedactedHubActivity, RedactedForumMainActivity::class.java)
            }
            addRow("Notifications", "Torrent notifications") {
                Intent(this@RedactedHubActivity, RedactedNotificationsActivity::class.java)
            }
            addRow("Announcements", "Site news") {
                Intent(this@RedactedHubActivity, RedactedAnnouncementsActivity::class.java)
            }
            addRow("User search", "Find users") {
                Intent(this@RedactedHubActivity, RedactedUserSearchActivity::class.java)
            }
            addRow("User profile", "By user ID") {
                Intent(this@RedactedHubActivity, RedactedUserProfileActivity::class.java)
            }
            addRow("Community stats", "By user ID") {
                Intent(this@RedactedHubActivity, RedactedCommunityStatsActivity::class.java)
            }
            addRow("Collage", "By collage ID") {
                Intent(this@RedactedHubActivity, RedactedCollageActivity::class.java)
            }
            addRow("Wiki", "Article by name or ID") {
                Intent(this@RedactedHubActivity, RedactedWikiActivity::class.java)
            }
            addRow("Similar artists", "By artist ID") {
                Intent(this@RedactedHubActivity, RedactedSimilarArtistsActivity::class.java)
            }
            addRow("Artist", "Artist page by ID") {
                Intent(this@RedactedHubActivity, RedactedArtistActivity::class.java)
            }
            addRow("Subscriptions", "Artist subscriptions") {
                Intent(this@RedactedHubActivity, RedactedSubscriptionsActivity::class.java)
            }
            addRow("Logchecker", "Paste or check log text") {
                Intent(this@RedactedHubActivity, RedactedLogcheckerActivity::class.java)
            }
            addRow("Rip log", "Torrent log file by ID") {
                Intent(this@RedactedHubActivity, RedactedRipLogActivity::class.java)
            }
            addRow("Torrent detail", "Single torrent by ID") {
                Intent(this@RedactedHubActivity, RedactedTorrentDetailActivity::class.java)
            }
            addRow("Edit torrent", "POST torrentedit (API key)") {
                Intent(this@RedactedHubActivity, RedactedTorrentEditActivity::class.java)
            }
            addRow("Edit group", "POST groupedit (API key)") {
                Intent(this@RedactedHubActivity, RedactedGroupEditActivity::class.java)
            }
            addRow("Add to collage", "POST addtocollage") {
                Intent(this@RedactedHubActivity, RedactedAddToCollageActivity::class.java)
            }
            addRow("Upload (API note)", "Multipart upload per wiki") {
                Intent(this@RedactedHubActivity, RedactedUploadInfoActivity::class.java)
            }
        }

        val adapter = TwoLineRowsAdapter { pos ->
            startActivity(intents[pos])
        }
        adapter.rows = rows
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
    }

    private fun MutableList<TwoLineRow>.addRow(title: String, subtitle: String, intent: () -> Intent) {
        val i = intent()
        add(TwoLineRow(title, subtitle))
        intents.add(i)
    }
}
