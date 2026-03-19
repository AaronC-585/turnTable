cmake_minimum_required(VERSION 3.16)
project(barcode_scanner_core LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)

# ZXing-cpp (shared between Android and iOS)
include(FetchContent)
FetchContent_Declare(
  zxing_cpp
  GIT_REPOSITORY https://github.com/zxing-cpp/zxing-cpp.git
  GIT_TAG        v3.0.2
  GIT_SHALLOW    TRUE
)
set(ZXING_READERS ON CACHE BOOL "" FORCE)
set(ZXING_WRITERS OFF CACHE STRING "" FORCE)
set(ZXING_EXAMPLES OFF CACHE BOOL "" FORCE)
set(ZXING_C_API OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(zxing_cpp)

add_library(barcode_scanner_core STATIC
  src/barcode_scanner.cpp
)
target_include_directories(barcode_scanner_core PUBLIC
  ${CMAKE_CURRENT_SOURCE_DIR}/include
)
target_link_libraries(barcode_scanner_core PUBLIC ZXing::ZXing)
