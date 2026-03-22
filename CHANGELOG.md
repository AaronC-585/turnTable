# Changelog

All notable changes to the turnTable / 1D Barcode Scanner project are documented here.

## [2026.3.22.866] — Android production release

- **Settings:** Theme, secondary preset, and **Open in browser** use **expandable bullet lists** (`ExpandableBulletChoice`), matching Redacted browse / list screens. **`bindLabelList`** supports an optional **`onItemClick`** for preset → URL fill.
- **Redacted browse (advanced):** **Has cue**, **Scene**, and **Vanity house** each use **one boolean switch** (on → API `1`; off → no filter).
- **Torrent group / torrent detail:** Collapsible sections start **collapsed** by default (expand to read).
- **Album compilation info (torrent group):** **`wikiBody`** rendered with **`AppRichText`** (BBCode + HTML links); long-press **Compilation info** header copies **RTF** with hyperlinks (`RedactedBbCodeToRtf`, including **`htmlToRtfDocument`** for HTML). **`AppRichText`** detects broader BBCode (not only `[url]`).
- **Release:** APK only from Linux; upload iOS IPA when ready per **RELEASE.md**.

## [2026.3.22.133] — Android production release

- **Help text & links (Android):** Long settings/help strings use **`AppRichText`** — HTML (`<a href>`) and BBCode-style `[url]` where relevant, plus auto-linking of bare `http(s)://` URLs; **`LinkMovementMethod`** on `TextView`s and API-key dialog message. About, Donate, Settings, qBittorrent settings, Home no-key hint, and list editors updated.
- **Tracker status strip:** Tapping a status icon opens a **legend** dialog (green/red meaning, icon order); **no longer opens** the tracker status website in the browser.
- **Release:** APK only from Linux; upload iOS IPA when ready per **RELEASE.md**.

## [2026.3.22.111] — Android production release

- **Redacted torrent browse (advanced):** Multi-value filters (bitrate, format, media, release type, has log, leech status, order by / direction) use **expandable sections** with **bullet-prefixed** single-choice lists (`ExpandableBulletChoice`, `bindBulletFromResource`). **Has cue / Scene / Vanity house** use **Yes/No switches** (mutually exclusive; both off = no filter).
- **Redacted list screens:** **Top 10** (category + limit), **Bookmarks** (type), **My torrents** (list type) use the same **expandable bullet** pattern.
- **Release:** APK only from Linux; upload iOS IPA when ready per **RELEASE.md**.

## [2026.3.22.54] — Android production release

