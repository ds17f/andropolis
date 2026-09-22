# Task 002: Implement the C-ABI boundary

## Goal
Make the C ABI real. Bridge the functions in `micropolis_c.h` to the C++
`Micropolis` engine, so a plain C program can drive the simulation.

## Context
The planner designed the contract. You write one file: the implementation.

- Contract (do not change): `android/engine/include/micropolis_c.h`.
- File you edit: `android/engine/src/micropolis_c.cpp`. It is now a STUB with
  default returns. Replace every body with a real one.
- Test (do not change): `android/engine/test/abi_smoke.c`. It drives the ABI and
  checks the results. It must pass.
- Reuse: `android/engine/include/micropolis_null_callback.h` gives a `NullCallback`.
- Engine headers (read for signatures): in
  `MicropolisCore/packages/micropolis-engine/src/` — `micropolis.h` (the
  `Micropolis` class, `setCallback`, `init`, `generateSomeRandomCity`,
  `generateMap`, `loadCity`, `simTick`, `simUpdate`, `setSpeed`, `setPasses`,
  `setCityTax`, `setGameLevel`, `getTile`, and the fields `cityTime`,
  `totalFunds`, `cityPop`, `cityScore`, `cityYear`, `cityMonth`, `resValve`,
  `comValve`, `indValve`, `gameLevel`); and `tool.h` (`EditingTool`, and
  `doTool(EditingTool, short x, short y)` returns `ToolResult`).

Task 001 showed the start sequence: make a `Micropolis`, call `setCallback` with a
`NullCallback` and `emscripten::val()`, then `init()`, then generate, then tick.

## Interface (match exactly)
- Keep every function signature exactly as in `micropolis_c.h`. Do not change the
  header.
- Define the opaque handle inside the `.cpp`, for example:
  ```cpp
  struct MicropolisEngine {
      Micropolis sim;
      NullCallback callback;
  };
  ```
  `micropolis_create` makes one with `new`, calls
  `e->sim.setCallback(&e->callback, emscripten::val())`, and returns it.
  `micropolis_destroy` calls `delete`.
- `micropolis_copy_tiles`: copy in column-major order, `dst[x * H + y]`. Return
  `W*H`. Return 0 if `dst` is NULL or `dst_len < W*H`.
- `micropolis_get_stats`: fill every field of `MicropolisStats` from the engine.
- `micropolis_do_tool`: cast `tool` to `EditingTool`, call `doTool`, and return
  the `ToolResult` as an int.
- Add `static_assert` lines that prove the C enums equal the C++ enums:
  `MICROPOLIS_TOOL_RESIDENTIAL == TOOL_RESIDENTIAL`, ... , and the four
  `MicropolisToolResult` values equal the `ToolResult` values. Put them at file
  scope so a mismatch stops the build.

## Files in scope
- `android/engine/src/micropolis_c.cpp`: replace the stub bodies with real ones.

## Definition of done
- Build:
  ```
  cmake -S android -B android/build-host -DCMAKE_BUILD_TYPE=Debug
  cmake --build android/build-host -j
  ```
- Test:
  ```
  ./android/build-host/engine/micropolis_abi_smoke
  ```
  It must print `OK: C ABI smoke passed` and exit with code 0.
- The old test must still pass: `./android/build-host/smoke/micropolis_smoke`
  prints a line with `cityTime=` and a value more than 0.

## Constraints
- Change only `android/engine/src/micropolis_c.cpp`.
- Do not change the header, the test, the CMake files, or the engine sources.
- Do not add a new dependency.
- Stage only your file by name. Do not use `git checkout`, `git reset`, or
  `git add -A`. Commit when the test is green.
