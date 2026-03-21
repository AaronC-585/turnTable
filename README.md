# turnTable — 1D Barcode Scanner (Android & iOS, C++)

Native **Android** and **iOS** apps that show the camera and scan **1D barcodes** (Code 128, EAN-13, EAN-8, UPC-A, UPC-E, Code 39, Code 93, Codabar, ITF). The decoding core is shared **C++** (ZXing-cpp), with platform UI and camera on each side. The Android app (turnTable) opens a **Home** screen after the splash (Redacted profile/stats or API-key prompt), then configurable **primary** (music-info APIs, e.g. MusicBrainz/Discogs) and **secondary** (e.g. tracker) search flows from the scanner, plus optional **Redacted** JSON API features (in-app torrent search and more) when you add your API key; see **CHANGELOG.md** and **`android/REDACTED_FEATURES.md`**.

## Structure

- **`cpp/`** — Shared C++ library (ZXing-cpp + thin wrapper). Builds for Android NDK and for iOS.
- **`android/`** — Android app (Kotlin, CameraX, JNI). Uses `cpp/` via NDK/CMake.
- **`ios/`** — iOS app (Swift, AVFoundation); Xcode project **`ios/turnTable.xcodeproj`**. Uses `cpp/` via a prebuilt static lib and bridging header.

### Build everything (from repo root)

```bash
make all          # C++ standalone lib + Android debug APK; on macOS also iOS static libs
make help         # list targets: cpp, android, android-release, ios, clean
```

The **iOS app binary** is built in **Xcode** (`open ios/turnTable.xcodeproj`); `make all` only produces the iOS **native library** on macOS.

### GitHub releases

Publish **Android APK** and **iOS IPA** on each release (attach both to the same tag). See **`RELEASE.md`** and **`scripts/release-github.sh`**.

