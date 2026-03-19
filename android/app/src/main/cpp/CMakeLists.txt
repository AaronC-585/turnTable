cmake_minimum_required(VERSION 3.22.1)
project(barcode_jni CXX)

set(CMAKE_CXX_STANDARD 20)

# Shared C++ barcode core (repo root is ../../../../../ from app/src/main/cpp)
get_filename_component(BARCODE_TOP "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../" ABSOLUTE)
set(CMAKE_POLICY_DEFAULT_CMP0077 NEW)
add_subdirectory(${BARCODE_TOP}/cpp ${CMAKE_BINARY_DIR}/barcode_core)

add_library(barcode_jni SHARED jni_bridge.cpp)
target_include_directories(barcode_jni PRIVATE ${BARCODE_TOP}/cpp/include)
target_link_libraries(barcode_jni barcode_scanner_core)
# Align load segments to 16KB for 16KB page-size devices (ELF align 2**14)
target_link_options(barcode_jni PRIVATE "-Wl,-z,max-page-size=16384")
