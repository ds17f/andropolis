cmake_minimum_required(VERSION 3.22)
project(micropolis_jni CXX)

# Reuse the engine + C-ABI static library (android/engine). The path is relative
# to this file: cpp -> main -> src -> app -> android, then /engine.
add_subdirectory(
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../engine
    ${CMAKE_BINARY_DIR}/engine)

add_library(micropolis SHARED micropolis_jni.cpp)

find_library(log-lib log)
target_link_libraries(micropolis PRIVATE micropolis_engine ${log-lib})
