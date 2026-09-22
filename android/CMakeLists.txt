# Top-level build for the Micropolis Android port.
# Works for two toolchains from the same tree:
#   - Host (g++/clang) for the headless smoke test.
#   - Android NDK (via -DCMAKE_TOOLCHAIN_FILE=<ndk>/build/cmake/android.toolchain.cmake).
cmake_minimum_required(VERSION 3.22)
project(micropolis_android_port CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_subdirectory(engine)
add_subdirectory(smoke)
