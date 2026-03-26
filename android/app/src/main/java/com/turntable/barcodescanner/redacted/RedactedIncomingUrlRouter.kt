package com.turntable.barcodescanner.redacted

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.turntable.barcodescanner.HomeActivity
import com.turntable.barcodescanner.RedactedAnnouncementsActivity
import com.turntable.barcodescanner.RedactedArtistActivity
import com.turntable.barcodescanner.RedactedBookmarksActivity
import com.turntable.barcodescanner.RedactedBrowseActivity
import com.turntable.barcodescanner.RedactedCollageActivity
import com.turntable.barcodescanner.RedactedCollagesSearchActivity
import com.turntable.barcodescanner.RedactedForumMainActivity
import com.turntable.barcodescanner.RedactedForumThreadActivity
import com.turntable.barcodescanner.RedactedForumThreadsActivity
import com.turntable.barcodescanner.RedactedInboxActivity
import com.turntable.barcodescanner.RedactedNotificationsActivity
import com.turntable.barcodescanner.RedactedTop10Activity
import com.turntable.barcodescanner.RedactedRequestDetailActivity
import com.turntable.barcodescanner.RedactedRequestsSearchActivity
import com.turntable.barcodescanner.RedactedSubscriptionsActivity
import com.turntable.barcodescanner.RedactedTorrentDetailActivity
import com.turntable.barcodescanner.RedactedTorrentGroupActivity
import com.turntable.barcodescanner.RedactedUserProfileActivity
import com.turntable.barcodescanner.RedactedWikiActivity
import java.util.Locale

/**
 * Maps `redacted.sh` links (opened via VIEW / share, or tapped in rich text) to in-app screens.
 */
object RedactedIncomingUrlRouter {

    private val redactedHosts = setOf("redacted.sh", "www.redacted.sh")

    private val redactedUrlInText = Regex(
        """https?://(?:www\.)?redacted\.sh[^\s<>"'{}|\\^`\[\]]*""",
        RegexOption.IGNORE_CASE,
    )

    fun isRedactedSiteUrl(url: String?): Boolean {
        val uri = parseUri(url) ?: return false
        return uri.host?.lowercase(Locale.ROOT) in redactedHosts
    }

    /**
     * Starts the matching screen. Returns false if [raw] is not a usable Redacted URL.
     */
    fun startFromUrlString(context: Context, raw: String?): Boolean {
        val uri = parseUri(raw) ?: return false
        return startFromUri(context, uri)
    }

    /** First Redacted URL in shared plain text. */
    fun startFromSharedText(context: Context, text: String?): Boolean {
        val m = redactedUrlInText.find(text.orEmpty()) ?: return false
        return startFromUrlString(context, m.value)
    }

