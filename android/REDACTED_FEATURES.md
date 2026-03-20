# Redacted integration (Android) — kept features

The **hub / “API browser” screen** (`RedactedHubActivity`) was removed. All JSON API and feature screens below remain; primary entry for browsing is **in-app torrent search** (`RedactedBrowseActivity`) from Home, the scanner overflow menu, and Search.

## HTTP client — `redacted/RedactedApiClient.kt`

Sends `Authorization: <api key>` to `https://redacted.sh/ajax.php`. Notable actions include:

| Area | Actions (query `action=`) |
|------|-----------------------------|
| Session / user | `index`, `user`, `community_stats` |
| Torrents | `browse`, `torrentgroup`, `torrent`, `download`, `user_torrents`, `top10`, `better` |
| Tags / edits | `addtag`, `groupedit`, `torrentedit`, `addtocollage` |
| Artists | `artist`, `similar_artists` |
| Requests | `requests`, `request`, `requestfill` |
| Social | `inbox` (+ `viewconv`), `send_pm`, `usersearch`, `bookmarks`, `subscriptions` |
| Forums | `forum` (main / viewforum / viewthread) |
| Site | `notifications`, `announcements`, `wiki`, `logchecker`, `riplog`, `collage` |
| Upload | `postUpload` (multipart; client only) |

Helpers: `RedactedUiHelper`, `RedactedExtras`, `RedactedResult`, profile UI builders used by **Home**.

## Activities (still registered)

Reachable from **browse → group** chains, **Search → Search Redacted**, **Settings** (API key only), **Home** (profile + shortcuts), or **Main ⋮ → Torrent search** (formerly hub):

- `RedactedBrowseActivity` — torrent search / results  
- `RedactedTorrentGroupActivity`, `RedactedTorrentDetailActivity`  
- `RedactedAccountActivity`  
- `RedactedTop10Activity`, `RedactedBookmarksActivity`, `RedactedRequestsActivity`, `RedactedRequestDetailActivity`  
- `RedactedUserTorrentsActivity`  
- `RedactedInboxActivity`, `RedactedConversationActivity`, `RedactedSendPmActivity`  
- `RedactedForumMainActivity`, `RedactedForumThreadsActivity`, `RedactedForumThreadActivity`  
- `RedactedNotificationsActivity`, `RedactedAnnouncementsActivity`  
- `RedactedUserSearchActivity`, `RedactedUserProfileActivity`, `RedactedCommunityStatsActivity`  
- `RedactedWikiActivity`  
- `RedactedArtistActivity`, `RedactedSimilarArtistsActivity`  
- `RedactedCollageActivity`  
- `RedactedLogcheckerActivity`, `RedactedRipLogActivity`  
- `RedactedGroupEditActivity`, `RedactedTorrentEditActivity`, `RedactedAddToCollageActivity`, `RedactedUploadInfoActivity`  
- `RedactedSubscriptionsActivity`  

## Removed

- **`RedactedHubActivity`** — single menu of links to the above; no longer in the app.
