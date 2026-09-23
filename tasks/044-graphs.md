# Task 044: History graphs (City → Graphs tab)

## Goal
Add a **Graphs** tab to the City panel showing the city's history as line charts:
residential, commercial, industrial, money, crime, pollution over time, with a
10-year / 120-year scale toggle. Classic SimCity graph screen.

## Context
- 028 exposes `MicropolisNative.getHistory(handle, history, scale, IntArray(120)): Int`
  — fills 120 samples OLDEST-FIRST. `history`: 0 RES, 1 COM, 2 IND, 3 MONEY, 4 CRIME,
  5 POLLUTION. `scale`: 0 short (10-year), 1 long (120-year). Runs on `sim`.
- `showCityPanelUI(...)` builds the City tabs via `showPanel("City", listOf(PanelTab...))`.
  Add a fourth tab. `sim`/`ui`/`handle`, `dp`, `roundedBg` exist. Tabs support a glyph.

## Interface

### 1. New file `GraphView.kt`
```kotlin
package micropolis.port

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Draws several normalised line series (each a colour + 120 samples). */
class GraphView(context: Context) : View(context) {
    private var series: List<Pair<Int, IntArray>> = emptyList()
    private val line = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 3f }
    private val grid = Paint().apply { color = 0x1FFFFFFF; style = Paint.Style.STROKE; strokeWidth = 1f }
    private val path = Path()

    fun setSeries(s: List<Pair<Int, IntArray>>) { series = s; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        for (i in 0..4) { val y = h * i / 4f; canvas.drawLine(0f, y, w, y, grid) }   // 4 grid rows
        var maxV = 1
        for ((_, d) in series) for (v in d) if (v > maxV) maxV = v
        for ((color, d) in series) {
            if (d.isEmpty()) continue
            line.color = color
            path.reset()
            for (i in d.indices) {
                val x = if (d.size > 1) w * i / (d.size - 1) else 0f
                val y = h - (d[i].coerceAtLeast(0).toFloat() / maxV) * h
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, line)
        }
    }
}
```

### 2. MainActivity — add the Graphs tab
Colours + labels for the six series:
```kotlin
// (add near the top of the class)
private val histColors = intArrayOf(0xFF4CAF50.toInt(), 0xFF42A5F5.toInt(), 0xFFF5A623.toInt(),
    0xFF66BB6A.toInt(), 0xFFE5533D.toInt(), 0xFF9C6ADE.toInt())   // res, com, ind, money, crime, poll
private val histNames = arrayOf("Residential", "Commercial", "Industrial", "Money", "Crime", "Pollution")
```
Add this `PanelTab` to the `showPanel("City", listOf(...))` list in `showCityPanelUI` (after Stats):
```kotlin
PanelTab("Graphs", "📈") {
    val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val scaleState = intArrayOf(0)                    // 0 = 10yr, 1 = 120yr
    val graph = GraphView(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(180))
    }
    fun load() {
        sim.post {
            val s = ArrayList<Pair<Int, IntArray>>()
            for (t in 0..5) {
                val a = IntArray(120)
                MicropolisNative.getHistory(handle, t, scaleState[0], a)
                s.add(histColors[t] to a)
            }
            ui.post { graph.setSeries(s) }
        }
    }
    // scale toggle
    val toggle = Button(this).apply {
        text = "10-year"; background = roundedBg(0x1FFFFFFF, 10); setTextColor(0xFFEEF2F6.toInt())
        stateListAnimator = null
        setOnClickListener { scaleState[0] = 1 - scaleState[0]; text = if (scaleState[0] == 0) "10-year" else "120-year"; load() }
    }
    col.addView(toggle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
    col.addView(graph)
    // legend
    val legend = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(10), 0, 0) }
    for (i in histNames.indices) {
        legend.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, dp(3), 0, dp(3))
            addView(View(this@MainActivity).apply { background = roundedBg(histColors[i], 3)
                layoutParams = LinearLayout.LayoutParams(dp(14), dp(14)).apply { rightMargin = dp(8) } })
            addView(TextView(this@MainActivity).apply { text = histNames[i]; setTextColor(0xFF9AA7B4.toInt()); textSize = 13f })
        })
    }
    col.addView(legend)
    load()
    col
}
```

## Files in scope
- `android/app/src/main/java/micropolis/port/GraphView.kt`: new.
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit (add the Graphs tab + the two arrays).

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- The planner verifies on device: City has a Graphs tab; it draws coloured history lines;
  the 10-year/120-year toggle reloads the data.

## Constraints
- Change only the two files above. Do not change the JNI, C ABI, engine, `MapView`,
  `MinimapView`, drawables, or `build.gradle.kts`. Keep the other City tabs working.
- No new dependencies. Commit when green; stage only your files by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