**In-app update check (Android):** Set `github_update_owner` (and optionally `github_update_version_ref`, usually `main`) in **`android/app/src/main/res/values/github_update.xml`** to your GitHub user or org (same repo as releases). The app calls the public [latest release](https://docs.github.com/en/rest/releases/releases#get-the-latest-release) API when possible; if that fails (e.g. rate limit), it falls back to **`android/app/update-check-latest-version.txt`** via `raw.githubusercontent.com`. **`scripts/release-github.sh`** updates that file when you cut a release—commit and push it so the fallback stays in sync. Tags / version lines use the dotted form (e.g. `v2026.3.19.1373` on GitHub, `2026.3.19.1373` in the text file).

## Android

**Requirements:** Android Studio (or CLI), **NDK 28** (16 KB page-size–friendly libc++ and defaults), CMake 3.22+, **AGP 8.5+** (aligned JNI packaging).

**ABI / Play:** Native builds target **arm64-v8a** and **x86_64** (64-bit only). CMake uses `ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON` and `-Wl,-z,max-page-size=16384` on JNI/ZXing; APK uses uncompressed, page-aligned JNI (`packaging.jniLibs.useLegacyPackaging=false`, `extractNativeLibs=false`). See [Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes).

### Redacted API (optional)

If you use [Redacted](https://redacted.sh), generate an **API key** on the site (with the scopes you need) and paste it under **Settings → Redacted API key** (or when prompted on **Home**). The app sends it as the `Authorization` header to `https://redacted.sh/ajax.php` (per the site’s JSON API docs). **User** and **torrents** permissions are required for core Redacted features.

- **Home** shows your avatar and API `userstats` when a key is set; shortcuts include **Scan** and **Torrent search** (opens `RedactedBrowseActivity`, then `RedactedBrowseResultsActivity` after you search).
- **Scanner overflow (⋮) → Torrent search** opens the same two-step flow (search form, then results) when a key is set (no separate hub screen).
- On the **Search** screen, **Search Redacted** appears when a key is set; it opens in-app torrent search and can prefill from the artist/album field.

Implementation lives under `android/app/src/main/java/com/turntable/barcodescanner/` (`Redacted*Activity`, `redacted/RedactedApiClient.kt`). **OkHttp** is used for HTTP. Downloaded `.torrent` files are shared via **`FileProvider`** (`res/xml/file_paths.xml`). Multipart **upload** is not fully wired in the UI; the client exposes `postUpload` for extensions. See **`android/REDACTED_FEATURES.md`** for the full list of screens and API actions.

### qBittorrent Web API (future)

Planned / exploratory integration with **qBittorrent’s Web UI API** is documented in **`docs/qbittorrent-api-future.md`**. It points at the Python client **[qbittorrent-api](https://qbittorrent-api.readthedocs.io/en/latest/)** (`pip install qbittorrent-api`) as the reference implementation for scripts or a LAN companion; the app does not bundle Python. Optional pin file: **`docs/requirements-qbittorrent.txt`**.

1. Open the `android/` folder in Android Studio (or use it as the project root).
2. Sync Gradle and build. The app’s CMake build will pull and build the `cpp/` project (including ZXing-cpp) for the selected ABI.
3. Run on a device or emulator with a camera. Grant camera permission and point at a 1D barcode; the last decoded value appears at the bottom.

```bash
cd android
# Version is stamped every build: year.month.day.minutesSinceMidnight (see app/build.gradle).
./gradlew printAppVersion   # e.g. 2026.3.19.1213 (205311213)

./gradlew assembleDebug
# Debug APK: app/build/outputs/apk/debug/turnTable.debug-<version>.apk

./gradlew assembleRelease
# Release APK: app/build/outputs/apk/release/turnTable.release-<version>.apk
# (Release build type; use keystore.properties or ANDROID_KEYSTORE_* for store signing.)
```

## iOS

**Requirements:** **macOS**, **Xcode 14+**, **CMake 3.16+**, and the **iOS SDK** (bundled with Xcode). Full compiler / toolchain checklist: **`ios/BUILD.md`**.

1. **Build the C++ library** (device and simulator) from the **repo root** (requires **macOS** with Xcode; CMake uses the iOS toolchain):

   ```bash
   make ios
   # or: chmod +x ios/scripts/build_lib.sh && ./ios/scripts/build_lib.sh
   ```

   This builds `cpp/` (and ZXing-cpp) for iOS and puts:
   - `ios/build/Release-iphoneos/libbarcode_scanner_core.a`
   - `ios/build/Release-iphonesimulator/libbarcode_scanner_core.a`

2. **Open the Xcode project** and run the app:

   ```bash
   open ios/turnTable.xcodeproj
   ```

   In Xcode: choose a device or simulator, set your **Development Team** in Signing & Capabilities, then Run. The app follows the same flow as Android: **splash** → **onboarding** (first launch) → **Home** (shortcuts to scan, history, settings, Redacted browse/account/more when an API key is set), then **scanner** → **search** after a decode. Settings and search prefs use the same `UserDefaults` suite keys as Android’s `search_prefs` where applicable. The **App icon** uses the same foreground as Android (`drawable/ic_launcher.png`) with an opaque **#2B2B2B** fill (same as Android `home_card_bg`) in `ios/turnTable/Assets.xcassets`. After changing the Android icon, regenerate with **`./ios/scripts/sync_app_icon_from_android.sh`** (requires [Pillow](https://pypi.org/project/Pillow/): `pip install Pillow`).

If the iOS toolchain doesn’t find the right SDK, set `CMAKE_OSX_SYSROOT` in `ios/ios.toolchain.cmake` or use [ios-cmake](https://github.com/leetal/ios-cmake) and point the script at it.

## C++ core (standalone)

To build and use only the C++ barcode library (e.g. for tests or another host):

```bash
cd cpp
cmake -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build
```

The public API is in `cpp/include/barcode_scanner.h`: one function, `barcode_decode_grayscale()`, that takes a grayscale buffer and writes the first decoded 1D barcode into a char buffer.

## Web (optional)

The original browser-based scanner is still in the repo:

- **`barcode-scanner.html`** — Single-page 1D scanner (camera + Quagga2). Open in a browser or serve via a local server. See **`BARCODE-SCANNER.md`** for usage.
