# Task 007: Stats HUD

## Goal
Show a heads-up line with the city funds, date, population, and score. Update it
every tick.

## Context
`MainActivity` runs the sim on a sim thread and draws tiles. The engine already
exposes `MicropolisNative.getStats(handle, IntArray)`, which fills an `IntArray`
of length 10 with:
`[cityTime, totalFunds, cityPop, cityScore, cityYear, cityMonth, resDemand,
comDemand, indDemand, gameLevel]`.

We drive the HUD by polling those stats each tick (no engine callbacks needed).

## Interface (implementation notes for MainActivity.kt)
- Add fields: `private val statsBuf = IntArray(10)` and
  `private lateinit var hud: android.widget.TextView`.
- Build the `hud` TextView and add it as the FIRST child of the root
  `LinearLayout` (above the `MapView`), with `WRAP_CONTENT` height. Give it a dark
  background and light text and some padding so it is readable, e.g.:
  ```kotlin
  hud = android.widget.TextView(this).apply {
      setBackgroundColor(0xCC000000.toInt())
      setTextColor(0xFFFFFFFF.toInt())
      setPadding(24, 16, 24, 16)
  }
  root.addView(hud, LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT))
  ```
  (Add it before `root.addView(mapView, ...)`.)
- In the tick loop, after `copyTiles`, read the stats and post the text to the UI:
  ```kotlin
  MicropolisNative.getStats(handle, statsBuf)
  val funds = statsBuf[1]; val pop = statsBuf[2]; val score = statsBuf[3]
  val year = statsBuf[4]; val month = statsBuf[5]
  val months = arrayOf("Jan","Feb","Mar","Apr","May","Jun",
                       "Jul","Aug","Sep","Oct","Nov","Dec")
  val monthName = months.getOrElse(month) { "?" }
  val text = "Funds: \$$funds   $monthName $year   Pop: $pop   Score: $score"
  ui.post { hud.text = text }
  ```

## Files in scope
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Do not run the emulator; the planner verifies the HUD on the device.

## Constraints
- Change only `MainActivity.kt`.
- Do not change `MapView`, the JNI, the C ABI, the CMake files, `build.gradle.kts`,
  or the engine.
- No new dependencies. Commit when green; stage only your file by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
