# Task 042: City panel redesign — Evaluation-first tabs, stat bars, live budget

## Goal
Rework the **City** panel and move city windows out of the ⋮ menu:
1. Tabs become **Evaluation (default) · Budget · Stats**, each with an icon.
2. **Stats** shows labeled bars (0–100 + a word) instead of opaque numbers.
3. **Budget** shows live projections (tax income / spend / net) that update as sliders move.
4. Remove **Budget**, **City evaluation**, and **Tax rate** from the ⋮ menu (now in City).

## Context (MainActivity.kt)
- `class PanelTab(val title: String, val build: () -> View)` and `showPanel(title, tabs)`
  render text-only tab chips (`text = t.title`).
- `showCityPanel()` gathers data on `sim` then calls `showCityPanelUI(d: CityData)`.
- `getBudget(handle, IntArray(12))` → `[0 totalFunds, 1 taxRate, 2 taxIncome, 3 roadFund,
  4 roadSpend, 5 roadPct, 6 policeFund, 7 policeSpend, 8 policePct, 9 fireFund, 10 fireSpend,
  11 firePct]` (Fund = amount needed at 100%; percents 0..100).
- `getEvaluation(handle, IntArray(7))` → `[0 score, 1 scoreDelta, 2 cityClass, 3 pop,
  4 popDelta, 5 assessedValue, 6 approval]`.
- `setCityTax(handle, tax)` (0..20), `setFunding(handle, road, fire, police)` (0..100).
- `avgOverlay(kind)` returns 0..255 for kinds 1 pop, 2 traffic, 3 pollution, 4 landValue,
  5 crime. `cityClassNames`, `roundedBg`, `dp` exist.
- The ⋮ `PopupMenu` adds `"Budget"`, `"City evaluation"`, `"Tax rate — ${...}%"` and handles
  them in `when (item.title)` (plus a `startsWith("Tax")` branch in the `else`).

## Interface

### 1. Tab icons (PanelTab + showPanel)
- Change the class to `class PanelTab(val title: String, val glyph: String = "", val build: () -> View)`.
  (Default glyph keeps every existing `PanelTab("X") { ... }` caller compiling.)
- In `showPanel`, where the chip text is set, use:
  `text = if (t.glyph.isEmpty()) t.title else "${t.glyph}  ${t.title}"`.

### 2. A labeled stat bar (add helper)
```kotlin
private fun levelWord(v: Int) = when {           // v is 0..255
    v < 26 -> "None"; v < 77 -> "Low"; v < 128 -> "Medium"; v < 191 -> "High"; else -> "Very high"
}
private fun statBar(label: String, v: Int): View {
    val pct = (v * 100 / 255).coerceIn(0, 100)
    val color = when { v < 77 -> 0xFF4CAF50.toInt(); v < 160 -> 0xFFF5A623.toInt(); else -> 0xFFE5533D.toInt() }
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(8))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(this@MainActivity).apply {
                text = label; setTextColor(0xFF9AA7B4.toInt()); textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(this@MainActivity).apply {
                text = levelWord(v); setTextColor(0xFFEEF2F6.toInt()); textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
        })
        // track + fill
        addView(FrameLayout(this@MainActivity).apply {
            background = roundedBg(0x1FFFFFFF, 6)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8)).apply { topMargin = dp(4) }
            addView(View(this@MainActivity).apply {
                background = roundedBg(color, 6)
                // width set after layout via a post: use weightless fixed fraction
                layoutParams = FrameLayout.LayoutParams(0, dp(8))
                post { layoutParams = FrameLayout.LayoutParams((width.let { (it) } ).let { (parent as View).width * pct / 100 }, dp(8)) }
            })
        })
    }
}
```
(If the `post{}` width trick is awkward, instead give the fill a horizontal LinearLayout with
two weighted spacers — a filled View with `layoutParams weight = pct` and an empty View with
`weight = 100 - pct` inside a horizontal container. Use whichever renders a proportional bar.)

