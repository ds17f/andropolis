# Task 003: Background sim thread and tap-to-build

## Goal
Move the simulation off the UI thread onto one dedicated thread, and let a tap on
the map build a park at that tile.

## Context
The app works now (see `DESIGN.md` §10). `MainActivity` currently ticks the
engine on the UI thread with a `Handler`. `MapView` draws the tiles.

The C++ engine is **not thread-safe**. All native calls for one engine handle
MUST run on ONE thread. So the design is: a dedicated "sim" thread owns the
handle and does every `MicropolisNative` call. The UI thread never calls the
engine directly. Touch events are sent to the sim thread as messages.

Use a `HandlerThread` for the sim thread. It gives you one thread with a message
queue, which is the clean way to keep all engine calls on one thread and to
marshal touch events onto it.

## Interface (match exactly)
Edit `MainActivity` to this design:

- In `onCreate`:
  - Make a `HandlerThread("sim")`, start it, and make a `Handler` on its looper
    (call it `sim`). Make a `Handler` on the main looper (call it `ui`).
  - Create the `MapView`, set it as the content view, and give it a tap listener
    (see below).
  - Post one setup message to `sim`: `create()`, `init(handle)`,
    `generateRandomCity(handle)`. Store the handle in a field the sim thread uses.
  - Post the tick loop (below) to `sim`.
- Tick loop (runs on `sim`):
  - Call `simTick(handle)` 8 times.
  - Call `copyTiles(handle, buf)` into a reused `ShortArray(W*H)`.
  - Post a copy of the tiles to the UI: `ui.post { mapView.update(tilesCopy) }`.
  - Re-post the tick loop to `sim` with `postDelayed(..., 100)`.
- Tap handling:
  - `MapView` reports a tap as tile coordinates (see MapView change). On a tap,
    post a message to `sim` that calls
    `doTool(handle, MICROPOLIS_TOOL_PARK, tileX, tileY)`. Use the integer value
    `11` for the park tool (it is `MicropolisTool.PARK` in the C ABI).
- In `onDestroy`:
  - Remove pending UI callbacks. Post a final message to `sim` that calls
    `destroy(handle)`. Then `quitSafely()` the `HandlerThread`.

Edit `MapView` to report taps as tile coordinates:
- Add `var onTileTap: ((Int, Int) -> Unit)? = null`.
- Override `onTouchEvent`. On `MotionEvent.ACTION_DOWN`, convert the pixel
  position to a tile: `tileX = (event.x / (width / cols)).toInt()` and
  `tileY = (event.y / (height / rows)).toInt()`. Clamp `tileX` to `0..cols-1` and
  `tileY` to `0..rows-1`. Call `onTileTap?.invoke(tileX, tileY)`. Return `true`
  for `ACTION_DOWN` so touches are received; call `performClick()` too.

## Files in scope
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.
- `android/app/src/main/java/micropolis/port/MapView.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug`
  It must finish with `BUILD SUCCESSFUL`.
- Do not run the emulator yourself; the planner verifies on the device.

## Constraints
- Change only the two files in scope.
- Do not call any `MicropolisNative` method from the UI thread. Every engine call
  runs on the sim thread.
- Do not change the C ABI, the JNI code, the CMake files, or the engine.
- No new dependencies. Commit when the build is green.
- Stage only your two files by name. Do not use `git checkout`, `git reset`, or
  `git add -A`.