- **Tracker status strip (bottom dock):** Replaced PNG **up/down** pairs with **vector** icons (one shape per service). **Green** tint when service is up, **red** when down (`tracker_status_down`: `@color/app_error` in light; **#EF5350** in night for a clear red on dark bars).
- **Release:** APK only from Linux; upload iOS IPA when ready per **RELEASE.md**.

## [2026.3.22.32] — Android production release

- **UI — listboxes (Android):** Replaced **Spinner** dropdowns with in-place **single-choice ListViews** (scrollable listboxes) on Settings (theme, secondary preset, browser), secondary list editor, Redacted browse filters / order, Top 10 / Bookmarks / User torrents, and the torrent-group format picker dialog. Shared **`ListViewSingleChoice`** helper and listbox background drawable.
- **Primary APIs (Settings):** Removed **Add** and per-row **Remove**; list is fixed-size for reorder/toggle/edit only. **Enabled** label is **right-aligned** beside the switch.
- **Release:** APK only from Linux; upload iOS IPA when ready per **RELEASE.md**.

## [2026.3.22.11] — Android production release

- **Theming / forms (Android):** Shared `Widget.Redacted.*` styles for outlined TextInputs, Material buttons, spinner, switch; night theme parity. Bottom shortcut dock uses one solid bar color so icon tiles don’t show seams.
- **Primary API list (Settings):** Replaced spinners with a **drag-handle** reorderable list, **enable switch per row**, free-text API id + display name, **Add API**; removed “available APIs” dropdown.
- **Button icons:** Add (+), Remove (−), **Save** (floppy), **Cancel** (X) on edit flows and key actions.
- **Scan screen:** **Flashlight** toggle as an on-screen control on the camera preview; toolbar **overflow (⋯) removed** — Redacted opens from the toolbar with a compass icon alongside Home, History, Settings.
- **Release:** APK only from Linux; upload iOS IPA when ready: `gh release upload v2026.3.22.11 <file>.ipa` per **RELEASE.md**.

## [Unreleased]

- **Docs (future):** **`docs/qbittorrent-api-future.md`** — notes and links for integrating [qBittorrent Web API](https://qbittorrent-api.readthedocs.io/en/latest/) via the Python **`qbittorrent-api`** client (companion scripts / reference for a future Android or LAN flow). **`docs/requirements-qbittorrent.txt`** optional pip pin. README subsection **qBittorrent Web API (future)**.
- **About & Donate:** Settings includes **About** (app name, version, short description) and **Donate** (PayPal link [paypal.me/rcbaaron](https://paypal.me/rcbaaron?locale.x=en_US&country.x=US)) on Android (`AboutActivity`, `DonationActivity`) and iOS (`AboutViewController`, `DonationViewController`).
- **Redacted torrent search (Android):** Rebuilt **Torrents** browse to mirror the site **Basic / Advanced** filter box: search terms vs artist/album/label/catalogue/year/edition/file list/description; rip **bitrate / format / media / release type**; misc **log / cue / scene / vanity house / leech**; **tags** (any/all); **order** + **group by release**; **category** checkboxes; **Reset** + **Filter torrents**. Same `browse` JSON API with Gazelle-style query params. **Requests** and **User search** use a separate minimal layout (`activity_redacted_simple_list.xml`).
- **Barcode decode symbol compatibility (iOS):** Added a C wrapper `barcode_decode_greyscal` forwarding to `barcode_decode_grayscale` to tolerate legacy misspellings and prevent “cannot find … in scope” build failures.
- **Settings:** Removed Last.fm API key field, help text, and prefs (Android & iOS). Removed Last.fm as a primary music-info provider; **TheAudioDB** and other sources unchanged.
- **TheAudioDB (Android):** All requests use **v2** only with header **`X-API-KEY`** (same Settings value; default `123` if unset). Barcode primary lookup: **`GET /api/v2/json/lookup/album_mb/{releaseMbid}`**. Search screen text search: **`search/artist/{q}`** and **`search/album/{q}`**. No v1 URL path keys. UI: Search screen → TheAudioDB section + result picker fills secondary terms and cover assist.

## [2026.3.19.1373] — Android production release

- **Secondary browser list:** Android Settings keeps the full Play Store–oriented list (including Android-only browsers: Soul, Kiwi, Firefox Beta/Nightly, Via, Cake). iOS Settings uses a separate list: the same cross-platform browsers plus iOS-only **Arc Search** and **Orion Browser**; Android-only entries are omitted. **Samsung Internet** is not listed on iOS (no App Store product). Opening GET secondary links on iOS respects the chosen browser (URL schemes + `LSApplicationQueriesSchemes`).
- **Store IDs:** Corrected Play/App Store fallbacks for Opera, Opera GX, Vivaldi, UC Browser, Tor-related iOS (**Onion Browser**), Arc, Orion; documented in **`docs/browser-store-ids.md`**. Android: **`KnownBrowsers.kt`**. iOS: **`IosSecondaryBrowser.swift`**.
- **Settings:** Removed Discogs personal API token field and help text (Android & iOS). Discogs primary search still works **without** a token (anonymous rate limits).
- **Releases (policy):** GitHub releases should include **Android** (`turnTable.release-<version>.apk`) and **iOS** (`turnTable-ios-<version>.ipa`) as assets. See **`RELEASE.md`**, **`scripts/release-github.sh`**, and Cursor rule **releases-include-mobile-apps**. This tag ships **APK** from Linux; upload IPA from macOS with `gh release upload v2026.3.19.1373 turnTable-ios-<version>.ipa` when ready.

## [2026.3.19.1219] — Android production release

- **Versioning:** Each Gradle build sets `versionName` to `year.month.day.minutesSinceMidnight` and `versionCode` to `epochDay * 10000 + minutes` (monotonic, Play-safe). APKs: `turnTable.release-<version>.apk` / `turnTable.debug-<version>.apk`. Task: `./gradlew printAppVersion`.
- **Release:** Production (no beta filename); **release** build type for distribution (`assembleRelease`). Release signing: optional keystore; without it, build logs a warning and uses debug keystore for the release variant.

---

- **Home — Redacted notifications (Android):** While **Home** is visible, profile data **auto-refreshes every 60s** via `index` (notifications are **not** listed on the page; OS alerts only). If any notification field reads as active (**boolean true**, **count > 0**, or string **yes** / **1**), and the payload **changed** since last poll, the app posts an **OS notification** (tap / **Open site** → `https://redacted.sh/index.php`). **POST_NOTIFICATIONS** is requested once; snapshot is stored in **`SearchPrefs.lastRedactedNotificationsSnapshot`** to avoid duplicate alerts.
- **Home screen (Android):** After splash (and permission onboarding), **`HomeActivity`** is the app **home page**: with a Redacted API key, shows **avatar**, **username**, and **card sections** built from **`index`**, **`user`**, and **`community_stats`** (Statistics, Personal, Community, Next userclass when present, Percentile rankings, Donor, plus extra JSON objects). The **first two** sections (**Statistics**, **Personal** when present) appear **beside the avatar**; **remaining** sections are **collapsible** (closed until tapped). **Pull-to-refresh** reloads profile data. **Ratio** is highlighted (met vs not); **tokens/bonus** labels use accent color. **Shortcut** icon buttons (**Scan**, History, Stats, Settings, Redacted) sit below the profile. Without a key, a **dialog** requests the key (user + torrents permissions message); shortcuts still work.
- **Redacted JSON API (Android):** Optional **API key** in Settings (`SearchPrefs.redactedApiKey`), sent as **`Authorization`** to `https://redacted.sh/ajax.php`. **`RedactedApiClient`** (OkHttp) implements documented actions: `index`, `user`, `community_stats`, `browse`, `inbox` / conversation, `send_pm`, `usersearch`, `bookmarks`, `subscriptions`, `user_torrents`, `notifications`, `top10`, `artist`, `torrentgroup`, `torrent`, `riplog`, `download` (binary), `groupedit`, `addtag`, `torrentedit`, `collage`, `addtocollage`, `requests`, `request`, `requestfill`, `forum` (main / viewforum / viewthread), `wiki`, `similar_artists`, `announcements`, `logchecker`; **`postUpload`** for multipart extensions. Entry: **Home** / **scanner menu** / **Search** → in-app **torrent browse** (`RedactedBrowseActivity`); hub menu screen removed — see **`android/REDACTED_FEATURES.md`**. Torrent **browse → group** with cover, tags, group edit, **download/share .torrent** via **`FileProvider`**. Also: inbox/PM, forums, requests, bookmarks, top 10, user torrents, notifications, announcements, user search/profile/stats, wiki, logchecker, rip log, torrent/group edit forms, add-to-collage, upload info screen. **Entry:** main menu **Redacted**, Settings **Redacted API browser**, Search **Search Redacted** (when key set). Dependency: **OkHttp 4.12.0**.
- **Android 16 KB page size & 64-bit:** `ndk.abiFilters` **arm64-v8a** and **x86_64** only (64-bit). `packaging.jniLibs.useLegacyPackaging = false`, `android:extractNativeLibs="false"` for aligned JNI loads. NDK 28, `ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON`, and `-Wl,-z,max-page-size=16384` on `barcode_jni` / ZXing retained.
- **Music Metadata primary sources** (see [Soundcharts music API guide](https://soundcharts.com/en/blog/music-data-api)): **TheAudioDB** v2 `lookup/album_mb` by MusicBrainz release id with **`X-API-KEY`**. Reuses a **per-lookup cache** of the MusicBrainz barcode search so MB rate limits stay at one request when chaining MB-derived providers.
- **MusicBrainz API (primary search)** aligned with [MusicBrainz API](https://musicbrainz.org/doc/MusicBrainz_API): meaningful `User-Agent`, `Accept: application/json` with `fmt=json`, explicit `limit`/`offset` on release search, and **global throttling** so consecutive requests to `musicbrainz.org` are at least **1 second** apart (per rate-limiting rules).
- **Discogs API (primary search)** aligned with [developers.discogs.com](https://www.discogs.com/developers): RFC-style `User-Agent` (`AppName/version +URL`), `Accept: application/json`, optional personal token (`Authorization: Discogs token=…`), paginated database search + `GET /releases/{id}` for artist/title and images. Barcode-aware ordering when search results include barcode fields; verifies release `identifiers` when possible. Optional token field in Settings (password toggle).

---

## [Alpha] — 2025-03

### Added

- **Primary (1st) search — API list editor**
  - All available music-info APIs (e.g. MusicBrainz, Discogs) shown in a list.
  - Enable/disable per API; drag to reorder.
  - Search tries each enabled API in order until one returns a result, then uses that for secondary search.
  - No CLI editing of the primary list (UI only).

- **Secondary (2nd) search — dropdown editor**
  - Secondary search entries shown in a dropdown; selecting one allows editing **name** and **URL** (with `%s` for query) separately.
  - Add, remove, and reorder entries (Move up / Move down).
  - At least one entry required.

- **16 KB page-size compatibility (Android)**
  - NDK 28 and `-Wl,-z,max-page-size=16384` for native libs (barcode JNI, ZXing).
  - CameraX upgraded to 1.4.0 for 16 KB–aligned `libimage_processing_util_jni.so`.
  - Satisfies Google Play 16 KB requirement for 64-bit.

### Changed

- **Android build**
  - Debug APK renamed to `turnTable.debug.apk`; release to `turnTable.release.apk`.
  - `ndkVersion` set to `28.0.12433566`.

- **Settings**
  - Primary search: single “Edit primary list” button opens the new API list editor (no spinner).
  - Secondary search: “Edit secondary list” opens the new dropdown editor (no raw CLI screen).

### Removed

- CLI (raw text) editing for the primary search list.
- Single primary-API spinner in Settings; replaced by the primary API list editor.

---

## [1.0] — earlier

- 1D barcode scanning (Android & iOS) with shared C++ (ZXing-cpp).
- Android: CameraX, configurable primary (music-info) and secondary (e.g. tracker) search, GET/POST methods, optional beep on scan.
