# Redacted integration (Android) — kept features

The **hub / “API browser” screen** (`RedactedHubActivity`) was removed. All JSON API and feature screens below remain; primary entry for browsing is **in-app torrent search** (`RedactedBrowseActivity`) from Home, the scanner overflow menu, and Search. **Forums** also have a **bottom dock** shortcut (cheerleader-horn icon) next to News/Mail, opening `RedactedForumMainActivity`.

## HTTP client — `redacted/RedactedApiClient.kt`

Sends `Authorization: <api key>` to `https://redacted.sh/ajax.php`. Notable actions include:

| Area | Actions (query `action=`) |
|------|-----------------------------|
| Session / user | `index`, `user`, `community_stats` |
| Torrents | `browse`, `torrentgroup`, `torrent`, `download`, `user_torrents`, `top10`, `better` |
| Tags / edits | `addtag`, `groupedit`, `torrentedit`, `addtocollage` |
| Artists | `artist`, `similar_artists` |
| Requests | `requests`, `request`, `requestfill` |
| Social | `inbox` (tabs: default inbox, `sentbox`, `staffpm`), `viewconv`, `send_pm`, `usersearch`, `bookmarks`, `subscriptions` |
| Forums | `forum` (main / viewforum / viewthread); **POST** `forum&type=takepost&threadid=` + form `body` for replies (Redacted extension; falls back to **Open thread in browser** if unsupported) |
| Site | `notifications`, `announcements`, `wiki`, `logchecker`, `riplog`, `collage` |
| Upload | `postUpload` (multipart; client only) |

Helpers: `RedactedUiHelper`, `RedactedExtras`, `RedactedResult`, profile UI builders used by **Home**.

## Activities (still registered)

Reachable from **browse → group** chains, **Search → Search Redacted**, **Settings** (API key only), **Home** (profile + shortcuts), or **Main ⋮ → Torrent search** (formerly hub):

- `RedactedBrowseActivity` — torrent **search form** (Basic + Advanced filters). **`RedactedBrowseResultsActivity`** — **results** list + paging (receives encoded `browse` params). `RedactedRequestsActivity` / `RedactedUserSearchActivity` use `activity_redacted_simple_list.xml` instead.  
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

## Test event log (app-wide)

- **`DebugEventLogActivity`** — in-memory colored log (timestamps, SCAN / SEARCH / REDACTED / SYSTEM / ERROR). **Shortcut:** press **Volume Up**, then within a few seconds **shake** the device (with the app in foreground). **Save / share:** toolbar writes UTF-8 text under app cache `event_logs/` and opens the system share sheet; **Copy all** copies plain text. Implementation: `debug/AppEventLog.kt`, `debug/DebugShortcutCoordinator.kt`.
- **`CrashReporter`** — `Thread.setDefaultUncaughtExceptionHandler`: on crash, writes stack trace to private `files/turnTable_last_crash.txt`, logs a short **ERROR** line, then chains the previous handler. Next cold start, `TurnTableApp` ingests that file into **AppEventLog** as **ERROR** (then deletes the file).
- **`OutgoingUrlLog` / `OutgoingUrlInterceptor`** — every outgoing **OkHttp** request and **`HttpURLConnection` / `VIEW`** open logs **SYSTEM** lines like `GET https://…` or `POST …` or `VIEW …`. Response bodies are **not** written to the event log (API error toasts may still show snippets).

## Removed

- **`RedactedHubActivity`** — single menu of links to the above; no longer in the app.
