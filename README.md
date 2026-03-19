# turnTable — 1D Barcode Scanner (Android & iOS, C++)

Native **Android** and **iOS** apps that show the camera and scan **1D barcodes** (Code 128, EAN-13, EAN-8, UPC-A, UPC-E, Code 39, Code 93, Codabar, ITF). The decoding core is shared **C++** (ZXing-cpp), with platform UI and camera on each side. The Android app (turnTable) opens a **Home** screen after the splash (Redacted profile/stats or API-key prompt), then configurable **primary** (music-info APIs, e.g. MusicBrainz/Discogs) and **secondary** (e.g. tracker) search flows from the scanner, plus an optional in-app **Redacted** JSON API browser when you add your API key; see **CHANGELOG.md** for features.

## Structure

- **`cpp/`** — Shared C++ library (ZXing-cpp + thin wrapper). Builds for Android NDK and for iOS.
- **`android/`** — Android app (Kotlin, CameraX, JNI). Uses `cpp/` via NDK/CMake.
- **`ios/`** — iOS app (Swift, AVFoundation). Uses `cpp/` via a prebuilt static lib and bridging header.

## Android

**Requirements:** Android Studio (or CLI), **NDK 28** (16 KB page-size–friendly libc++ and defaults), CMake 3.22+, **AGP 8.5+** (aligned JNI packaging).

**ABI / Play:** Native builds target **arm64-v8a** and **x86_64** (64-bit only). CMake uses `ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON` and `-Wl,-z,max-page-size=16384` on JNI/ZXing; APK uses uncompressed, page-aligned JNI (`packaging.jniLibs.useLegacyPackaging=false`, `extractNativeLibs=false`). See [Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes).

### Redacted API (optional)

If you use [Redacted](https://redacted.sh), generate an **API key** on the site (with the scopes you need) and paste it under **Settings → Redacted API key** (or when prompted on **Home**). The app sends it as the `Authorization` header to `https://redacted.sh/ajax.php` (per the site’s JSON API docs). **User** and **torrents** permissions are required for core Redacted features.

- **Home** shows your avatar and API `userstats` when a key is set; use the toolbar **Scan** camera button for barcode scanning.
- **Toolbar (⋮) → Redacted** or **Settings → Redacted API browser** opens the **hub** (account, torrent search, top 10, bookmarks, requests, inbox, forums, notifications, wiki, logchecker, edits, etc.).
- On the **Search** screen, **Search Redacted** appears when a key is set; it opens in-app torrent search and can prefill from the artist/album field.

Implementation lives under `android/app/src/main/java/com/turntable/barcodescanner/` (`Redacted*Activity`, `redacted/RedactedApiClient.kt`). **OkHttp** is used for HTTP. Downloaded `.torrent` files are shared via **`FileProvider`** (`res/xml/file_paths.xml`). Multipart **upload** is not fully wired in the UI; the client exposes `postUpload` for extensions—see the upload note in the hub.

1. Open the `android/` folder in Android Studio (or use it as the project root).
2. Sync Gradle and build. The app’s CMake build will pull and build the `cpp/` project (including ZXing-cpp) for the selected ABI.
3. Run on a device or emulator with a camera. Grant camera permission and point at a 1D barcode; the last decoded value appears at the bottom.

```bash
cd android
./gradlew assembleDebug
# Debug APK: app/build/outputs/apk/debug/turnTable.debug.apk
adb install -r app/build/outputs/apk/debug/turnTable.debug.apk

./gradlew assembleRelease
# Release APK: app/build/outputs/apk/release/turnTable.release.apk
```

## iOS

**Requirements:** Xcode 14+, CMake 3.16+, macOS.

1. **Build the C++ library** (device and simulator) from the **repo root**:

   ```bash
   chmod +x ios/scripts/build_lib.sh
   ./ios/scripts/build_lib.sh
   ```

   This builds `cpp/` (and ZXing-cpp) for iOS and puts:
   - `ios/build/Release-iphoneos/libbarcode_scanner_core.a`
   - `ios/build/Release-iphonesimulator/libbarcode_scanner_core.a`

2. **Open the Xcode project** and run the app:

   ```bash
   open ios/BarcodeScanner.xcodeproj
   ```

   In Xcode: choose a device or simulator, set your **Development Team** in Signing & Capabilities, then Run. The app shows the camera and displays the last scanned 1D barcode at the bottom.

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
