# Task 069: Fire and police coverage overlays, and an overlay legend

## Goal
Add two map overlays, "Fire" and "Police", that show the coverage of fire stations
and police stations. Also add a legend over the map that shows the colour scale of
the active overlay.

## Context
- The overlay system already exists. `micropolis_copy_overlay` in
  `android/engine/src/micropolis_c.cpp` fills a byte per tile (0..255) from an engine
  map. The Kotlin side shows one card per entry in `overlayNames`, and the card index
  is the overlay kind. `MapView` tints each tile with `heatLut[value]`.
- The engine keeps two coverage maps (both `MapShort8`, 1/8 resolution):
  `e->sim->fireStationEffectMap` and `e->sim->policeStationEffectMap`.
  `worldGet(x, y)` takes tile coordinates, as the other cases already do.
- Their values are `short` and can be more than 255 where stations overlap. Clamp the
  value to 0..255. Do not use a plain `(unsigned char)` cast, because it wraps.
- The map sits in `mapContainer` (a FrameLayout) in `MainLayout.kt`. The message
  banner is top-left, the minimap is top-right, the tool FAB is bottom-right. The
  legend goes bottom-left.

## Interface (copy exactly)

### 1. `android/engine/include/micropolis_c.h`
Extend the enum (change the POWER line to end with a comma):
```c
    MICROPOLIS_OVERLAY_POWER      = 7,  /* powerGridMap        (full res)  */
    MICROPOLIS_OVERLAY_FIRE       = 8,  /* fireStationEffectMap   (1/8 res) */
    MICROPOLIS_OVERLAY_POLICE     = 9   /* policeStationEffectMap (1/8 res) */
```

### 2. `android/engine/src/micropolis_c.cpp`
Add two cases to the switch in `micropolis_copy_overlay`, before `default:`:
```cpp
                case MICROPOLIS_OVERLAY_FIRE: {
                    int v = e->sim->fireStationEffectMap.worldGet(x, y);
                    val = (unsigned char)(v < 0 ? 0 : (v > 255 ? 255 : v));
                    break;
                }
                case MICROPOLIS_OVERLAY_POLICE: {
                    int v = e->sim->policeStationEffectMap.worldGet(x, y);
                    val = (unsigned char)(v < 0 ? 0 : (v > 255 ? 255 : v));
                    break;
                }
```

### 3. `android/app/src/main/java/micropolis/port/OverlayLegend.kt` (new file, copy exactly)
```kotlin
package micropolis.port

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Overlay intensity 0..255 -> ARGB heat colour (transparent at 0, green→yellow→red as it rises). */
internal val HEAT_LUT = IntArray(256) { i ->
    if (i == 0) 0 else {
        val t = i / 255f
        val r = (255 * kotlin.math.min(1f, t * 2f)).toInt()
        val g = (255 * kotlin.math.min(1f, (1f - t) * 2f)).toInt()
        val a = (60 + 140 * t).toInt().coerceIn(0, 200)
        (a shl 24) or (r shl 16) or (g shl 8)
    }
}

/** Scale end labels (low, high) per overlay kind; the index is the overlay kind. Empty low == on/off overlay. */
internal val OVERLAY_SCALE = arrayOf(
    "" to "",                   // 0 Off
    "Sparse" to "Dense",        // 1 Population
    "Light" to "Heavy",         // 2 Traffic
    "Clean" to "Polluted",      // 3 Pollution
    "Low" to "High",            // 4 Land value
    "Low" to "High",            // 5 Crime
    "Shrinking" to "Growing",   // 6 Growth
    "" to "Powered",            // 7 Power
    "Weak" to "Strong",         // 8 Fire
    "Weak" to "Strong"          // 9 Police
)

/** Key over the map: overlay name, colour bar, low/high labels. Hidden when kind == 0. */
internal class OverlayLegend(ctx: Context) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6202A36.toInt() }
    private val bar = Paint()
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEEF2F6.toInt(); textSize = 12f * d; isFakeBoldText = true
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9AA7B4.toInt(); textSize = 11f * d }
    private val r = RectF()
    private var kind = 0
    private var name = ""

    init { visibility = GONE }

    /** Show the key for this overlay kind, or hide it when kind == 0. */
    fun show(kind: Int, name: String) {
        this.kind = kind; this.name = name
        visibility = if (kind == 0) GONE else VISIBLE
        invalidate()
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension((150 * d).toInt(), (58 * d).toInt())

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val pad = 10 * d
        r.set(0f, 0f, w, height.toFloat())
        canvas.drawRoundRect(r, 10 * d, 10 * d, bg)
        canvas.drawText(name, pad, 18 * d, title)
        val (lo, hi) = OVERLAY_SCALE.getOrElse(kind) { "Low" to "High" }
        val top = 24 * d; val bottom = 34 * d; val span = w - 2 * pad
        if (lo.isEmpty()) {
            // On/off overlay (Power): one swatch of the full-intensity colour
            bar.color = HEAT_LUT[255] or (0xFF shl 24)
            canvas.drawRect(pad, top, pad + span, bottom, bar)
        } else {
            val n = 64
            for (i in 0 until n) {
                bar.color = HEAT_LUT[1 + i * 254 / (n - 1)] or (0xFF shl 24)
                canvas.drawRect(pad + span * i / n, top, pad + span * (i + 1) / n + 1f, bottom, bar)
            }
        }
        label.textAlign = Paint.Align.LEFT; canvas.drawText(lo, pad, 50 * d, label)
        label.textAlign = Paint.Align.RIGHT; canvas.drawText(hi, w - pad, 50 * d, label)
    }
}
```