    fun startFromUri(context: Context, uri: Uri): Boolean {
        val i = buildIntent(context, uri) ?: return false
        if (context !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
        return true
    }

    /**
     * Target activity for this Redacted URL, or null if [uri] is not on this site.
     */
    fun buildIntent(context: Context, uri: Uri): Intent? {
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (host !in redactedHosts) return null

        val path = uri.path ?: "/"
        val file = path.trim('/').substringAfterLast('/').lowercase(Locale.ROOT)

        intentForUserPhp(context, uri)?.let { return it }

        if (file == "torrents.php" || path.contains("torrents.php", ignoreCase = true)) {
            val torrentId = uri.getQueryParameter("torrentid")?.toIntOrNull()
                ?: uri.getQueryParameter("torrent_id")?.toIntOrNull()
            if (torrentId != null && torrentId > 0) {
                return Intent(context, RedactedTorrentDetailActivity::class.java)
                    .putExtra(RedactedExtras.TORRENT_ID, torrentId)
            }
            val groupId = uri.getQueryParameter("groupid")?.toIntOrNull()
                ?: uri.getQueryParameter("id")?.toIntOrNull()
            if (groupId != null && groupId > 0) {
                return Intent(context, RedactedTorrentGroupActivity::class.java)
                    .putExtra(RedactedExtras.GROUP_ID, groupId)
            }
            return Intent(context, RedactedBrowseActivity::class.java)
        }

        if (file == "artist.php" || path.contains("artist.php", ignoreCase = true)) {
            val artistId = uri.getQueryParameter("id")?.toIntOrNull()
            if (artistId != null && artistId > 0) {
                return Intent(context, RedactedArtistActivity::class.java)
                    .putExtra(RedactedExtras.ARTIST_ID, artistId)
            }
            val artistName = uri.getQueryParameter("artistname")?.trim().orEmpty()
            if (artistName.isNotEmpty()) {
                return Intent(context, RedactedBrowseActivity::class.java)
                    .putExtra(RedactedExtras.BROWSE_ADVANCED_ARTIST_NAME, artistName)
                    .putExtra(RedactedExtras.BROWSE_AUTO_SUBMIT_RESULTS, true)
            }
            return Intent(context, RedactedBrowseActivity::class.java)
        }

        if (file == "requests.php" || path.contains("requests.php", ignoreCase = true)) {
            val requestId = uri.getQueryParameter("id")?.toIntOrNull()
                ?: uri.getQueryParameter("requestid")?.toIntOrNull()
            if (requestId != null && requestId > 0) {
                return Intent(context, RedactedRequestDetailActivity::class.java)
                    .putExtra(RedactedExtras.REQUEST_ID, requestId)
            }
            return Intent(context, RedactedRequestsSearchActivity::class.java)
        }

        if (file == "forums.php" || file == "forum.php" ||
            path.contains("forums.php", ignoreCase = true)
        ) {
            val threadId = uri.getQueryParameter("threadid")?.toIntOrNull()
                ?: uri.getQueryParameter("thread_id")?.toIntOrNull()
            if (threadId != null && threadId > 0) {
                return Intent(context, RedactedForumThreadActivity::class.java)
                    .putExtra(RedactedExtras.THREAD_ID, threadId)
            }
            val forumId = uri.getQueryParameter("forumid")?.toIntOrNull()
                ?: uri.getQueryParameter("forum_id")?.toIntOrNull()
            if (forumId != null && forumId > 0) {
                return Intent(context, RedactedForumThreadsActivity::class.java)
                    .putExtra(RedactedExtras.FORUM_ID, forumId)
            }
            return Intent(context, RedactedForumMainActivity::class.java)
        }

        if (file == "collages.php" || file == "collage.php" ||
            path.contains("collages.php", ignoreCase = true) ||
            path.contains("collage.php", ignoreCase = true)
        ) {
            val collageId = uri.getQueryParameter("id")?.toIntOrNull()
            if (collageId != null && collageId > 0) {
                return Intent(context, RedactedCollageActivity::class.java)
                    .putExtra(RedactedExtras.COLLAGE_ID, collageId)
            }
            return Intent(context, RedactedCollagesSearchActivity::class.java)
        }

        if (file == "inbox.php" || path.contains("inbox.php", ignoreCase = true) ||
            file == "staffpm.php" || path.contains("staffpm.php", ignoreCase = true)
        ) {
            return Intent(context, RedactedInboxActivity::class.java)
        }

        if (file == "notifications.php" || path.contains("notifications.php", ignoreCase = true)) {
            return Intent(context, RedactedNotificationsActivity::class.java)
        }

        if (file == "announcements.php" || path.contains("announcements.php", ignoreCase = true)) {
            return Intent(context, RedactedAnnouncementsActivity::class.java)
        }

        if (file == "subscriptions.php" || path.contains("subscriptions.php", ignoreCase = true)) {
            return Intent(context, RedactedSubscriptionsActivity::class.java)
        }

        if (file == "bookmarks.php" || path.contains("bookmarks.php", ignoreCase = true)) {
            return Intent(context, RedactedBookmarksActivity::class.java)
        }

        if (file == "top10.php" || path.contains("top10.php", ignoreCase = true)) {
            return Intent(context, RedactedTop10Activity::class.java)
        }

        if (file == "wiki.php" || path.contains("wiki.php", ignoreCase = true)) {
            return Intent(context, RedactedWikiActivity::class.java)
        }

        return Intent(context, HomeActivity::class.java)
    }

    private fun intentForUserPhp(context: Context, uri: Uri): Intent? {
        val path = uri.path ?: ""
        if (!path.endsWith("user.php", ignoreCase = true)) return null

        val idParam = uri.getQueryParameter("id")?.toIntOrNull()
        if (idParam != null && idParam > 0) {
            return Intent(context, RedactedUserProfileActivity::class.java)
                .putExtra(RedactedExtras.USER_ID, idParam)
        }
        if (!uri.getQueryParameter("action").equals("search", ignoreCase = true)) return null
        val username = uri.getQueryParameter("username")?.trim()
            ?: uri.getQueryParameter("search")?.trim()
        if (username.isNullOrEmpty()) return null
        return Intent(context, RedactedUserProfileActivity::class.java)
            .putExtra(RedactedExtras.USERNAME, username)
    }

    private fun parseUri(raw: String?): Uri? {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return null
        val abs = when {
            t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true) -> t
            t.startsWith("//") -> "https:$t"
            else -> "https://redacted.sh/${t.trimStart('/')}"
        }
        return try {
            Uri.parse(abs)
        } catch (_: Exception) {
            null
        }
    }
}
