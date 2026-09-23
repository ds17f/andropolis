# Task 036: Minimap overview (viewport rect + tap-to-jump)

## Goal
Add a small **minimap** in the map's top-right corner showing the whole city, with a
rectangle marking the visible area. Tapping/dragging on the minimap jumps the main view
there. This is the compact "overview + zoomed view" that eases navigation and building.

## Context
- `MapView` renders the 120×100 tile map (atlas `assets/tiles.png`, 16×16 tiles, 16 cols).
  Tile index = `tiles[x * rows + y].toInt() and 0x03FF`. It has private `panX,panY,scale,
  tileSize,cols,rows` and `clampPan()`. `MicropolisNative.mapWidth()/mapHeight()` = 120/100.
- The map is added to the vertical `root` as: `root.addView(mapView, LinearLayout.LayoutParams(
  MATCH_PARENT, 0, 1f))`. `tickLoop()` refreshes the map via `ui.post { mapView.update(tilesCopy) }`.
- `dp(v)` exists in MainActivity.

## Interface

### 1. New file `MinimapView.kt`
```kotlin
package micropolis.port

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/** Whole-map overview: a downscaled colour image of the tiles + a viewport rectangle. */
class MinimapView(context: Context) : View(context) {
    private val cols = MicropolisNative.mapWidth()
    private val rows = MicropolisNative.mapHeight()
    private val mini = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
    private val pixels = IntArray(cols * rows)
    private val lut = IntArray(1024)
    private val srcRect = Rect(0, 0, cols, rows)
    private val dstRect = RectF()
    private val bmpPaint = Paint().apply { isFilterBitmap = true }
    private val frame = Paint().apply { color = 0xFFF5A623.toInt(); style = Paint.Style.STROKE; strokeWidth = 3f }
    private val border = Paint().apply { color = 0xFF0B0E12.toInt(); style = Paint.Style.STROKE; strokeWidth = 4f }
    private var vpL = 0f; private var vpT = 0f; private var vpR = 0f; private var vpB = 0f
    var onTileSelected: ((Int, Int) -> Unit)? = null

    init {
        // representative colour per tile = centre pixel of its 16x16 atlas cell
        val atlas = BitmapFactory.decodeStream(context.assets.open("tiles.png"))
        for (idx in 0 until 960) {
            val c = idx % 16; val r = idx / 16
            lut[idx] = atlas.getPixel(c * 16 + 8, r * 16 + 8)
        }
        atlas.recycle()
    }

    fun update(tiles: ShortArray) {
        for (x in 0 until cols) {
            val base = x * rows
            for (y in 0 until rows) {
                val idx = tiles[base + y].toInt() and 0x03FF
                pixels[y * cols + x] = lut[if (idx >= 960) 0 else idx]
            }
        }
        mini.setPixels(pixels, 0, cols, 0, 0, cols, rows)
        postInvalidate()
    }

    /** Visible region in TILE coords, from MapView. Dedup to avoid redraw churn. */
    fun setViewport(l: Float, t: Float, r: Float, b: Float) {
        if (l == vpL && t == vpT && r == vpR && b == vpB) return
        vpL = l; vpT = t; vpR = r; vpB = b; postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        dstRect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawBitmap(mini, srcRect, dstRect, bmpPaint)
        val sx = width.toFloat() / cols; val sy = height.toFloat() / rows
        canvas.drawRect(vpL * sx, vpT * sy, vpR * sx, vpB * sy, frame)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), border)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val tx = (event.x / width * cols).toInt().coerceIn(0, cols - 1)
                val ty = (event.y / height * rows).toInt().coerceIn(0, rows - 1)
                onTileSelected?.invoke(tx, ty)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
```

### 2. MapView.kt — center-on-tile + report the viewport
```kotlin
var onViewportChanged: ((Float, Float, Float, Float) -> Unit)? = null

fun centerOnTile(tx: Int, ty: Int) {
    panX = width / 2f - (tx + 0.5f) * tileSize * scale
    panY = height / 2f - (ty + 0.5f) * tileSize * scale
    clampPan(); invalidate()
}
```
At the very END of `onDraw` (after the ghost/overlay drawing, where `tileSize` is set), report
the visible region in tile coords:
```kotlin
onViewportChanged?.invoke(
    (-panX / scale) / tileSize,
    (-panY / scale) / tileSize,
    ((width - panX) / scale) / tileSize,
    ((height - panY) / scale) / tileSize
)
```

### 3. MainActivity.kt — float the minimap over the map + wire it
- Add field `private lateinit var minimap: MinimapView`.
- Replace `root.addView(mapView, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))` with a
  FrameLayout wrapper so the minimap floats in the corner:
  ```kotlin
  minimap = MinimapView(this)
  val mapContainer = android.widget.FrameLayout(this)
  mapContainer.addView(mapView, android.widget.FrameLayout.LayoutParams(
      android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
      android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
  mapContainer.addView(minimap, android.widget.FrameLayout.LayoutParams(dp(120), dp(100)).apply {
      gravity = android.view.Gravity.TOP or android.view.Gravity.END
      setMargins(0, dp(8), dp(8), 0)
  })
  root.addView(mapContainer, LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
  ```
- Wire the two directions (after both views exist):
  ```kotlin
  minimap.onTileSelected = { tx, ty -> mapView.centerOnTile(tx, ty) }
  mapView.onViewportChanged = { l, t, r, b -> minimap.setViewport(l, t, r, b) }
  ```
- In `tickLoop`, refresh the minimap alongside the map: change
  `ui.post { mapView.update(tilesCopy) }` to
  `ui.post { mapView.update(tilesCopy); minimap.update(tilesCopy) }`.

## Files in scope
- `android/app/src/main/java/micropolis/port/MinimapView.kt`: new.
- `android/app/src/main/java/micropolis/port/MapView.kt`: edit.
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- The planner verifies on device: a minimap shows in the corner; the amber rectangle
  tracks the visible area while panning/zooming; tapping the minimap jumps the main view.

## Constraints
- Change only the files above. Do not change the JNI, C ABI, engine, drawables, or
  `build.gradle.kts`. Keep the map render, gestures, overlays, ghost, and pills working.
- No new dependencies. Commit when green; stage only your files by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
