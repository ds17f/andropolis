# The Micropolis C++ simulation engine, built native (no Emscripten).
#
# We compile 25 of the 27 upstream sources. We exclude the two JS-binding files:
#   - emscripten.cpp  (Embind bindings)
#   - callback.cpp    (JS callback impl; includes <emscripten.h>)
# Without -D__EMSCRIPTEN__, micropolis.h uses its built-in native `emscripten::val`
# stub (the #else branch), so the core compiles with a plain C++17 toolchain.

set(ENGINE_SRC_DIR
    ${CMAKE_CURRENT_SOURCE_DIR}/../../MicropolisCore/packages/micropolis-engine/src)

set(ENGINE_SOURCES
    allocate animate budget connect disasters evaluate fileio generate graph
    initialize map frontendmessage message micropolis position power random
    scan simulate sprite tool traffic update utilities zone)
list(TRANSFORM ENGINE_SOURCES PREPEND ${ENGINE_SRC_DIR}/)
list(TRANSFORM ENGINE_SOURCES APPEND .cpp)

add_library(micropolis_engine STATIC ${ENGINE_SOURCES})
target_include_directories(micropolis_engine PUBLIC ${ENGINE_SRC_DIR})
target_compile_features(micropolis_engine PUBLIC cxx_std_17)
