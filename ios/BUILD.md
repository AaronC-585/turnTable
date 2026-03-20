# iOS — compiler prerequisites

You **cannot** compile the iOS app or its native C++ library on Linux or Windows. Apple only distributes the iOS SDK and Swift toolchain as part of **Xcode on macOS**.

---

## Required

| Prerequisite | Notes |
|--------------|--------|
| **macOS** | Current or recent macOS that your Xcode version supports. |
| **Xcode** | **14 or newer** (matches `IPHONEOS_DEPLOYMENT_TARGET` 14.0 and project format). Install from the Mac App Store or [developer.apple.com](https://developer.apple.com/xcode/). |
| **Xcode Command Line Tools** | Clang, `xcodebuild`, SDK paths. Install: `xcode-select --install`, or they install with Xcode. After installing Xcode: `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer` |
| **CMake** | **≥ 3.16** (see `cpp/CMakeLists.txt`). Check: `cmake --version`. Install via [cmake.org](https://cmake.org/download/) or `brew install cmake` if your Xcode-bundled CMake is too old. |
| **iOS SDK** | Included with Xcode (device + Simulator). The CMake script uses `iphoneos` and `iphonesimulator` sysroots. |
| **Network (first C++ build)** | Building `cpp/` fetches **ZXing-cpp** via CMake `FetchContent` (git). Offline builds need a prior successful fetch or a vendored cache. |

---

## Languages / standards (this repo)

| Component | Language / standard |
|-----------|---------------------|
| App (Swift UI) | **Swift 5.0** (`SWIFT_VERSION` in Xcode project) |
| C++ core + ZXing | **C++20** (`cpp/CMakeLists.txt`) |
| Bridging header | C headers from `cpp/include/` |

---

## Xcode project settings (reference)

- **Minimum iOS:** **14.0** (`IPHONEOS_DEPLOYMENT_TARGET`, CMake `CMAKE_OSX_DEPLOYMENT_TARGET`).
- **Architectures:** App links prebuilt **`libbarcode_scanner_core.a`** for **iphoneos** (arm64) and **iphonesimulator** (see `ios/scripts/build_lib.sh` + `ios/ios.toolchain.cmake`). Simulator build currently uses **`SIMULATOR64`** (x86_64); on **Apple Silicon** Macs the simulator may run that slice under Rosetta, or you can extend the script to use **`SIMULATORARM64`** if needed.

---

## Build steps (summary)

1. **Native library:** from repo root, `make ios` or `./ios/scripts/build_lib.sh` (produces `ios/build/Release-iphoneos/` and `.../Release-iphonesimulator/`).
2. **App:** `open ios/turnTable.xcodeproj` → select a simulator or device → **Product → Build** (⌘B).

**Signing:** For a **physical device**, set a **Team** under **Signing & Capabilities**. Simulator builds do not require a paid Apple Developer account.

---

## Optional / release

- **IPA export:** Archive in Xcode on macOS; see repo **`RELEASE.md`**.
- **Icon regeneration:** `./ios/scripts/sync_app_icon_from_android.sh` needs **Python 3** + **Pillow** (`pip install Pillow`).

---

## Quick verification

```bash
xcodebuild -version
cmake --version    # expect 3.16+
swift --version    # Swift 5.x from Xcode toolchain
xcrun --show-sdk-path --sdk iphoneos
```

If `cmake` or SDK paths fail, fix Xcode path with `xcode-select` and open Xcode once to accept the license: `sudo xcodebuild -license accept`.
