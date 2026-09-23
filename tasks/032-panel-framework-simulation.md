# Task 032: Bottom-panel framework + Simulation pill

## Goal
Build the reusable **tabbed bottom-panel** and a row of pills that open panels (the
pattern the user likes — like the tool pill). Wire the first pill, **Simulation**
(Speed + Disasters tabs), and move the speed control off the top bar into it. City and
Overlay pills open placeholder panels for now (filled by later tasks).

## Context (MainActivity.kt)
- Bottom bar `bottom` (horizontal) holds `toolPill` (weight 1) + `undoBtn`, added to the
  vertical `root` (order: `topBar`, `mapView`, `bottom`).
- Top bar `row1` has `playPauseBtn`, `speedChip`, `overflowBtn`. Speed state:
  `speed` (0=Pause,1=Slow,2=Med,3=Fast), `speedNames`, `lastRunSpeed`,
  `updatePlayPauseText()`, `updateSpeedChipText()` (currently sets `speedChip.text`).
- Palette uses `com.google.android.material.bottomsheet.BottomSheetDialog` (see
  `openPalette()`); `roundedBg(color,radiusDp)`, `dp(v)` exist.
- 028 added `MicropolisNative.makeDisaster(handle, kind)` — kinds: 0 Fire, 1 Flood,
  2 Tornado, 3 Earthquake, 4 Monster, 5 Meltdown. All engine calls run on `sim`.

## Interface

### 1. Panel framework (add near the top-level, e.g. below `ToolItem`)
```kotlin
class PanelTab(val title: String, val build: () -> View)
```
And the reusable opener:
```kotlin
private fun showPanel(title: String, tabs: List<PanelTab>) {
    val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(16), dp(20), dp(24)); setBackgroundColor(0xFF12161C.toInt())
    }
    col.addView(TextView(this).apply {
        text = title; setTextColor(0xFFEEF2F6.toInt()); textSize = 18f
        setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 0, 0, dp(12))
    })
    val content = android.widget.FrameLayout(this)
    if (tabs.size > 1) {
        val tabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0,0,0,dp(12)) }
        val chips = ArrayList<TextView>()
        fun select(idx: Int) {
            content.removeAllViews()
            val sv = ScrollView(this); sv.addView(tabs[idx].build()); content.addView(sv)
            chips.forEachIndexed { i, c ->
                val on = i == idx
                c.background = roundedBg(if (on) 0xFFF5A623.toInt() else 0x1FFFFFFF, 10)
                c.setTextColor(if (on) 0xFF1A1207.toInt() else 0xFF9AA7B4.toInt())
            }
        }
        tabs.forEachIndexed { i, t ->
            val chip = TextView(this).apply {
                text = t.title; textSize = 13f; setPadding(dp(14), dp(8), dp(14), dp(8))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(8) }
                setOnClickListener { select(i) }
            }
            chips.add(chip); tabRow.addView(chip)
        }
        col.addView(tabRow); col.addView(content); select(0)
    } else {
        val sv = ScrollView(this); sv.addView(tabs[0].build()); col.addView(sv)
    }
    sheet.setContentView(col); sheet.show()
}
```

