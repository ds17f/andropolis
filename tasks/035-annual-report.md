# Task 035: Year-end report card (pause + status), toggleable

## Goal
At each new game year the simulation PAUSES and shows an annual report card
(class, population, score, approval, funds). The player can turn this on/off from the
⋮ overflow menu; the choice persists.

## Context (MainActivity.kt)
- `tickLoop()` (on `sim`, every 100ms) fills `statsBuf` via `getStats`, then reads
  `val year = statsBuf[4]` and, in a `ui.post { ... }` block, updates the HUD
  (`subtitle`, `fundsValue`, `popValue`, `scoreValue`). This `ui.post` block is the place
  to detect a year change (runs on the main thread, safe for pausing + dialogs).
- Speed: `speed` (0=Pause), `lastRunSpeed`, `updatePlayPauseText()`, `updateSpeedChipText()`.
- `prefs` (SharedPreferences "micropolis"), `cityClassNames` (array), and
  `getEvaluation(handle, IntArray(7))` / `getBudget(handle, IntArray(12))` all exist.
- The overflow menu is built in `overflowBtn`'s click (`pm.menu.add(...)` then a
  `when (item.title)` handler ending in `true`).

## Interface

### Fields
```kotlin
private var annualReportEnabled = true    // loaded from prefs in onCreate
private var lastReportYear = -1           // -1 until the first tick seen
```
In `onCreate` (after `prefs` is usable — anywhere before the tick loop starts), add:
```kotlin
annualReportEnabled = prefs.getBoolean("annualReport", true)
```

### Detect the year boundary (in the tickLoop `ui.post` block)
After the HUD text is set (using the already-extracted `year`), add:
```kotlin
if (lastReportYear != -1 && year > lastReportYear && annualReportEnabled) {
    if (speed != 0) { lastRunSpeed = speed; speed = 0; updatePlayPauseText(); updateSpeedChipText() }
    showReportCard(year)
}
lastReportYear = year
```
(Setting `lastReportYear` every tick — even when disabled — prevents an instant popup when
the player re-enables it. While paused the year can't advance, so it fires once per year.)

### The report card
```kotlin
private fun showReportCard(year: Int) {
    sim.post {
        val ev = IntArray(7); MicropolisNative.getEvaluation(handle, ev)
        val b = IntArray(12); MicropolisNative.getBudget(handle, b)
        ui.post {
            val cls = cityClassNames.getOrElse(ev[2]) { "?" }
            val msg = """
                Class: $cls
                Population: ${ev[3]}  (Δ ${ev[4]})
                Score: ${ev[0]}  (Δ ${ev[1]})
                Approval: ${ev[6]}%
                Funds: ${'$'}${b[0]}    Tax: ${b[1]}%
            """.trimIndent()
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Annual Report — $year")
                .setMessage(msg)
                .setPositiveButton("Continue", null)
                .setCancelable(false)
                .show()
        }
    }
}
```

### Overflow-menu toggle
- Add a menu item that shows the current state:
  ```kotlin
  pm.menu.add(if (annualReportEnabled) "Annual report: On" else "Annual report: Off")
  ```
- In the `when (item.title)` handler, add a branch (before the `else`):
  ```kotlin
  item.title.toString().startsWith("Annual report") -> {
      annualReportEnabled = !annualReportEnabled
      prefs.edit().putBoolean("annualReport", annualReportEnabled).apply()
  }
  ```
  (Use a `when` with `item.title.toString()` conditions consistently — if the existing
  `when` switches on `item.title` directly, put this as an `item.title.toString()
  .startsWith(...)` check in the `else` branch alongside the existing "Tax" check.)

## Files in scope
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- The planner verifies on device: a new year pauses the sim and shows the report; the ⋮
  toggle turns it off (no popup) and the setting survives a restart.

## Constraints
- Change only `MainActivity.kt`. Do not change the JNI, C ABI, engine, `MapView`,
  drawables, or `build.gradle.kts`. Keep the other menu items and tick/HUD working.
- No new dependencies. Commit when green; stage only your file by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
