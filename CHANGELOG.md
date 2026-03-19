# Changelog

All notable changes to the turnTable / 1D Barcode Scanner project are documented here.

## [Unreleased]

- **Android 16 KB page size & 64-bit:** Explicit `ndk.abiFilters` (**arm64-v8a**, **x86_64**, plus 32-bit **armeabi-v7a** / **x86**). `packaging.jniLibs.useLegacyPackaging = false`, `android:extractNativeLibs="false"` for aligned JNI loads. Existing NDK 28, `ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON`, and `-Wl,-z,max-page-size=16384` on `barcode_jni` / ZXing retained.
- **Music Metadata primary sources** (see [Soundcharts music API guide](https://soundcharts.com/en/blog/music-data-api)): **TheAudioDB** (`album-mb.php` by MusicBrainz release id; optional API key, defaults to public `1`) and **Last.fm** (`album.getinfobymbid`; API key required). Both reuse a **per-lookup cache** of the MusicBrainz barcode search so MB rate limits stay at one request when chaining MB-derived providers.
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
