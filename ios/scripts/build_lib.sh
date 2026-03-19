#!/bin/bash
# Builds the shared C++ barcode library for iOS (device and simulator).
# Run from repo root: ./ios/scripts/build_lib.sh
# Output: ios/build/Release-iphoneos/ and ios/build/Release-iphonesimulator/

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IOS_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$IOS_DIR/../.." && pwd)"
CPP_DIR="$REPO_ROOT/cpp"
TOOLCHAIN="$IOS_DIR/ios.toolchain.cmake"
BUILD_DIR="$IOS_DIR/build"

# Device (arm64)
mkdir -p "$BUILD_DIR/device"
(cd "$BUILD_DIR/device" && cmake "$CPP_DIR" -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" -DPLATFORM=OS64 -DCMAKE_BUILD_TYPE=Release && cmake --build .)
mkdir -p "$BUILD_DIR/Release-iphoneos"
cp "$BUILD_DIR/device/libbarcode_scanner_core.a" "$BUILD_DIR/Release-iphoneos/" 2>/dev/null || \
  cp "$BUILD_DIR/device/barcode_scanner_core/libbarcode_scanner_core.a" "$BUILD_DIR/Release-iphoneos/" 2>/dev/null || true

# Simulator
mkdir -p "$BUILD_DIR/simulator"
(cd "$BUILD_DIR/simulator" && cmake "$CPP_DIR" -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" -DPLATFORM=SIMULATOR64 -DCMAKE_BUILD_TYPE=Release && cmake --build .)
mkdir -p "$BUILD_DIR/Release-iphonesimulator"
cp "$BUILD_DIR/simulator/libbarcode_scanner_core.a" "$BUILD_DIR/Release-iphonesimulator/" 2>/dev/null || \
  cp "$BUILD_DIR/simulator/barcode_scanner_core/libbarcode_scanner_core.a" "$BUILD_DIR/Release-iphonesimulator/" 2>/dev/null || true

echo "Built. Device lib: $BUILD_DIR/Release-iphoneos/ Simulator lib: $BUILD_DIR/Release-iphonesimulator/"
