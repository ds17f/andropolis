# Task 004: Real tile rendering

## Goal
Draw the real Micropolis tile graphics instead of the placeholder color palette,
so terrain and built structures (parks, roads, zones) look correct.

## Context
`MapView` currently colors each tile with a guessed palette (`colorFor`). Replace
that with the real tile atlas.

The atlas is in the submodule: `MicropolisCore/content/micropolis/images/tiles.png`.
It is **256×960 px**: 16×16-pixel tiles in a **16-column, 60-row grid = 960 tiles**,
**row-major** (tile 0 is top-left, tile 1 is to its right, tile 16 starts the next
row). This matches the engine's tile set.

You must:
1. Copy that PNG into the app assets at `android/app/src/main/assets/tiles.png`
   (copy the bytes exactly; do not resize or re-encode).
2. Render from it in `MapView`.

Tile lookup: a map tile value's low 10 bits are the tile index:
`idx = value and 0x03FF`. Valid indices are 0..959; if `idx >= 960`, use 0. In the
atlas: `col = idx % 16`, `row = idx / 16`; the source rectangle is
`(col*16, row*16)` with size `16×16`.

## Interface (implementation notes for MapView.kt)
- Load the atlas once (e.g. an `init` block):
  `BitmapFactory.decodeStream(context.assets.open("tiles.png"))`.
- Add a reusable `Rect` for the source and a `RectF` for the destination. Set
  `paint.isFilterBitmap = false` so tiles stay crisp when scaled.
- In `onDraw`, for each tile `(x, y)`: compute `idx`, set the source rect to the
  atlas cell, set the destination rect to the screen cell (the existing `cw`/`ch`
  give cell size), and call `canvas.drawBitmap(atlas, src, dst, paint)`.
- Remove the `colorFor` function and its use.

## Files in scope
- `android/app/src/main/java/micropolis/port/MapView.kt`: edit.
- `android/app/src/main/assets/tiles.png`: create (copy from the submodule path
  above).

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Do not run the emulator yourself; the planner verifies the look on the device.

## Constraints
- Change only the two paths above.
- Copy the atlas exactly; do not modify the image.
- Do not change the engine, the JNI, the C ABI, the CMake files, or other Kotlin.
- No new dependencies. Commit when green; stage only your files by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
