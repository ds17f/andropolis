# Task 005: Zoom and pan the map

## Goal
Let the user pinch to zoom and drag to pan the map. Keep tap-to-build working
under the zoom/pan transform.

## Context
`MapView` draws the whole 120×100 map stretched to fill the view, and reports a
tap as tile coordinates through `onTileTap`. Add a viewport: a scale and a pan
offset. Draw the map through that transform, and invert it when converting a tap
to a tile.

Use Android's `ScaleGestureDetector` (pinch) and `GestureDetector` (drag + tap).
`MapView` owns them; nothing outside `MapView` changes.

## Interface (implementation notes for MapView.kt)
Add these fields:
- `scale = 1f`, `panX = 0f`, `panY = 0f`.
- A `ScaleGestureDetector` and a `GestureDetector`.

Tile size (square tiles): at scale 1 the whole map width fits the view, so
`tileSize = width.toFloat() / cols` (compute it in `onDraw`; `width` is valid there).

Draw (in `onDraw`):
- `canvas.save()`, then `canvas.translate(panX, panY)`, then `canvas.scale(scale, scale)`.
- For each tile draw the atlas cell into `dst = (x*tileSize, y*tileSize, +tileSize, +tileSize)`
  (square). Keep the atlas source-rect logic as-is.
- `canvas.restore()`.

Gestures:
- `ScaleGestureDetector` `onScale`: `scale = (scale * detector.scaleFactor).coerceIn(1f, 8f)`,
  then re-clamp pan (below), `invalidate()`, return true.
- `GestureDetector` `onScroll(e1, e2, dx, dy)`: `panX -= dx`, `panY -= dy`, clamp pan,
  `invalidate()`, return true.
- `GestureDetector` `onSingleTapUp(e)`: convert to a tile and call `onTileTap`
  (below); return true. Call `performClick()`.
- `onTouchEvent`: feed the event to BOTH detectors
  (`scaleDetector.onTouchEvent(event)`, `gestureDetector.onTouchEvent(event)`),
  then return true. Remove the old `ACTION_DOWN` tap handling.

Tap → tile (invert the transform):
- `worldX = (e.x - panX) / scale`, `worldY = (e.y - panY) / scale`.
- `tileX = (worldX / tileSize).toInt().coerceIn(0, cols - 1)`, and the same for
  `tileY` with `rows`.

Clamp pan (call after scroll and after scale) so the map cannot be dragged fully
off screen:
- `mapW = tileSize * cols * scale`; if `mapW <= width` then `panX = (width - mapW) / 2f`
  else `panX = panX.coerceIn(width - mapW, 0f)`.
- `mapH = tileSize * rows * scale`; if `mapH <= height` then `panY = (height - mapH) / 2f`
  else `panY = panY.coerceIn(height - mapH, 0f)`.

## Files in scope
- `android/app/src/main/java/micropolis/port/MapView.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Do not run the emulator yourself; the planner verifies pinch/drag/tap on the device.

## Constraints
- Change only `MapView.kt`.
- Do not change `MainActivity`, the JNI, the C ABI, the CMake files, or the engine.
- No new dependencies. Commit when green; stage only your file by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
