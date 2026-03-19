# 1D Barcode Scanner — Android & iOS (C++)

Native **Android** and **iOS** apps that show the camera and scan **1D barcodes** (Code 128, EAN-13, EAN-8, UPC-A, UPC-E, Code 39, Code 93, Codabar, ITF). The decoding core is shared **C++** (ZXing-cpp), with platform UI and camera on each side.

## Structure

- **`cpp/`** — Shared C++ library (ZXing-cpp + thin wrapper). Builds for Android NDK and for iOS.
- **`android/`** — Android app (Kotlin, CameraX, JNI). Uses `cpp/` via NDK/CMake.
- **`ios/`** — iOS app (Swift, AVFoundation). Uses `cpp/` via a prebuilt static lib and bridging header.

## Android

**Requirements:** Android Studio (or CLI), NDK, CMake 3.22+.

1. Open the `android/` folder in Android Studio (or use it as the project root).
2. Sync Gradle and build. The app’s CMake build will pull and build the `cpp/` project (including ZXing-cpp) for the selected ABI.
3. Run on a device or emulator with a camera. Grant camera permission and point at a 1D barcode; the last decoded value appears at the bottom.

```bash
cd android
./gradlew assembleDebug
# Install: adb install app/build/outputs/apk/debug/app-debug.apk
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
