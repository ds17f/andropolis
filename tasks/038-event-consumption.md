# Task 038: Consume engine events — messages, zoom-to-event, Query tool

## Goal
Now that the engine pushes events (037), surface them:
- Show an **in-game message banner** for engine messages (fire, flood, traffic, etc.).
- **Zoom to the event** when the engine asks (autoGoto) or a message has a location.
- Make the **Query tool** work: tapping a tile with Query shows a zone-info popup.

## Context (MainActivity.kt)
- 037 added `MicropolisNative.pollEvent(handle, IntArray(9)): Boolean` — drain in a loop;
  it fills `[type, x, y, a, b, c, d, e, f]`. Types: 0 MESSAGE (a=messageIndex 1..57,
  b=picture, c=important), 1 ZONE_STATUS (a=tileCat, b=popDensity, c=landValue, d=crime,
  e=pollution, f=growth), 2 AUTO_GOTO, 3 EARTHQUAKE (a=strength), 4 LOSE, 5 WIN.
- `tickLoop()` runs on the `sim` thread every 100ms and already marshals UI via `ui.post`.
- `MapView.centerOnTile(tx, ty)` exists (036). The map is inside a `FrameLayout`
  (`mapContainer` from 036) that also holds the `minimap` — good place to add a banner.
- `roundedBg(color,radiusDp)`, `dp(v)` exist. Engine calls fire on the sim thread; polling
  in tickLoop (also sim thread) is safe.

## Interface

### Fields
```kotlin
private val eventBuf = IntArray(9)
private lateinit var messageBanner: TextView
private val bannerHide = Runnable { messageBanner.visibility = View.GONE }
private var lastEventTile: Pair<Int, Int>? = null
```
Message text table (index = messageIndex; 0 unused):
```kotlin
private val messageText = arrayOf(
    "", "More residential zones needed", "More commercial zones needed",
    "More industrial zones needed", "More roads required", "Inadequate rail system",
    "Build a power plant", "Residents demand a stadium", "Industry requires a seaport",
    "Commerce requires an airport", "Pollution very high", "Crime very high",
    "Frequent traffic jams reported", "Citizens demand a fire department",
    "Citizens demand a police department", "Blackouts reported — check the power map",
    "Citizens upset: taxes too high", "Roads deteriorating — underfunded",
    "Fire departments need funding", "Police departments need funding", "Fire reported!",
    "A monster has been sighted!", "Tornado reported!", "Major earthquake reported!",
    "A plane has crashed!", "Shipwreck reported!", "A train crashed!",
    "A helicopter crashed!", "Unemployment is high", "YOUR CITY HAS GONE BROKE!",
    "Firebombing reported!", "Need more parks", "Explosion detected!",
    "Insufficient funds to build that", "Area must be bulldozed first",
    "Population has reached 2,000", "Population has reached 10,000",
    "Population has reached 50,000", "Population has reached 100,000",
    "Population has reached 500,000", "Brownouts — build another power plant",
    "Heavy traffic reported", "Flooding reported!", "A nuclear meltdown has occurred!",
    "They're rioting in the streets!", "Started a new city", "Restored a saved city",
    "You won the scenario!", "You lost the scenario", "About Micropolis"
)
private fun msgText(i: Int) = messageText.getOrElse(i) { "City update" }
```

### The banner (add to the map container, over the map)
Where `mapContainer` is built (036), add a banner child that starts hidden:
```kotlin
messageBanner = TextView(this).apply {
    visibility = View.GONE
    setTextColor(0xFFEEF2F6.toInt()); textSize = 13f
    background = roundedBg(0xE6202A36.toInt(), 12)
    setPadding(dp(14), dp(10), dp(14), dp(10))
    setOnClickListener { lastEventTile?.let { mapView.centerOnTile(it.first, it.second) } }
}
mapContainer.addView(messageBanner, android.widget.FrameLayout.LayoutParams(
    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT).apply {
        gravity = android.view.Gravity.TOP or android.view.Gravity.START
        setMargins(dp(8), dp(8), dp(8), 0)
    })
```
Helper to show it for a few seconds:
```kotlin
private fun showBanner(text: String) {
    messageBanner.text = text
    messageBanner.visibility = View.VISIBLE
    ui.removeCallbacks(bannerHide)
    ui.postDelayed(bannerHide, 6000)
}
```

### Drain events in `tickLoop`
After the stats/HUD work (near the year-end check), drain and dispatch on the UI thread:
```kotlin
while (MicropolisNative.pollEvent(handle, eventBuf)) {
    val type = eventBuf[0]; val ex = eventBuf[1]; val ey = eventBuf[2]
    val a = eventBuf[3]; val b = eventBuf[4]; val c = eventBuf[5]
    val d = eventBuf[6]; val e2 = eventBuf[7]; val f = eventBuf[8]
    ui.post {
        when (type) {
            0 -> { // MESSAGE
                showBanner(msgText(a))
                if (ex >= 0 && ey >= 0) {
                    lastEventTile = Pair(ex, ey)
                    if (c == 1) mapView.centerOnTile(ex, ey)   // important → auto-zoom
                }
            }
            1 -> showZoneStatusDialog(ex, ey, a, b, c, d, e2, f) // Query result
            2 -> { lastEventTile = Pair(ex, ey); mapView.centerOnTile(ex, ey) } // AUTO_GOTO
            3 -> showBanner("Earthquake! (strength $a)")
            4 -> showBanner("Your city has fallen.")
            5 -> showBanner("You won!")
        }
    }
}
```

### Query zone-status dialog
```kotlin
private fun levelName(i: Int) = arrayOf("None","Low","Medium","High","Very high").getOrElse(i) { "$i" }
private fun showZoneStatusDialog(x: Int, y: Int, cat: Int, pop: Int, lv: Int, crime: Int, poll: Int, growth: Int) {
    val msg = """
        Tile category: $cat
        Population density: ${levelName(pop)}
        Land value: ${levelName(lv)}
        Crime rate: ${levelName(crime)}
        Pollution: ${levelName(poll)}
        Growth rate: ${levelName(growth)}
    """.trimIndent()
    androidx.appcompat.app.AlertDialog.Builder(this)
        .setTitle("Zone at ($x, $y)").setMessage(msg)
        .setPositiveButton("OK", null).show()
}
```
(The Query tool already calls `doTool(QUERY)` via `onTileTap`; that triggers the engine's
`showZoneStatus`, which now arrives as a type-1 event — no tool-routing change needed.)

## Files in scope
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- The planner verifies on device: triggering a disaster shows a banner and zooms; the
  Query tool opens a zone-info dialog on tap.

## Constraints
- Change only `MainActivity.kt`. Do not change the JNI, C ABI, engine, `MapView`,
  `MinimapView`, drawables, or `build.gradle.kts`. Keep tick/HUD/overlay/report working.
- No new dependencies. Commit when green; stage only your file by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
