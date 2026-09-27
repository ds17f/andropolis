# Task 068: Blink a lightning-bolt icon over unpowered zones

## Goal
Restore the classic SimCity feedback: any zone whose center tile lacks power blinks
a lightning-bolt icon over it, so the player can spot unpowered zones on sight.

## Context
- Every tile in the engine's map packs status flags into the high bits alongside the
  tile index (`MicropolisCore/packages/micropolis-engine/src/tool.h` lines 115-129):
  `PWRBIT = 0x8000` (tile has power), `ZONEBIT = 0x0400` (tile is the center tile of
  a zone), `LOMASK = 0x03ff` (mask for the tile index itself).
- `MicropolisNative.copyTiles` (`android/app/src/main/java/micropolis/port/MicropolisNative.kt`
  line ~14, backed by `micropolis_copy_tiles` in `android/engine/src/micropolis_c.cpp`
  lines 181-192) already copies the RAW tile values, flags included — nothing needs to
  change there. `MapView.update()` stores that raw `ShortArray` in `tiles`
  (`android/app/src/main/java/micropolis/port/MapView.kt` line ~146); `onDraw`
  (lines 338-356) only masks off the low 10 bits (`and 0x03FF`) to look up the atlas
  cell — the flag bits are still sitting in `tiles[base + y]` untouched.
- The tile atlas (`assets/tiles.png`, 16 cols × 60 rows, indices 0-959) already
  contains the classic lightning-bolt glyph at index 827
  (`LIGHTNINGBOLT = 827` in `MicropolisCore/packages/micropolis-engine/src/micropolis.h`
  line 623) — same atlas layout the rest of `onDraw` already indexes into
  (`col = idx % 16; row = idx / 16`). No asset work needed.
- This is a pure display-time substitution: never write the substitution back into
  `tiles` or into the engine's map. Rule (matches the original game's behaviour,
  documented in
  `MicropolisCore/documentation/designs/playable-pie-publishing-cauldron/playbooks-sprites/SB-09-unpowered-zone-blink.md`):
  `(tile & ZONEBIT) && !(tile & PWRBIT) && blinkPhase → draw LIGHTNINGBOLT at (x, y)`.
  Prior art cited there for the blink cadence: the original Java client used a 500 ms
  timer. Use that: `(System.currentTimeMillis() / 500) % 2 == 0L` as `blinkPhase`,
  computed once per `onDraw` call (not per tile).
- Draw this as its own pass in `onDraw`, right after the base tile-draw loop (after
  line 356, before the overlay-heatmap pass) — same nested `x`/`y` loop shape as the
  base tile loop, same `srcRect`/`dstRect` reuse, just gated on the flag check and
  skipped entirely when `blinkPhase` is off (no work to do on the off half of the cycle).

## Interface (copy exactly)
```kotlin
// MapView.kt — top-level constants near the other tile constants, or as companion
// object members. Values from tool.h / micropolis.h (see Context).
private const val ZONEBIT = 0x0400
private const val PWRBIT = 0x8000
private const val LIGHTNINGBOLT = 827

// MapView.kt — onDraw, inserted after the base tile-draw loop (after line 356),
// before the "Draw overlay heatmap tint" block.
if ((System.currentTimeMillis() / 500) % 2 == 0L) {
    val boltCol = LIGHTNINGBOLT % 16
    val boltRow = LIGHTNINGBOLT / 16
    srcRect.set(boltCol * 16, boltRow * 16, boltCol * 16 + 16, boltRow * 16 + 16)
    for (x in 0 until cols) {
        val base = x * rows
        for (y in 0 until rows) {
            val raw = tiles[base + y].toInt()
            if ((raw and ZONEBIT) != 0 && (raw and PWRBIT) == 0) {
                dstRect.set(x * tileSize, y * tileSize, (x + 1) * tileSize, (y + 1) * tileSize)
                canvas.drawBitmap(atlas, srcRect, dstRect, paint)
            }
        }
    }
}
```

## Files in scope
- `android/app/src/main/java/micropolis/port/MapView.kt`: add the constants and the
  blink-draw pass in `onDraw`, exactly as shown above. Change nothing else.

## Definition of done
- Build: `make build`
- Test (manual, no automated test exists for this): `make run`. Build a zone (e.g. a
  residential zone) with no power plant or power line connected to it, so its center
  tile has `ZONEBIT` set and `PWRBIT` clear. Take two screenshots roughly 500-1000ms
  apart (`make screenshot`). The lightning-bolt icon must be visible over the zone's
  center tile in one screenshot and absent in the other (i.e. it is actually
  blinking, not always on or always off). A zone that IS connected to power must
  never show the icon.

## Constraints
- Do not change any file under `MicropolisCore/`.
- Do not change `micropolis_copy_tiles`, `MicropolisNative.kt`, or any other file —
  the raw flag bits this needs are already present in `MapView.tiles`.
- Do not add a new dependency or a new asset — `LIGHTNINGBOLT` (827) is already in
  `assets/tiles.png`.
- Do not write the substituted tile value back into `tiles` or call any engine
  setter — this must be purely a draw-time overlay, so it can never desync from the
  engine's map or get saved into a city file.
