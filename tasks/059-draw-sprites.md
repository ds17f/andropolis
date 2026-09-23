# Task 059: Draw the moving objects (monster, tornado, trains, planes, ships…)

## Goal
The engine's moving objects are **sprites**, not tiles, and the map never draws them.
So a monster or tornado is invisible: you only see the damage. Draw them on the map,
over the tiles and the overlay tint, every frame.

## Context (read only these parts)
- `MicropolisNative.kt`: `external fun copySprites(handle: Long, dst: IntArray): Int` —
  fills 4 ints per active sprite: `[type, frame, left, top]`. `left`/`top` are world
  pixels, 16 per tile. The image is the asset `"sprites/sprite_<type>_<frame-1>.png"`
  (already in `android/app/src/main/assets/sprites/`, 61 PNGs, 32×32 to 48×48 px).
- `SimLoop.kt` line 65–67: the tick loop copies tiles and posts them to the UI:
  `MicropolisNative.copyTiles(handle, buf)` … `ui.post { mapView.update(tilesCopy); minimap.update(tilesCopy) }`.
- `MapView.kt`: line 24 loads the tile atlas with `BitmapFactory.decodeStream(context.assets.open("tiles.png"))`;
  line 117 `fun update(newTiles: ShortArray)`; lines 281–315 `onDraw`: `canvas.translate(panX, panY)`,
  `canvas.scale(scale, scale)`, tiles drawn at `x * tileSize`, then the overlay tint, then
  `canvas.restore()` (line 315).
- Do NOT read all of `MainActivity.kt`; you do not need it.

## Interface

### 1. `MapView.kt` — additions only
```kotlin
private var sprites = IntArray(0)          // [type, frame, left, top] * spriteCount
private var spriteCount = 0
private val spriteBitmaps = HashMap<String, Bitmap?>()   // asset name -> bitmap (null = missing)

/** New sprite list from the sim (see MicropolisNative.copySprites). */
fun updateSprites(data: IntArray, count: Int) {
    sprites = data; spriteCount = count
    invalidate()
}

private fun spriteBitmap(type: Int, frame: Int): Bitmap? {
    val name = "sprites/sprite_${type}_${frame - 1}.png"
    return spriteBitmaps.getOrPut(name) {
        try { context.assets.open(name).use { BitmapFactory.decodeStream(it) } } catch (e: Exception) { null }
    }
}
```
In `onDraw`, **after the overlay tint loop and before `canvas.restore()`**, draw each
sprite in the same (translated + scaled) canvas:
```kotlin
val px = tileSize / 16f                       // screen units per world pixel
for (i in 0 until spriteCount) {
    val bmp = spriteBitmap(sprites[i * 4], sprites[i * 4 + 1]) ?: continue
    val left = sprites[i * 4 + 2] * px
    val top = sprites[i * 4 + 3] * px
    dstRect.set(left, top, left + bmp.width * px, top + bmp.height * px)
    canvas.drawBitmap(bmp, null, dstRect, paint)
}
```

### 2. `SimLoop.kt` — additions only
Add a field-free buffer local to the tick: right after the `copyTiles` line (65), add
```kotlin
val spriteBuf = IntArray(4 * 32)
val nSprites = MicropolisNative.copySprites(handle, spriteBuf)
```
and in the existing `ui.post { … }` on line 67, also call `mapView.updateSprites(spriteBuf, nSprites)`.

## Files in scope
- `android/app/src/main/java/micropolis/port/MapView.kt`: additions above.
- `android/app/src/main/java/micropolis/port/SimLoop.kt`: additions above.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Planner verifies on device: Simulation → Disasters → Monster / Tornado shows the
  moving sprite on the map; a city with rail shows trains.

## Constraints
- **Copy the code blocks above exactly.** Everything you need is in this spec. Do not
  read engine sources (`MicropolisCore/`) or git history, and read only the line ranges
  named above.
- Only ADD code. Do not delete, re-indent or restructure existing code. Do not edit this spec.
- Do not change C/C++ code, CMake, `build.gradle.kts`, or the assets.
- Commit when green; stage only your files by name. Do not use `git checkout`, `git reset`, or `git add -A`.