### 2. Panel pill row (insert ABOVE the tool/undo row — additive)
Add fields: `private lateinit var panelBar: LinearLayout` and
`private lateinit var simState: TextView`. Add a helper that builds one pill:
```kotlin
private fun buildPill(glyph: String, label: String, state: TextView, onClick: () -> Unit): LinearLayout {
    val pill = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
        background = roundedBg(0xFF1A222A.toInt(), 18); setPadding(dp(12), dp(8), dp(12), dp(8))
        layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT).apply { weight = 1f; setMargins(dp(4),0,dp(4),0) }
        setOnClickListener { onClick() }
    }
    pill.addView(TextView(this).apply { text = glyph; textSize = 18f; setTextColor(0xFFF5A623.toInt()); setPadding(0,0,dp(8),0) })
    val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    info.addView(TextView(this).apply { text = label; setTextColor(0xFFEEF2F6.toInt()); textSize = 13f; setTypeface(null, android.graphics.Typeface.BOLD) })
    info.addView(state)
    pill.addView(info)
    return pill
}
```
Build the row where the bottom bar is assembled (after `bottom` is added to `root`):
```kotlin
simState = TextView(this).apply { text = speedNames[speed]; setTextColor(0xFF9AA7B4.toInt()); textSize = 11f }
val cityState = TextView(this).apply { text = "Budget · stats"; setTextColor(0xFF9AA7B4.toInt()); textSize = 11f }
val overlayState = TextView(this).apply { text = "Off"; setTextColor(0xFF9AA7B4.toInt()); textSize = 11f }
panelBar = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    setBackgroundColor(0xFF12161C.toInt()); setPadding(dp(8), dp(6), dp(8), dp(6))
}
panelBar.addView(buildPill("⏩", "Simulation", simState) { showSimulationPanel() })
panelBar.addView(buildPill("📊", "City", cityState) { showPanel("City", listOf(PanelTab("Soon", { TextView(this).apply { text = "City panel — coming soon"; setTextColor(0xFF9AA7B4.toInt()); setPadding(0,dp(8),0,dp(8)) } }))) })
panelBar.addView(buildPill("🗺", "Overlay", overlayState) { showPanel("Map overlay", listOf(PanelTab("Soon", { TextView(this).apply { text = "Overlays — coming soon"; setTextColor(0xFF9AA7B4.toInt()); setPadding(0,dp(8),0,dp(8)) } }))) })
// insert the pill row just above the tool/undo row
root.addView(panelBar, root.indexOfChild(bottom))
```

### 3. Simulation panel (Speed + Disasters)
```kotlin
private fun showSimulationPanel() {
    showPanel("Simulation", listOf(
        PanelTab("Speed") {
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                for (i in speedNames.indices) {
                    val on = speed == i
                    addView(Button(this@MainActivity).apply {
                        text = speedNames[i]
                        background = roundedBg(if (on) 0xFFF5A623.toInt() else 0x1FFFFFFF, 12)
                        setTextColor(if (on) 0xFF1A1207.toInt() else 0xFFEEF2F6.toInt())
                        stateListAnimator = null
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
                        setOnClickListener {
                            speed = i; if (i > 0) lastRunSpeed = i
                            updatePlayPauseText(); updateSpeedChipText()
                        }
                    })
                }
            }
        },
        PanelTab("Disasters") {
            val names = listOf("Fire", "Flood", "Tornado", "Earthquake", "Monster", "Meltdown")
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                names.forEachIndexed { kind, n ->
                    addView(Button(this@MainActivity).apply {
                        text = n
                        background = roundedBg(0x1FFFFFFF, 12); setTextColor(0xFFEEF2F6.toInt()); stateListAnimator = null
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
                        setOnClickListener { sim.post { MicropolisNative.makeDisaster(handle, kind) } }
                    })
                }
            }
        }
    ))
}
```

### 4. Remove the top-bar speed chip (localized)
- Delete the `speedChip` field (`private lateinit var speedChip: Button`), its whole
  construction block, and the `row1.addView(speedChip, ...)` line. KEEP `playPauseBtn`.
- Change `updateSpeedChipText()` to update the pill instead:
  ```kotlin
  private fun updateSpeedChipText() { simState.text = speedNames[speed] }
  ```
  (Every current caller stays valid; it now refreshes the Simulation pill's state line.)

## Files in scope
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- The planner verifies on device: three pills appear above the tool row; Simulation
  opens a tabbed panel; Speed changes the sim speed and the pill's state line; Disasters
  trigger; City/Overlay open placeholder panels.

## Constraints
- Change only `MainActivity.kt`. Do not change the JNI, C ABI, engine, `MapView`,
  drawables, or `build.gradle.kts`. Keep play/pause, tools, undo, autosave working.
- No new dependencies. Commit when green; stage only your file by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
