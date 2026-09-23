# Task 030: Place-on-lift for buildings — drag a ghost, place on finger up

## Goal
Multi-tile placement tools (zones, services, power plants, stadium, port, airport)
should NOT build the instant you touch or while you drag. Instead, dragging moves a
translucent **ghost** footprint over the map, and the building is placed only when the
finger LIFTS. Line tools (road, rail, wire, bulldozer, terrain, park) keep building
continuously on touch-and-drag as they do now.

## Context
`MapView.onTouchEvent` (post-029) handles one finger = build, two fingers = pan, pinch =
zoom, and already tracks `built` and calls `onStrokeEnd(built)` on `ACTION_UP`/`CANCEL`
(that drives Undo — keep it working: one placement must still be ONE `onStrokeEnd(true)`).
`buildAt(px,py)` converts a screen point to a tile (`tileX = ((px - panX)/scale)/tileSize`,
same for Y, coerced to `0..cols-1` / `0..rows-1`) and invokes `onTileTap`. `onDraw` draws
tiles at screen rect `left = panX + tile*tileSize*scale` (scale/tileSize/panX/panY/cols/
rows are fields). `MainActivity.currentTool` is the selected tool; `updatePill()` runs on
every tool change.

The rule uses the engine's footprint sizes (`gToolSize`), indexed by tool value:
```
tool:  0 1 2 3  4 5 6 7  8 9 10 11  12 13 14 15  16 17 18 19
size:  3 3 3 3  3 1 1 1  1 1  4  1   4  4  4  6   1  1  1  1
```
A tool with **size > 1 is a placement tool** (place-on-lift); **size == 1 is continuous**.
When placed, the engine anchors the building's TOP-LEFT tile at `(tileX-1, tileY-1)` and it
covers `size × size` tiles (the engine does `mapH--; mapV--`). The ghost must match that.

## Interface

### MainActivity.kt — tell MapView the current tool's footprint:
```kotlin
// engine gToolSize, index = tool value; default 1 for anything past the table
private val toolFootprints = intArrayOf(3,3,3,3, 3,1,1,1, 1,1,4,1, 4,4,4,6, 1,1,1,1)
private fun footprintOf(tool: Int) = toolFootprints.getOrElse(tool) { 1 }
```
- In `updatePill()`, add `mapView.toolFootprint = footprintOf(currentTool)`.
- Set it once at startup too (after `mapView` is created / initial `currentTool` known),
  e.g. right where the initial tool/pill is set.

### MapView.kt — footprint + ghost state:
```kotlin
var toolFootprint: Int = 1              // set by MainActivity; >1 == place-on-lift
private var ghostX = -1                 // tile coords of the ghost anchor; -1 == no ghost
private var ghostY = -1
private val ghostFill = Paint().apply { color = 0x55F5A623; style = Paint.Style.FILL }
private val ghostStroke = Paint().apply {
    color = 0xFFF5A623.toInt(); style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true
}
```
Add a small helper mirroring `buildAt`'s conversion (reuse it in both places):
```kotlin
private fun tileXat(px: Float) = (((px - panX) / scale) / tileSize).toInt().coerceIn(0, cols - 1)
private fun tileYat(py: Float) = (((py - panY) / scale) / tileSize).toInt().coerceIn(0, rows - 1)
```

### MapView.kt — touch handling (branch the one-finger path on `toolFootprint`):
- `ACTION_DOWN`: keep `built = false`. If `toolFootprint > 1`: set `ghostX = tileXat(event.x)`,
  `ghostY = tileYat(event.y)`, `invalidate()`, and DO NOT build. Else: existing `buildAt(...)`.
- `ACTION_MOVE`, one finger (`pointerCount < 2 && !panning`): if `toolFootprint > 1`: update
  `ghostX/ghostY` from the event, `invalidate()`, DO NOT build. Else: existing `buildAt(...)`.
- `ACTION_POINTER_DOWN` (2nd finger → pan): clear the ghost (`ghostX = ghostY = -1`) in
  addition to the existing `panning = true; built = false; ...`; `invalidate()`.
- `ACTION_UP`: if `toolFootprint > 1 && !panning && ghostX >= 0`, COMMIT once:
  `onTileTap?.invoke(ghostX, ghostY); built = true`. Then clear the ghost. Keep the existing
  `panning = false; lastBuiltTile = null; onStrokeEnd?.invoke(built); performClick()`
  (invoke order: commit the build BEFORE `onStrokeEnd`, so the undo snapshot captures it).
- `ACTION_CANCEL`: clear the ghost with no build; keep the rest as-is.

### MapView.kt — draw the ghost in `onDraw` (AFTER the tiles):
When `ghostX >= 0 && toolFootprint > 0`, draw the footprint rectangle. Anchor top-left tile
is `(ghostX - 1, ghostY - 1)` when `toolFootprint > 1` (matches the engine), size
`toolFootprint`:
```kotlin
if (ghostX >= 0) {
    val n = toolFootprint
    val tlx = if (n > 1) ghostX - 1 else ghostX
    val tly = if (n > 1) ghostY - 1 else ghostY
    val left = panX + tlx * tileSize * scale
    val top  = panY + tly * tileSize * scale
    val side = n * tileSize * scale
    val r = RectF(left, top, left + side, top + side)
    canvas.drawRect(r, ghostFill)
    canvas.drawRect(r, ghostStroke)
}
```
(`RectF` is already imported.)

## Files in scope
- `android/app/src/main/java/micropolis/port/MapView.kt`: edit.
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Placement can't be tested here; the planner verifies drag-ghost / lift-to-place on device.

## Constraints
- Change only the two files above. Do not change the JNI, C ABI, CMake, engine,
  drawables, or `build.gradle.kts`. Keep line-tool drawing and Undo's `onStrokeEnd`
  behavior intact.
- No new dependencies. Commit when green; stage only your files by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
