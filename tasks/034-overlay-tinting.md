# Task 034: Map overlays — Overlay pill + heatmap tinting

## Goal
Make the **Overlay** pill switch the map into a data view: population, traffic,
pollution, land value, crime, growth, or power — drawn as a translucent heatmap tint
over the tiles, refreshed as the sim runs.

## Context
- `MapView.onDraw` draws the 120×100 tiles inside a `canvas.save()/translate(panX,panY)/
  scale(scale,scale)` block, then `canvas.restore()` and the build ghost. Tile data is
  column-major: `tiles[x * rows + y]`. `cols`, `rows`, `tileSize` are fields.
- 028 gives `MicropolisNative.copyOverlay(handle, kind, ByteArray(12000))` → per-tile
  intensity 0..255, SAME column-major layout (`dst[x*rows+y]`). Overlay kinds:
  0 none, 1 population, 2 traffic, 3 pollution, 4 land value, 5 crime, 6 growth, 7 power.
- `MainActivity.tickLoop()` runs on `sim` every 100ms; it already does
  `copyTiles(handle, buf)` then `mapView.update(buf.copyOf())` on `ui`. The
  `showPanel(title, tabs)` framework exists. The Overlay pill (in `panelBar`) currently
  opens a placeholder; its state line is a TextView created there (see note in step 3).

## Interface

### 1. MapView.kt — overlay state + heatmap LUT
Add:
```kotlin
private var overlayMode = 0                 // 0 == off
private var overlayData: ByteArray? = null
private val overlayPaint = Paint()
// intensity 0..255 -> ARGB heat colour (transparent at 0, green→yellow→red as it rises)
private val heatLut = IntArray(256) { i ->
    if (i == 0) 0 else {
        val t = i / 255f
        val r = (255 * kotlin.math.min(1f, t * 2f)).toInt()
        val g = (255 * kotlin.math.min(1f, (1f - t) * 2f)).toInt()
        val a = (60 + 140 * t).toInt().coerceIn(0, 200)
        (a shl 24) or (r shl 16) or (g shl 8)
    }
}

fun setOverlay(mode: Int, data: ByteArray?) {
    overlayMode = mode
    overlayData = data
    postInvalidate()
}
```

### 2. MapView.kt — draw the tint inside the transform
Inside `onDraw`, AFTER the tile-drawing loop but BEFORE `canvas.restore()`, add:
```kotlin
val ov = overlayData
if (overlayMode != 0 && ov != null && ov.size >= cols * rows) {
    for (x in 0 until cols) {
        val base = x * rows
        for (y in 0 until rows) {
            val v = ov[base + y].toInt() and 0xFF
            if (v == 0) continue
            overlayPaint.color = heatLut[v]
            dstRect.set(x * tileSize, y * tileSize, (x + 1) * tileSize, (y + 1) * tileSize)
            canvas.drawRect(dstRect, overlayPaint)
        }
    }
}
```

### 3. MainActivity.kt — overlay pill panel + per-tick refresh
- Promote the Overlay pill's state TextView to a field so it can be updated: change the
  local `val overlayState = TextView(...)` (created where `panelBar` is built) to assign a
  new field `private lateinit var overlayState: TextView` (i.e. `overlayState = TextView(...)`).
- Add fields:
  ```kotlin
  private var currentOverlay = 0
  private val overlayBuf = ByteArray(120 * 100)
  private val overlayNames = arrayOf("Off","Population","Traffic","Pollution","Land value","Crime","Growth","Power")
  ```
- Change the Overlay pill's `onClick` to `showOverlayPanel()`:
  ```kotlin
  private fun showOverlayPanel() {
      showPanel("Map overlay", listOf(PanelTab("Mode") {
          LinearLayout(this).apply {
              orientation = LinearLayout.VERTICAL
              overlayNames.forEachIndexed { kind, name ->
                  addView(Button(this@MainActivity).apply {
                      text = name
                      val on = kind == currentOverlay
                      background = roundedBg(if (on) 0xFFF5A623.toInt() else 0x1FFFFFFF, 12)
                      setTextColor(if (on) 0xFF1A1207.toInt() else 0xFFEEF2F6.toInt())
                      stateListAnimator = null
                      layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                          LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
                      setOnClickListener {
                          currentOverlay = kind
                          overlayState.text = name
                          if (kind == 0) mapView.setOverlay(0, null)
                      }
                  })
              }
          }
      }))
  }
  ```
- In `tickLoop()`, after the existing tile copy/update, refresh the overlay when one is active:
  ```kotlin
  if (currentOverlay != 0 && cityReady && handle != 0L) {
      MicropolisNative.copyOverlay(handle, currentOverlay, overlayBuf)
      val snap = overlayBuf.copyOf()
      ui.post { mapView.setOverlay(currentOverlay, snap) }
  }
  ```

## Files in scope
- `android/app/src/main/java/micropolis/port/MapView.kt`: edit.
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- The planner verifies on device: Overlay pill lists the modes; picking one tints the map
  (e.g. Population shows a heatmap); Off clears it; the state line shows the mode.

## Constraints
- Change only the two files above. Do not change the JNI, C ABI, engine, drawables, or
  `build.gradle.kts`. Keep tile rendering, gestures, ghost, and the other pills working.
- No new dependencies. Commit when green; stage only your files by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
