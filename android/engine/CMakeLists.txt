# The Micropolis C++ simulation engine + the C-ABI boundary, built native.
#
# We compile 25 of the 27 upstream sources. We exclude the two JS-binding files:
#   - emscripten.cpp  (Embind bindings)
#   - callback.cpp    (JS callback impl; includes <emscripten.h>)
# Without -D__EMSCRIPTEN__, micropolis.h uses its built-in native `emscripten::val`
# stub (the #else branch), so the core compiles with a plain C++17 toolchain.
#
# On top of the engine we build micropolis_c.cpp: a stable C ABI (micropolis_c.h)
# that Kotlin/JNI calls, hiding all C++ and Emscripten types.

set(ENGINE_SRC_DIR
    ${CMAKE_CURRENT_SOURCE_DIR}/../../MicropolisCore/packages/micropolis-engine/src)

set(ENGINE_SOURCES
    allocate animate budget connect disasters evaluate fileio generate graph
    initialize map frontendmessage message micropolis position power random
    scan simulate sprite tool traffic update utilities zone)
list(TRANSFORM ENGINE_SOURCES PREPEND ${ENGINE_SRC_DIR}/)
list(TRANSFORM ENGINE_SOURCES APPEND .cpp)

add_library(micropolis_engine STATIC
    ${ENGINE_SOURCES}
    ${CMAKE_CURRENT_SOURCE_DIR}/src/micropolis_c.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/src/micropolis_seeded.cpp)
target_include_directories(micropolis_engine PUBLIC
    ${ENGINE_SRC_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/include)
target_compile_features(micropolis_engine PUBLIC cxx_std_17)

# C-ABI smoke test: a plain C program that drives the engine through the C ABI.
# Proves the boundary is truly C-callable. LINKER_LANGUAGE=CXX pulls in libstdc++.
# Host-only: the Android/Gradle build reuses this dir via add_subdirectory and
# only wants the library, so skip the test executable when cross-compiling.
if(NOT ANDROID)
    add_executable(micropolis_abi_smoke ${CMAKE_CURRENT_SOURCE_DIR}/test/abi_smoke.c)
    target_link_libraries(micropolis_abi_smoke PRIVATE micropolis_engine)
    set_target_properties(micropolis_abi_smoke PROPERTIES LINKER_LANGUAGE CXX)
    add_executable(micropolis_determinism ${CMAKE_CURRENT_SOURCE_DIR}/test/determinism.c)
    target_link_libraries(micropolis_determinism PRIVATE micropolis_engine)
    set_target_properties(micropolis_determinism PROPERTIES LINKER_LANGUAGE CXX)
endif()
