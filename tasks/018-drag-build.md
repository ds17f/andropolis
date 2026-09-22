# Task 018: Drag to build (and navigate vs. build)

## Goal
In Build mode, dragging a finger lays a continuous line of the current tool (e.g.
roads), not just one tile. In Move mode, dragging pans the map (unchanged). This
also cleanly separates navigating the map from building on it.

## Context
`MapView` has `buildEnabled` (set by the Move/Build toggle), a `convertToTile`
helper, a `scaleDetector` (pinch) and a `gestureDetector` (pan + tap). Today a
single tap builds one tile via `GestureListener.onSingleTapUp`. Change touch
handling so that in Build mode, DOWN and MOVE build tiles along the path, while
Move mode keeps panning. Pinch-zoom must keep working in both modes. Uses the
existing `onTileTap` (which already calls `do_tool`) — no engine/JNI change.

## Interface (implementation notes for MapView.kt)
- Add a field to avoid re-building the same tile repeatedly during a drag:
  ```kotlin
  private var lastBuiltTile: Pair<Int, Int>? = null
  ```
- Replace `onTouchEvent` with:
  ```kotlin
  override fun onTouchEvent(event: MotionEvent): Boolean {
      scaleDetector.onTouchEvent(event)
      // Build mode: drag paints tiles. Move mode: drag pans. Pinch always zooms.
      if (buildEnabled && !scaleDetector.isInProgress) {
          when (event.actionMasked) {
              MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> { buildAt(event); return true }
              MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                  lastBuiltTile = null; performClick(); return true
              }
          }
      } else {
          gestureDetector.onTouchEvent(event)
      }
      return true
  }

  private fun buildAt(event: MotionEvent) {
      val (tileX, tileY) = convertToTile(event)
      if (lastBuiltTile?.let { it.first == tileX && it.second == tileY } != true) {
          lastBuiltTile = Pair(tileX, tileY)
          onTileTap?.invoke(tileX, tileY)
      }
  }
  ```
- In `GestureListener.onSingleTapUp`, REMOVE the build (the `if (buildEnabled) { ...
  onTileTap ... }` block) since building is now handled in `onTouchEvent`. Keep
  `performClick()` and `return true` so pan-mode taps still behave.

## Files in scope
- `android/app/src/main/java/micropolis/port/MapView.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Do not run the emulator; the planner verifies drag-build and pan on device.

## Constraints
- Change only `MapView.kt`.
- Do not change `MainActivity`, the JNI, the C ABI, the CMake files, or the engine.
- No new dependencies. Commit when green; stage only your file by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
