# Task 001: Null callback and headless smoke test

## Goal
Make the Micropolis C++ engine tick with no Emscripten. Write a null callback
and a small program that runs the simulation and prints statistics.

## Context
This is the first step of the native Android port. See `DESIGN.md`. The engine is
in `MicropolisCore/packages/micropolis-engine/src/`. It builds native without
Emscripten. The CMake files are ready: `android/CMakeLists.txt`,
`android/engine/CMakeLists.txt`, and `android/smoke/CMakeLists.txt`. You write two
files only.

The `Micropolis` class needs a callback object. The class is in `micropolis.h`.
The abstract `Callback` class is in `callback.h`. It has many pure virtual
methods. The only concrete callback in the engine is the JS one, which the native
build does not use. So you must write a native callback that does nothing.

Read these files for the exact signatures:
- `MicropolisCore/packages/micropolis-engine/src/callback.h` — the `Callback` class.
- `MicropolisCore/packages/micropolis-engine/src/micropolis.h` — the `Micropolis`
  class, `setCallback`, `init`, `generateSomeRandomCity`, `simTick`, and the fields
  `cityTime`, `totalFunds`, and `cityPop`.

## Interface (match exactly)
Write `android/smoke/null_callback.h`:
```cpp
#pragma once
#include "callback.h"

// A Callback that does nothing. Use it for headless runs (no JS, no UI).
// Override EVERY pure virtual method of Callback with an empty body.
// Methods that return a value return a default value (0, false, "").
class NullCallback : public Callback {
    // ... one override for each pure virtual method in callback.h ...
};
```

Write `android/smoke/smoke_main.cpp`:
```cpp
#include <cstdio>
#include "micropolis.h"
#include "null_callback.h"

int main() {
    Micropolis *engine = new Micropolis();
    NullCallback callback;
    engine->setCallback(&callback, emscripten::val());  // native val stub
    engine->init();
    engine->generateSomeRandomCity();

    for (int i = 0; i < 500; i++) {
        engine->simTick();
    }

    std::printf("cityTime=%d totalFunds=%d cityPop=%d\n",
                (int)engine->cityTime, (int)engine->totalFunds,
                (int)engine->cityPop);

    delete engine;
    return 0;
}
```
Adjust only if a signature in the headers needs it. Do not change the tick
sequence or the printed fields.

## Files in scope
- `android/smoke/null_callback.h`: create.
- `android/smoke/smoke_main.cpp`: create.

## Definition of done
- Build:
  ```
  cmake -S android -B android/build-host -DCMAKE_BUILD_TYPE=Debug
  cmake --build android/build-host --target micropolis_smoke -j
  ```
- Test:
  ```
  ./android/build-host/smoke/micropolis_smoke
  ```
  The program must exit with code 0. It must print one line with `cityTime=`,
  `totalFunds=`, and `cityPop=`. The value of `cityTime` must be more than 0.

## Constraints
- Do not change the C++ engine sources in `MicropolisCore/`.
- Do not change the CMake files. Write only the two files in scope.
- Do not add a new dependency.
- Commit your work when the test is green.
