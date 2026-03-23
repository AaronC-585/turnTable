cmake_minimum_required(VERSION 3.16)
project(barcode_scanner_core LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)

# ZXing-cpp (shared between Android and iOS)
include(FetchContent)
FetchContent_Declare(
  zxing_cpp
  GIT_REPOSITORY https://github.com/zxing-cpp/zxing-cpp.git
  GIT_TAG        v2.2.1
  GIT_SHALLOW    TRUE
)

# Avoid relying on host pkg-config modules like `stb`; force zxing-cpp
# to fetch its dependencies itself.
set(BUILD_DEPENDENCIES "GITHUB" CACHE STRING "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(ZXING_READERS ON CACHE BOOL "" FORCE)
set(ZXING_WRITERS OFF CACHE STRING "" FORCE)
set(ZXING_EXAMPLES OFF CACHE BOOL "" FORCE)
set(ZXING_C_API OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(zxing_cpp)
# 16KB page-size alignment for any shared libs built by dependencies (ZXing).
# Apply globally so it affects real shared-library targets (not ALIAS targets).
if(ANDROID)
  add_link_options("-Wl,-z,max-page-size=16384")
endif()

add_library(barcode_scanner_core STATIC
  src/barcode_scanner.cpp
)
target_include_directories(barcode_scanner_core PUBLIC
  ${CMAKE_CURRENT_SOURCE_DIR}/include
)
target_link_libraries(barcode_scanner_core PUBLIC ZXing::ZXing)
