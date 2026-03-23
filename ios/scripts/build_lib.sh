#!/bin/bash
# Builds the shared C++ barcode library for iOS (device and simulator).
# Run from repo root: ./ios/scripts/build_lib.sh
# Output: ios/build/Release-iphoneos/ and ios/build/Release-iphonesimulator/

set -e
if [[ "$(uname -s)" != "Darwin" ]]; then
	echo "iOS library build requires macOS with Xcode (iOS SDK + CMake)." >&2
	echo "Run: make ios   (or ./ios/scripts/build_lib.sh) on a Mac after installing Xcode." >&2
	exit 1
fi
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IOS_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
# Repo root is the parent of the ios/ directory
REPO_ROOT="$(cd "$IOS_DIR/.." && pwd)"
CPP_DIR="$REPO_ROOT/cpp"
TOOLCHAIN="$IOS_DIR/ios.toolchain.cmake"
BUILD_DIR="$IOS_DIR/build"

copy_release_lib() {
	local src_dir="$1"
	local dest_dir="$2"
	local primary="$src_dir/libbarcode_scanner_core.a"
	local nested="$src_dir/barcode_scanner_core/libbarcode_scanner_core.a"

	mkdir -p "$dest_dir"
	if [[ -f "$primary" ]]; then
		cp "$primary" "$dest_dir/"
	elif [[ -f "$nested" ]]; then
		cp "$nested" "$dest_dir/"
	else
		echo "Missing built library in $src_dir" >&2
		exit 1
	fi
}

# Device (arm64)
mkdir -p "$BUILD_DIR/device"
(cd "$BUILD_DIR/device" && cmake "$CPP_DIR" -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" -DPLATFORM=OS64 -DCMAKE_BUILD_TYPE=Release && cmake --build .)
copy_release_lib "$BUILD_DIR/device" "$BUILD_DIR/Release-iphoneos"

# Simulator
mkdir -p "$BUILD_DIR/simulator"
(cd "$BUILD_DIR/simulator" && cmake "$CPP_DIR" -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" -DPLATFORM=SIMULATOR64 -DCMAKE_BUILD_TYPE=Release && cmake --build .)
copy_release_lib "$BUILD_DIR/simulator" "$BUILD_DIR/Release-iphonesimulator"

echo "Built. Device lib: $BUILD_DIR/Release-iphoneos/ Simulator lib: $BUILD_DIR/Release-iphonesimulator/"
