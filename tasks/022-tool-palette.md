# Task 022: Tool palette bottom sheet (UI redesign stage 2)

## Goal
Replace the flat row of 16 tool buttons with a single "current tool" pill that
opens a bottom sheet: tools shown as icon tiles grouped by category. Match the
mockup `build/mockups/project/Palette.dc.html`.

## Context
`MainActivity` builds tool buttons in a loop from a `tools` list and adds them to
`barLayout` (with the selected-tool highlight from task 015). Task 021 created
icon drawables `R.drawable.ic_residential`, `ic_commercial`, `ic_industrial`,
`ic_park`, `ic_road`, `ic_rail`, `ic_wire`, `ic_bulldozer`, `ic_police`, `ic_fire`,
`ic_coal`, `ic_nuclear`, `ic_stadium`, `ic_seaport`, `ic_airport`, `ic_query`.
`currentTool` (default 11) still drives `onTileTap`. Material is available
(`com.google.android.material.bottomsheet.BottomSheetDialog`).

## Interface (implementation notes for MainActivity.kt)

### Tool data (replace the old `tools` list + its button loop + the `highlightTool`
### helper + `toolButtons`):
```kotlin
data class ToolItem(val label: String, val value: Int, val icon: Int)
private val toolCategories = linkedMapOf(
    "Zones" to listOf(ToolItem("Residential",0,R.drawable.ic_residential), ToolItem("Commercial",1,R.drawable.ic_commercial), ToolItem("Industrial",2,R.drawable.ic_industrial), ToolItem("Park",11,R.drawable.ic_park)),
    "Transport" to listOf(ToolItem("Road",9,R.drawable.ic_road), ToolItem("Rail",8,R.drawable.ic_rail), ToolItem("Wire",6,R.drawable.ic_wire), ToolItem("Bulldozer",7,R.drawable.ic_bulldozer)),
    "Services & Power" to listOf(ToolItem("Police",4,R.drawable.ic_police), ToolItem("Fire",3,R.drawable.ic_fire), ToolItem("Coal",13,R.drawable.ic_coal), ToolItem("Nuclear",14,R.drawable.ic_nuclear)),
    "Special" to listOf(ToolItem("Stadium",10,R.drawable.ic_stadium), ToolItem("Seaport",12,R.drawable.ic_seaport), ToolItem("Airport",15,R.drawable.ic_airport), ToolItem("Query",5,R.drawable.ic_query))
)
private val allTools by lazy { toolCategories.values.flatten() }
```

### Current-tool pill (add to `barLayout` where the tool buttons used to go):
A horizontal `LinearLayout` (rounded dark bg `#EE12161C`, padding, ~height 56dp)
holding: an `ImageView` `pillIcon` (28dp, `imageTintList` amber `#F5A623`), a
`TextView` `pillName`, and a trailing "▲" `TextView`. Store `pillIcon`/`pillName`
as fields. `setOnClickListener { openPalette() }`. Add a helper:
```kotlin
private fun updatePill() {
    val ti = allTools.first { it.value == currentTool }
    pillIcon.setImageResource(ti.icon); pillName.text = ti.label
}
```
Call `updatePill()` once after building the pill.

### The palette sheet:
```kotlin
private fun openPalette() {
    val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
    val scroll = ScrollView(this)
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(20, 12, 20, 28); setBackgroundColor(0xFF12161C.toInt())
    }
    for ((cat, items) in toolCategories) {
        col.addView(android.widget.TextView(this).apply {
            text = cat.uppercase(); setTextColor(0xFF7D8B99.toInt()); textSize = 11f
            setPadding(4, 20, 0, 8); letterSpacing = 0.1f
        })
        val grid = android.widget.GridLayout(this).apply { columnCount = 4 }
        for (ti in items) grid.addView(buildToolCard(ti, sheet))
        col.addView(grid)
    }
    scroll.addView(col); sheet.setContentView(scroll); sheet.show()
}

private fun buildToolCard(ti: ToolItem, sheet: com.google.android.material.bottomsheet.BottomSheetDialog): View {
    val selected = ti.value == currentTool
    val card = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER_HORIZONTAL
        setPadding(8, 10, 8, 10)
        setBackgroundColor(if (selected) 0x33F5A623 else 0x0DFFFFFF)
    }
    card.addView(android.widget.ImageView(this).apply {
        setImageResource(ti.icon)
        imageTintList = android.content.res.ColorStateList.valueOf(if (selected) 0xFFF5A623.toInt() else 0xFFC3CCD6.toInt())
        layoutParams = LinearLayout.LayoutParams(84, 84)
    })
    card.addView(android.widget.TextView(this).apply {
        text = ti.label; setTextColor(0xFFCDD6E0.toInt()); textSize = 11f
        gravity = android.view.Gravity.CENTER; setPadding(0, 6, 0, 0)
    })
    // even 4-column sizing
    val lp = android.widget.GridLayout.LayoutParams()
    lp.width = 0; lp.columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
    lp.setMargins(6, 6, 6, 6); card.layoutParams = lp
    card.setOnClickListener { currentTool = ti.value; updatePill(); sheet.dismiss() }
    return card
}
```

### Remove:
The old `tools` list, its button-creating loop, `toolButtons`, and the
`highlightTool` function (superseded). Keep everything else (Move/Build toggle,
Tax, Preview/Confirm/Cancel — they are handled in a later stage).

## Files in scope
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Do not run the emulator; the planner verifies the palette on device.

## Constraints
- Change only `MainActivity.kt`. Do not change the drawables, JNI, C ABI, CMake,
  engine, `MapView`, or `build.gradle.kts`.
- No new dependencies. Commit when green; stage only your file by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
