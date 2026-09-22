# Task 009: Center the map on first layout

## Goal
Fix the startup look: the map should be centered in the view immediately, not
sit at the top with a dark gap until the user makes a gesture.

## Context
`MapView` draws the tile map through a `scale`/`panX`/`panY` viewport. `clampPan()`
already centers the map when it is smaller than the view, but it is only called
from the gesture handlers, so at startup `panX`/`panY` are 0 and the map is drawn
at the top-left. `clampPan()` needs `tileSize`, which is currently computed inside
`onDraw` (`tileSize = width.toFloat() / cols`), so it is 0 until the first draw.

## Interface (implementation notes for MapView.kt)
- Override `onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int)`. In it:
  set `tileSize = w.toFloat() / cols`, then call `clampPan()`, then `invalidate()`.
  Call `super.onSizeChanged(w, h, oldw, oldh)` first.
- Keep the `tileSize = width.toFloat() / cols` line in `onDraw` (harmless; it keeps
  the value correct if the view is drawn before a size change).

## Files in scope
- `android/app/src/main/java/micropolis/port/MapView.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Do not run the emulator; the planner verifies the centered map on the device.

## Constraints
- Change only `MapView.kt`.
- Do not change other files, the JNI, the C ABI, the CMake files, or the engine.
- No new dependencies. Commit when green; stage only your file by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
