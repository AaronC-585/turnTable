# turnTable — build convenience
# iOS native lib requires macOS + Xcode (CMake iOS toolchain).

.PHONY: all cpp android android-release ios ios-lib clean help

help:
	@echo "Targets:"
	@echo "  make all              C++ core + Android debug APK; on macOS also iOS static libs"
	@echo "  make cpp              Standalone C++ library (tests / dev)"
	@echo "  make android          ./gradlew assembleDebug"
	@echo "  make android-release  ./gradlew assembleRelease"
	@echo "  make ios              C++ lib for iOS device + simulator (macOS only)"
	@echo "  make clean            Remove cpp/build and ios/build trees"
	@echo "  make help             This message"

all:
	@$(MAKE) cpp
	@$(MAKE) android
	@if [ "$$(uname -s)" = Darwin ]; then $(MAKE) ios; else echo "make all: skipping iOS (run on macOS with Xcode to build ios/ static libs)"; fi

cpp:
	@cd cpp && cmake -B build -DCMAKE_BUILD_TYPE=Release && cmake --build build

android:
	@chmod +x android/gradlew
	cd android && ./gradlew assembleDebug

android-release:
	@chmod +x android/gradlew
	cd android && ./gradlew assembleRelease

ios ios-lib:
	@chmod +x ios/scripts/build_lib.sh
	./ios/scripts/build_lib.sh

clean:
	rm -rf cpp/build ios/build
