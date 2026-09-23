# Task 052: Undo the build, not the simulation

## Goal
Undo currently reloads a whole-city snapshot, which also rewinds time, growth and money.
Replace it with **per-build undo**: undoing a build restores only the tiles that build
changed and refunds its cost. Time and the rest of the city are untouched. Redo re-applies it.

## Why tiles are enough
The engine's tile map is the world state: each 16-bit tile encodes what is there (road
kind, power-plant piece, zone type AND density) plus flags (power, conductive, burnable,
bulldozable, animated, zone-center). Derived maps (power grid, density, crime, …) are
recomputed from tiles on the engine's scans. Funds are separate — hence the refund.

## Context
- C-ABI header (`android/engine/include/micropolis_c.h`) now declares:
  - `void micropolis_set_tile(MicropolisEngine *e, int x, int y, unsigned short value);`
  - `void micropolis_set_funds(MicropolisEngine *e, int funds);`
  Implement them in `android/engine/src/micropolis_c.cpp` (copy the style of neighbours):
  - set_tile: `if (!e || x < 0 || y < 0 || x >= MICROPOLIS_MAP_W || y >= MICROPOLIS_MAP_H) return;
    e->sim->setTile(x, y, value); e->sim->invalidateMaps();`
  - set_funds: `if (e) e->sim->setFunds(funds);`
  Then JNI (`micropolis_jni.cpp`, copy `setCityTax`) and Kotlin:
  `external fun setTile(handle: Long, x: Int, y: Int, value: Int)` (pass `value` as jint,
  cast to `unsigned short` in C) and `external fun setFunds(handle: Long, funds: Int)`.
- `MicropolisNative.copyTiles(handle, ShortArray(W*H))` copies the map column-major
  (`dst[x * H + y]`, W=120, H=100). `getStats(handle, IntArray(10))` → funds at index 1.
- `MapView`: one build stroke = ACTION_DOWN … ACTION_UP; it calls `onTileTap(x, y)` for each
  tile built and `onStrokeEnd(built: Boolean)` at the end. Multi-tile tools anchor at
  (x-1, y-1) with size `toolFootprint` (up to 6).
- `MainActivity` today: `mapView.onStrokeEnd = { built -> if (built) commitSnapshot() }`,
  plus `commitSnapshot()`, `undo()`, `redo()`, `resetHistory()`, `updateUndoButtons()` built
  on file snapshots (`history`, `cursor`, `snapDir`). Redo is in the ⋮ menu
  (`isEnabled = cursor < history.size - 1`). Undo button: `undoBtn`.
- All engine calls run on the `sim` Handler thread; UI on `ui`.

## Interface

### 1. Stroke start signal (MapView)
Add `var onStrokeStart: (() -> Unit)? = null` and call it in `ACTION_DOWN` (every stroke).

### 2. Undo records (MainActivity)
```kotlin
private class BuildEdit(val idx: IntArray, val before: ShortArray, val after: ShortArray, val cost: Int)
private val undoStack = ArrayDeque<BuildEdit>()   // newest last, cap 24
private val redoStack = ArrayDeque<BuildEdit>()
private val strokeBefore = ShortArray(120 * 100)  // map at stroke start (sim thread)
private var strokeFundsBefore = 0
private var strokeMinX = Int.MAX_VALUE; private var strokeMinY = Int.MAX_VALUE
private var strokeMaxX = -1; private var strokeMaxY = -1
```
- `mapView.onStrokeStart = { sim.post { MicropolisNative.copyTiles(handle, strokeBefore);
  val s = IntArray(10); MicropolisNative.getStats(handle, s); strokeFundsBefore = s[1] };
  strokeMinX = Int.MAX_VALUE; strokeMinY = Int.MAX_VALUE; strokeMaxX = -1; strokeMaxY = -1 }`
- In `mapView.onTileTap`, before posting `doTool`, grow the stroke box by the tool's footprint:
  for footprint n > 1 the building covers (x-1 .. x-2+n, y-1 .. y-2+n); for n == 1 just (x, y).
  Update `strokeMin/Max` accordingly.
- `mapView.onStrokeEnd = { built -> if (built) commitBuild() }` where `commitBuild()` posts to
  `sim` (FIFO after the stroke's doTool calls):
  - `after = ShortArray(W*H)`; `copyTiles(handle, after)`; read funds → `fundsAfter`.
  - Examine only the box expanded by **1 tile** on every side (clamped to the map) — roads,
    rails and wires re-shape their neighbours. Collect every index where
    `strokeBefore[i] != after[i]` into `idx`, with `before`/`after` values.
  - `cost = (strokeFundsBefore - fundsAfter).coerceAtLeast(0)`.
  - If any tile changed: on `ui`, push `BuildEdit` onto `undoStack` (drop oldest past 24),
    clear `redoStack`, `updateUndoButtons()`.

### 3. Undo / redo (replace the bodies of `undo()` and `redo()`)
```kotlin
private fun undo() {
    val e = undoStack.removeLastOrNull() ?: return
    redoStack.addLast(e); updateUndoButtons()
    sim.post {
        for (k in e.idx.indices) { val i = e.idx[k]; MicropolisNative.setTile(handle, i / 100, i % 100, e.before[k].toInt() and 0xFFFF) }
        val s = IntArray(10); MicropolisNative.getStats(handle, s)
        MicropolisNative.setFunds(handle, s[1] + e.cost)
    }
}
private fun redo() {
    val e = redoStack.removeLastOrNull() ?: return
    undoStack.addLast(e); updateUndoButtons()
    sim.post {
        for (k in e.idx.indices) { val i = e.idx[k]; MicropolisNative.setTile(handle, i / 100, i % 100, e.after[k].toInt() and 0xFFFF) }
        val s = IntArray(10); MicropolisNative.getStats(handle, s)
        MicropolisNative.setFunds(handle, s[1] - e.cost)
    }
}
```
(Index → (x, y): the map is column-major with H = 100, so `x = i / 100`, `y = i % 100`.)
- `updateUndoButtons()`: enabled/alpha from `undoStack.isNotEmpty()`.
- ⋮ menu Redo: `isEnabled = redoStack.isNotEmpty()`.
- `resetHistory()` (New city, Load city): clear both stacks and update the buttons.
- Stop using the file-snapshot system: remove the calls to `commitSnapshot()` (on stroke end,
  after startup, after New city / load). You may leave the old `commitSnapshot`/`history`/
  `snapDir` code in place unused if removing it is awkward.

## Files in scope
- `android/engine/src/micropolis_c.cpp`, `android/app/src/main/cpp/micropolis_jni.cpp`,
  `MicropolisNative.kt`, `MapView.kt`, `MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Planner verifies on device: build a road, let the date advance, Undo → the road is gone,
  funds refunded, the date did NOT go back; Redo puts it back and re-charges.

## Constraints
- Do not change the C-ABI header (done), engine sources, CMake, or `build.gradle.kts`.
- Commit when green; stage only your files by name. Do not use `git checkout`, `git reset`, or `git add -A`.