### 3. Replace `showCityPanel()` / `showCityPanelUI(...)`
Gather raw arrays and build three tabs:
```kotlin
private fun showCityPanel() {
    sim.post {
        val b = IntArray(12); MicropolisNative.getBudget(handle, b)
        val ev = IntArray(7); MicropolisNative.getEvaluation(handle, ev)
        val crime = avgOverlay(5); val poll = avgOverlay(3); val land = avgOverlay(4)
        val traffic = avgOverlay(2); val density = avgOverlay(1)
        ui.post { showCityPanelUI(b, ev, crime, poll, land, traffic, density) }
    }
}

private fun showCityPanelUI(b: IntArray, ev: IntArray, crime: Int, poll: Int, land: Int, traffic: Int, density: Int) {
    fun row(k: String, v: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(7), 0, dp(7))
        addView(TextView(this@MainActivity).apply { text = k; setTextColor(0xFF9AA7B4.toInt()); textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        addView(TextView(this@MainActivity).apply { text = v; setTextColor(0xFFEEF2F6.toInt()); textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD) })
    }
    showPanel("City", listOf(
        PanelTab("Evaluation", "🏛") {
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(row("Class", cityClassNames.getOrElse(ev[2]) { "?" }))
                addView(row("Population", "${ev[3]}  (Δ ${ev[4]})"))
                addView(row("Score", "${ev[0]}  (Δ ${ev[1]})"))
                addView(row("Approval", "${ev[6]}%"))
                addView(row("Assessed value", "$${ev[5]}"))
            }
        },
        PanelTab("Budget", "💰") {
            var road = b[5]; var fire = b[11]; var police = b[8]; var tax = b[1]; val curTax = b[1]
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val proj = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; background = roundedBg(0xFF12161C.toInt(), 12)
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }
            val income = TextView(this).apply { setTextColor(0xFFEEF2F6.toInt()) }
            val rSpend = TextView(this).apply { setTextColor(0xFF9AA7B4.toInt()) }
            val fSpend = TextView(this).apply { setTextColor(0xFF9AA7B4.toInt()) }
            val pSpend = TextView(this).apply { setTextColor(0xFF9AA7B4.toInt()) }
            val net = TextView(this).apply { setTypeface(null, android.graphics.Typeface.BOLD) }
            proj.addView(TextView(this).apply { text = "Projection"; setTextColor(0xFF7D8B99.toInt()); textSize = 11f })
            proj.addView(income); proj.addView(rSpend); proj.addView(fSpend); proj.addView(pSpend); proj.addView(net)
            fun update() {
                val inc = if (curTax > 0) (b[2].toLong() * tax / curTax).toInt() else b[2]
                val rs = b[3] * road / 100; val fs = b[9] * fire / 100; val ps = b[6] * police / 100
                income.text = "Tax income: $$inc"; rSpend.text = "Roads: $$rs"
                fSpend.text = "Fire: $$fs"; pSpend.text = "Police: $$ps"
                val n = inc - rs - fs - ps
                net.text = "Net: ${if (n >= 0) "+" else ""}$$n"
                net.setTextColor(if (n >= 0) 0xFF4CAF50.toInt() else 0xFFE5533D.toInt())
            }
            fun slider(title: String, value: Int, max: Int, onLive: (Int) -> Unit, onApply: (Int) -> Unit): View {
                val head = TextView(this).apply { text = "$title: $value%"; setTextColor(0xFFEEF2F6.toInt()); setPadding(0, dp(10), 0, dp(2)) }
                return LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; addView(head)
                    addView(android.widget.SeekBar(this@MainActivity).apply {
                        this.max = max; progress = value
                        setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(s: android.widget.SeekBar, p: Int, u: Boolean) { head.text = "$title: $p%"; onLive(p); update() }
                            override fun onStartTrackingTouch(s: android.widget.SeekBar) {}
                            override fun onStopTrackingTouch(s: android.widget.SeekBar) { onApply(s.progress) }
                        })
                    })
                }
            }
            col.addView(row("Funds", "$${b[0]}"))
            col.addView(slider("Tax rate", tax, 20, { tax = it }, { sim.post { MicropolisNative.setCityTax(handle, it) } }))
            col.addView(slider("Road funding", road, 100, { road = it }, { sim.post { MicropolisNative.setFunding(handle, road, fire, police) } }))
            col.addView(slider("Fire funding", fire, 100, { fire = it }, { sim.post { MicropolisNative.setFunding(handle, road, fire, police) } }))
            col.addView(slider("Police funding", police, 100, { police = it }, { sim.post { MicropolisNative.setFunding(handle, road, fire, police) } }))
            col.addView(proj, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) })
            update()
            col
        },
        PanelTab("Stats", "📊") {
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(statBar("Crime", crime)); addView(statBar("Pollution", poll))
                addView(statBar("Land value", land)); addView(statBar("Traffic", traffic))
                addView(statBar("Population density", density))
            }
        }
    ))
}
```
(Remove the old `CityData` class and the old `showCityPanelUI(d: CityData)` / `levelName` if
now unused; leave `avgOverlay`.)

### 4. Remove from the ⋮ menu
Delete `pm.menu.add("Budget")`, `pm.menu.add("City evaluation")`, and
`pm.menu.add("Tax rate — ${taxRates[taxIdx]}%")`, plus their `when` branches (the
`"Budget" -> ...`, `"City evaluation" -> ...`, and the `else`'s `startsWith("Tax")` block —
leave a plain `else -> {}` or remove the tax branch only). Keep Redo / New city / Save city /
Load city / Annual report / Settings.

## Files in scope
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- The planner verifies on device: City opens on Evaluation with tab icons; Stats shows bars;
  Budget sliders update the projection live; the three items are gone from the ⋮ menu.

## Constraints
- Change only `MainActivity.kt`. Do not change the JNI, C ABI, engine, `MapView`, drawables,
  or `build.gradle.kts`. Keep the manual Save/Load, Annual report toggle, and Settings working.
- No new dependencies. Commit when green; stage only your file by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
