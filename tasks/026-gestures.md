# Task 026: Gesture model — 1 finger builds, 2 fingers navigate

## Goal
Make map interaction natural: ONE finger builds (tap or drag), TWO fingers pan,
and pinch zooms **toward the pinch point** (it currently does not). Remove the
Move/Build toggle — the finger count decides.

## Context
`MapView` currently uses `buildEnabled` (set by a Move/Build toggle) to decide
between building and panning, a `GestureDetector` for pan+tap, and a
`ScaleGestureDetector` for pinch that does NOT keep the focal point fixed.
`MainActivity` has the Move/Build segmented toggle (`segMove`/`segBuild`/
`buildMode`/`styleModeSegments`/`modeContainer`). `onTileTap` builds a tile.
`scale`/`panX`/`panY`/`tileSize`/`clampPan()`/`cols`/`rows` exist.

## Interface

### MapView.kt — replace the touch handling entirely with:
```kotlin
private var panning = false
private var lastFocusX = 0f
private var lastFocusY = 0f
private var lastBuiltTile: Pair<Int, Int>? = null

override fun onTouchEvent(event: MotionEvent): Boolean {
    scaleDetector.onTouchEvent(event)
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> { lastBuiltTile = null; buildAt(event.x, event.y) }
        MotionEvent.ACTION_POINTER_DOWN -> {            // 2nd finger: navigate, stop building
            panning = true; lastBuiltTile = null
            lastFocusX = focusX(event); lastFocusY = focusY(event)
        }
        MotionEvent.ACTION_MOVE -> {
            if (event.pointerCount >= 2) {              // two-finger pan (zoom via onScale)
                val fx = focusX(event); val fy = focusY(event)
                panX += fx - lastFocusX; panY += fy - lastFocusY
                lastFocusX = fx; lastFocusY = fy
                clampPan(); invalidate()
            } else if (!panning) {                      // one-finger drag: build along path
                buildAt(event.x, event.y)
            }
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            panning = false; lastBuiltTile = null; performClick()
        }
    }
    return true
}

private fun focusX(e: MotionEvent): Float { var s = 0f; for (i in 0 until e.pointerCount) s += e.getX(i); return s / e.pointerCount }
private fun focusY(e: MotionEvent): Float { var s = 0f; for (i in 0 until e.pointerCount) s += e.getY(i); return s / e.pointerCount }

private fun buildAt(px: Float, py: Float) {
    val tileX = (((px - panX) / scale) / tileSize).toInt().coerceIn(0, cols - 1)
    val tileY = (((py - panY) / scale) / tileSize).toInt().coerceIn(0, rows - 1)
    if (lastBuiltTile?.let { it.first == tileX && it.second == tileY } != true) {
        lastBuiltTile = Pair(tileX, tileY)
        onTileTap?.invoke(tileX, tileY)
    }
}
```

### MapView.kt — focal-point zoom in the scale listener:
```kotlin
private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
    override fun onScale(d: ScaleGestureDetector): Boolean {
        val newScale = (scale * d.scaleFactor).coerceIn(1f, 8f)
        val f = newScale / scale
        // keep the world point under the pinch focus fixed
        panX = d.focusX - (d.focusX - panX) * f
        panY = d.focusY - (d.focusY - panY) * f
        scale = newScale
        clampPan(); invalidate()
        return true
    }
}
```

### MapView.kt — remove:
`buildEnabled`, the `GestureDetector`/`GestureListener` and its use, and the old
`convertToTile` (replaced by `buildAt`). Keep `scaleDetector`, `onDraw`,
`clampPan`, `onSizeChanged`, `update`, and `onTileTap`.

### MainActivity.kt — remove the Move/Build toggle:
Delete `buildMode`, `segMove`, `segBuild`, `modeContainer`, `styleModeSegments()`,
and every `mapView.buildEnabled = ...` line. Do not add anything in its place
(gestures handle it). Leave the tool pill and the Trial/Keep/Revert buttons.

## Files in scope
- `android/app/src/main/java/micropolis/port/MapView.kt`: edit.
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Multi-touch can't be tested here; the user verifies gestures on device.

## Constraints
- Change only the two files above. Do not change the JNI, C ABI, CMake, engine,
  drawables, or `build.gradle.kts`.
- No new dependencies. Commit when green; stage only your files by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
