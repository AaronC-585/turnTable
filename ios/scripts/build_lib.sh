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
	local lib_name="$3"
	local primary="$src_dir/$lib_name"
	local nested="$src_dir/barcode_scanner_core/$lib_name"
	local dep_nested="$src_dir/_deps/zxing_cpp-build/core/$lib_name"
	local discovered_path

	mkdir -p "$dest_dir"
	if [[ -f "$primary" ]]; then
		cp "$primary" "$dest_dir/"
	elif [[ -f "$nested" ]]; then
		cp "$nested" "$dest_dir/"
	elif [[ -f "$dep_nested" ]]; then
		cp "$dep_nested" "$dest_dir/"
	else
		discovered_path="$(python3 - "$src_dir" "$lib_name" <<'PY'
import os
import sys

root = sys.argv[1]
needle = sys.argv[2]
for dirpath, _, filenames in os.walk(root):
    if needle in filenames:
        print(os.path.join(dirpath, needle))
        break
PY
)"
		if [[ -n "$discovered_path" && -f "$discovered_path" ]]; then
			cp "$discovered_path" "$dest_dir/"
			return 0
		fi
		echo "Missing built library $lib_name in $src_dir" >&2
		exit 1
	fi
}

# Device (arm64)
mkdir -p "$BUILD_DIR/device"
(cd "$BUILD_DIR/device" && cmake "$CPP_DIR" -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" -DPLATFORM=OS64 -DCMAKE_BUILD_TYPE=Release && cmake --build .)
copy_release_lib "$BUILD_DIR/device" "$BUILD_DIR/Release-iphoneos" "libbarcode_scanner_core.a"
copy_release_lib "$BUILD_DIR/device" "$BUILD_DIR/Release-iphoneos" "libZXing.a"

# Simulator
mkdir -p "$BUILD_DIR/simulator"
(cd "$BUILD_DIR/simulator" && cmake "$CPP_DIR" -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" -DPLATFORM=SIMULATOR64 -DCMAKE_BUILD_TYPE=Release && cmake --build .)
copy_release_lib "$BUILD_DIR/simulator" "$BUILD_DIR/Release-iphonesimulator" "libbarcode_scanner_core.a"
copy_release_lib "$BUILD_DIR/simulator" "$BUILD_DIR/Release-iphonesimulator" "libZXing.a"

echo "Built. Device lib: $BUILD_DIR/Release-iphoneos/ Simulator lib: $BUILD_DIR/Release-iphonesimulator/"
