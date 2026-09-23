# Task 033: City panel — Budget + Stats tabs

## Goal
Replace the **City** pill's placeholder with a real tabbed panel:
- **Budget**: change the tax rate and the road/fire/police funding levels.
- **Stats**: show the city's key numbers — population, class, approval, and the
  crime / pollution / land-value / traffic / density indexes.

## Context (MainActivity.kt)
- The `showPanel(title, tabs: List<PanelTab>)` framework exists (tabbed bottom sheet;
  `PanelTab(title, build)` where `build` returns a `View`). The City pill currently calls
  `showPanel("City", listOf(PanelTab("Soon", { ... })))` — REPLACE that pill's onClick
  with `showCityPanel()`.
- 028 data (all on the `sim` thread; marshal UI back with `ui.post`):
  - `getBudget(handle, IntArray(12))` → `[totalFunds, taxRate, taxIncome, roadFund,
    roadSpend, roadPct, policeFund, policeSpend, policePct, fireFund, fireSpend, firePct]`
    (percents are 0..100).
  - `setCityTax(handle, tax)` (0..20), `setFunding(handle, roadPct, firePct, policePct)` (0..100 each).
  - `getStats(handle, IntArray(10))` → `[.., cityPop(2), .., resDemand(6), comDemand(7), indDemand(8), ..]`.
  - `getEvaluation(handle, IntArray(7))` → `[score(0), scoreDelta(1), cityClass(2), pop(3),
    popDelta(4), assessedValue(5), approval(6)]`.
  - `copyOverlay(handle, kind, ByteArray(12000))` → per-tile intensity 0..255. Overlay kinds:
    1 population, 2 traffic, 3 pollution, 4 land value, 5 crime. Average the array (each
    byte as `b.toInt() and 0xFF`) to get a city-wide index.
- `roundedBg(color,radiusDp)`, `dp(v)`, `sim`, `ui`, `handle` exist.

## Interface

### 1. Gather a data snapshot, then show the panel
Reading engine data must happen on `sim`; build the panel on `ui` from the snapshot.
```kotlin
private class CityData(
    val funds: Int, val taxRate: Int,
    val roadPct: Int, val firePct: Int, val policePct: Int,
    val pop: Int, val cityClass: Int, val approval: Int,
    val crime: Int, val pollution: Int, val landValue: Int, val traffic: Int, val density: Int
)

private fun avgOverlay(kind: Int): Int {
    val a = ByteArray(120 * 100)
    val n = MicropolisNative.copyOverlay(handle, kind, a)
    if (n <= 0) return 0
    var sum = 0L; for (b in a) sum += (b.toInt() and 0xFF)
    return (sum / a.size).toInt()          // 0..255
}

private fun showCityPanel() {
    sim.post {
        val b = IntArray(12); MicropolisNative.getBudget(handle, b)
        val ev = IntArray(7); MicropolisNative.getEvaluation(handle, ev)
        val d = CityData(
            funds = b[0], taxRate = b[1], roadPct = b[5], policePct = b[8], firePct = b[11],
            pop = ev[3], cityClass = ev[2], approval = ev[6],
            crime = avgOverlay(5), pollution = avgOverlay(3), landValue = avgOverlay(4),
            traffic = avgOverlay(2), density = avgOverlay(1)
        )
        ui.post { showCityPanelUI(d) }
    }
}
```

### 2. The panel UI (Budget + Stats)
```kotlin
private val cityClassNames = arrayOf("Village","Town","City","Capital","Metropolis","Megalopolis")

private fun showCityPanelUI(d: CityData) {
    // live funding values the sliders mutate; apply all three together
    var road = d.roadPct; var fire = d.firePct; var police = d.policePct
    fun label(text: String) = TextView(this).apply {
        this.text = text; setTextColor(0xFFEEF2F6.toInt()); textSize = 14f; setPadding(0, dp(10), 0, dp(2))
    }
    fun muted(text: String) = TextView(this).apply {
        this.text = text; setTextColor(0xFF9AA7B4.toInt()); textSize = 13f; setPadding(0, dp(2), 0, dp(2))
    }
    // a labeled slider row; onApply(progress) runs when the user releases the thumb
    fun sliderRow(title: String, value: Int, max: Int, suffix: String, onApply: (Int) -> Unit): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val head = label("$title: $value$suffix")
        col.addView(head)
        col.addView(android.widget.SeekBar(this).apply {
            this.max = max; progress = value
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar, p: Int, fromUser: Boolean) { head.text = "$title: $p$suffix" }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar) {}
                override fun onStopTrackingTouch(sb: android.widget.SeekBar) { onApply(sb.progress) }
            })
        })
        return col
    }
    showPanel("City", listOf(
        PanelTab("Budget") {
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(muted("Funds: \$${d.funds}"))
                addView(sliderRow("Tax rate", d.taxRate, 20, "%") { v -> sim.post { MicropolisNative.setCityTax(handle, v) } })
                addView(sliderRow("Road funding", road, 100, "%") { v -> road = v; sim.post { MicropolisNative.setFunding(handle, road, fire, police) } })
                addView(sliderRow("Fire funding", fire, 100, "%") { v -> fire = v; sim.post { MicropolisNative.setFunding(handle, road, fire, police) } })
                addView(sliderRow("Police funding", police, 100, "%") { v -> police = v; sim.post { MicropolisNative.setFunding(handle, road, fire, police) } })
            }
        },
        PanelTab("Stats") {
            fun statRow(name: String, value: String): View = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, dp(6))
                addView(TextView(this@MainActivity).apply { text = name; setTextColor(0xFF9AA7B4.toInt()); textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
                addView(TextView(this@MainActivity).apply { text = value; setTextColor(0xFFEEF2F6.toInt()); textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD) })
            }
            // show 0..255 indexes as 0..100 for readability
            fun pct(v: Int) = "${v * 100 / 255}"
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(statRow("Population", "${d.pop}"))
                addView(statRow("City class", cityClassNames.getOrElse(d.cityClass) { "—" }))
                addView(statRow("Approval", "${d.approval}%"))
                addView(statRow("Crime", pct(d.crime)))
                addView(statRow("Pollution", pct(d.pollution)))
                addView(statRow("Land value", pct(d.landValue)))
                addView(statRow("Traffic", pct(d.traffic)))
                addView(statRow("Density", pct(d.density)))
            }
        }
    ))
}
```

### 3. Wire the City pill
Change the City pill's `onClick` (in the `panelBar` build) from the placeholder
`showPanel("City", ...)` to `showCityPanel()`.

## Files in scope
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- The planner verifies on device: City pill opens Budget/Stats; tax + funding sliders
  apply; stats show real numbers.

## Constraints
- Change only `MainActivity.kt`. Do not change the JNI, C ABI, engine, `MapView`,
  drawables, or `build.gradle.kts`. Keep the existing overflow Budget/Tax items working.
- No new dependencies. Commit when green; stage only your file by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
