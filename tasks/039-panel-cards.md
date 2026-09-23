# Task 039: Style Simulation/Overlay panels as tool-style cards

## Goal
Make the Speed, Disasters, and Overlay buttons look and feel like the tool palette
cards (rounded card, an icon glyph in a tinted square, a label below, amber highlight
when selected) instead of plain full-width buttons.

## Context (MainActivity.kt)
- The tool palette (`buildToolCard`) is the target look: a vertical card, an icon in a
  rounded amber-tinted square, a label under it, in a 4-column `GridLayout`, with a
  selected card tinted amber.
- `showSimulationPanel()` builds a tabbed panel (Speed tab = 4 full-width buttons;
  Disasters tab = 6 buttons). `showOverlayPanel()` builds one tab of 8 buttons
  (`overlayNames`). Both use the `showPanel(title, tabs)` framework where each
  `PanelTab(title, build)` returns a `View`.
- State: `speed`/`speedNames`/`lastRunSpeed`, `updatePlayPauseText()`,
  `updateSpeedChipText()`; `MicropolisNative.makeDisaster(handle, kind)`;
  `currentOverlay`/`overlayNames`/`overlayState`, `mapView.setOverlay(kind, data)`.
- `roundedBg(color,radiusDp)`, `dp(v)` exist.

## Interface

### 1. A reusable card (add to MainActivity)
```kotlin
private class CardHandle(val view: LinearLayout, val setSelected: (Boolean) -> Unit)

/** A tool-style card: glyph in a tinted square + label; call setSelected to highlight. */
private fun panelCard(glyph: String, label: String, onClick: () -> Unit): CardHandle {
    val glyphTv = TextView(this).apply { text = glyph; textSize = 20f }
    val iconBox = LinearLayout(this).apply {
        gravity = android.view.Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
        addView(glyphTv)
    }
    val labelTv = TextView(this).apply {
        text = label; textSize = 11f; gravity = android.view.Gravity.CENTER
        setTextColor(0xFFCDD6E0.toInt()); setPadding(0, dp(6), 0, 0)
    }
    val card = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER_HORIZONTAL
        setPadding(dp(8), dp(10), dp(8), dp(10))
        addView(iconBox); addView(labelTv)
        setOnClickListener { onClick() }
    }
    val setSelected = { on: Boolean ->
        card.background = roundedBg(if (on) 0x33F5A623 else 0x0DFFFFFF, 14)
        iconBox.background = roundedBg(if (on) 0x55F5A623 else 0x22F5A623, 12)
        glyphTv.setTextColor(if (on) 0xFFF5A623.toInt() else 0xFFC3CCD6.toInt())
    }
    setSelected(false)
    return CardHandle(card, setSelected)
}

/** Put a card into a GridLayout cell (equal columns). */
private fun addCard(grid: android.widget.GridLayout, h: CardHandle) {
    val lp = android.widget.GridLayout.LayoutParams()
    lp.width = 0
    lp.columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
    lp.setMargins(dp(4), dp(4), dp(4), dp(4))
    h.view.layoutParams = lp
    grid.addView(h.view)
}
```

### 2. Speed tab → 4 cards (glyphs ⏸ ▶ ▶▶ ⏩)
Replace the Speed tab's button column with:
```kotlin
PanelTab("Speed") {
    val glyphs = arrayOf("⏸", "▶", "▶▶", "⏩")
    val grid = android.widget.GridLayout(this).apply { columnCount = 4 }
    val handles = ArrayList<CardHandle>()
    fun select(sel: Int) { handles.forEachIndexed { i, h -> h.setSelected(i == sel) } }
    for (i in speedNames.indices) {
        val h = panelCard(glyphs[i], speedNames[i]) {
            speed = i; if (i > 0) lastRunSpeed = i
            updatePlayPauseText(); updateSpeedChipText(); select(i)
        }
        handles.add(h); addCard(grid, h)
    }
    select(speed)
    grid
}
```

### 3. Disasters tab → 6 cards (glyphs 🔥 🌊 🌪 ⛰ 👾 ☢), no selected state
```kotlin
PanelTab("Disasters") {
    val names = arrayOf("Fire","Flood","Tornado","Earthquake","Monster","Meltdown")
    val glyphs = arrayOf("🔥","🌊","🌪","⛰","👾","☢")
    val grid = android.widget.GridLayout(this).apply { columnCount = 3 }
    names.forEachIndexed { kind, n ->
        addCard(grid, panelCard(glyphs[kind], n) { sim.post { MicropolisNative.makeDisaster(handle, kind) } })
    }
    grid
}
```

### 4. Overlay panel → 8 cards (glyphs ⊘ 👥 🚗 ☁ 💲 🚨 📈 ⚡)
Rework `showOverlayPanel()`'s single tab to a 4-column grid of cards; keep the current
selection highlighted and update `overlayState`/`mapView` on tap:
```kotlin
private fun showOverlayPanel() {
    val glyphs = arrayOf("⊘","👥","🚗","☁","💲","🚨","📈","⚡")
    showPanel("Map overlay", listOf(PanelTab("Mode") {
        val grid = android.widget.GridLayout(this).apply { columnCount = 4 }
        val handles = ArrayList<CardHandle>()
        fun select(sel: Int) { handles.forEachIndexed { i, h -> h.setSelected(i == sel) } }
        overlayNames.forEachIndexed { kind, name ->
            val h = panelCard(glyphs.getOrElse(kind) { "•" }, name) {
                currentOverlay = kind; overlayState.text = name
                if (kind == 0) mapView.setOverlay(0, null)
                select(kind)
            }
            handles.add(h); addCard(grid, h)
        }
        select(currentOverlay)
        grid
    }))
}
```

## Files in scope
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- The planner verifies on device: Speed/Disasters/Overlay show tool-style cards; the
  active speed and overlay are highlighted; taps still work.

## Constraints
- Change only `MainActivity.kt`. Do not change the JNI, C ABI, engine, `MapView`,
  drawables, or `build.gradle.kts`. Keep the tool palette and all actions working.
- No new dependencies. Commit when green; stage only your file by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