### 4. `android/app/src/main/java/micropolis/port/MapView.kt`
The LUT moves to `OverlayLegend.kt` so the map and the legend use the same colours.
Find this block (near line 69):
```kotlin
    // intensity 0..255 -> ARGB heat colour (transparent at 0, green→yellow→red as it rises)
    private val heatLut = IntArray(256) { i ->
        ...
    }
```
Replace the whole block (the comment line and all lines through the closing `}` of
the `IntArray` lambda) with this one line:
```kotlin
    private val heatLut = HEAT_LUT
```
Change nothing else in `MapView.kt`.

### 5. `android/app/src/main/java/micropolis/port/MainActivity.kt`
Replace the `overlayNames` line:
```kotlin
    internal val overlayNames = arrayOf("Off","Population","Traffic","Pollution","Land value","Crime","Growth","Power","Fire","Police")
```
Add this line next to `internal lateinit var mapContainer: android.widget.FrameLayout`:
```kotlin
    internal lateinit var overlayLegend: OverlayLegend
```

### 6. `android/app/src/main/java/micropolis/port/MainLayout.kt`
Directly after the `mapContainer.addView(messageBanner, ...)` call (it ends with
`})`), add:
```kotlin
    overlayLegend = OverlayLegend(this)
    mapContainer.addView(overlayLegend, android.widget.FrameLayout.LayoutParams(
        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.START
            setMargins(dp(8), 0, 0, dp(8))
        })
```

### 7. `android/app/src/main/java/micropolis/port/Panels.kt`
In `showOverlayPanel`, replace the `glyphs` line:
```kotlin
    val glyphs = arrayOf("⊘","👥","🚗","☁","💲","🚨","📈","⚡","🚒","👮")
```
In the card click handler, after `currentOverlay = kind; overlayState.text = name`, add:
```kotlin
                overlayLegend.show(kind, name)
```

## Files in scope
- `android/engine/include/micropolis_c.h`: edit. Add the two enum values.
- `android/engine/src/micropolis_c.cpp`: edit. Add the two switch cases.
- `android/app/src/main/java/micropolis/port/OverlayLegend.kt`: create.
- `android/app/src/main/java/micropolis/port/MapView.kt`: edit. Replace the LUT block with one line.
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit. Extend `overlayNames`, add `overlayLegend`.
- `android/app/src/main/java/micropolis/port/MainLayout.kt`: edit. Add the legend to `mapContainer`.
- `android/app/src/main/java/micropolis/port/Panels.kt`: edit. Extend `glyphs`, call `overlayLegend.show`.
Change only these files. Use the Edit tool for each change. Do not use scripts or
regex to edit files.

## Definition of done
- Build: `make build`. It must succeed.
- Commit the change when the build is green.

## Constraints
- Do not change the engine sources under `MicropolisCore/`.
- Do not change the JNI file or `SimLoop.kt`. They already handle any overlay kind.
- Do not add a new dependency.
