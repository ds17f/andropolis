# Task 051: Disaster frequency (Simulation → Disasters)

## Goal
Add a disaster-frequency control — **Off / Rare / Normal / Frequent** — at the top of the
Simulation → Disasters tab (above the manual trigger cards). Persist it.

## Why app-side
The engine only has disasters on/off; its frequency comes from the game difficulty level
(Easy ≈ one per 10 game years, Medium ≈ one per 5, Hard ≈ one per year), and changing the
level mid-game also changes other difficulty rules. So we turn the engine's random disasters
OFF and roll them ourselves once per game month.

## Context
- C-ABI header now declares `void micropolis_set_enable_disasters(MicropolisEngine *e, int on);`
  (`android/engine/include/micropolis_c.h`). Implement it in `android/engine/src/micropolis_c.cpp`
  as `if (e) e->sim->setEnableDisasters(on != 0);` (copy the `micropolis_set_auto_budget` style),
  then add JNI `setEnableDisasters(handle: Long, on: Int)` (copy `setAutoBudget`) and the
  `external fun` in `MicropolisNative.kt`.
- `MicropolisNative.makeDisaster(handle, kind)`: 0 Fire, 1 Flood, 2 Tornado, 3 Earthquake,
  4 Monster, 5 Meltdown.
- `MainActivity.tickLoop()` has `statsBuf` from `getStats` with `year = statsBuf[4]`,
  `month = statsBuf[5]`. The Disasters tab lives in `showSimulationPanel()` and is a grid of
  `panelCard(...)` cards added with `addCard(grid, h)`; `CardHandle.setSelected` highlights.

## Interface
- Fields:
  ```kotlin
  private var disasterFreq = 2                                  // 0 Off, 1 Rare, 2 Normal, 3 Frequent
  private val disasterFreqNames = arrayOf("Off", "Rare", "Normal", "Frequent")
  private val disasterYearsPer = intArrayOf(0, 10, 5, 1)        // average years between disasters
  private var lastDisasterMonth = -1
  ```
  Load `disasterFreq = prefs.getInt("disasterFreq", 2)` in `onCreate`.
- After the engine is created/loaded (sim thread setup, and after every loadCity/generate),
  call `MicropolisNative.setEnableDisasters(handle, 0)` so only our roll happens.
- In `tickLoop()` (sim thread), once per new game month:
  ```kotlin
  val monthKey = year * 12 + month
  if (monthKey != lastDisasterMonth) {
      if (lastDisasterMonth != -1 && disasterFreq > 0 && speed != 0) {
          val months = disasterYearsPer[disasterFreq] * 12
          if (java.util.Random().nextInt(months) == 0) {
              // weight like the original: fires most common, meltdown rarest
              val kind = intArrayOf(0, 0, 1, 2, 3, 4, 0, 5)[java.util.Random().nextInt(8)]
              MicropolisNative.makeDisaster(handle, kind)
          }
      }
      lastDisasterMonth = monthKey
  }
  ```
  (Keep a single `java.util.Random` instance as a field rather than allocating each time.)
- Disasters tab: above the trigger grid, add a label "Random disasters" and a 4-card row
  (`GridLayout` columnCount 4) using `panelCard` with glyphs `🚫 🌤 ⚠ 🔥` and the names above;
  tapping sets `disasterFreq`, saves `prefs.putInt("disasterFreq", ...)`, and updates
  selection. Then a label "Trigger now" above the existing six cards.

## Files in scope
- `android/engine/src/micropolis_c.cpp`, `android/app/src/main/cpp/micropolis_jni.cpp`,
  `MicropolisNative.kt`, `MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Planner verifies on device: the four options show and persist; "Frequent" on Fast produces
  a disaster within a game year or two; "Off" produces none.

## Constraints
- Do not change the C-ABI header (already done), engine sources, CMake, or `build.gradle.kts`.
- Commit when green; stage only your files by name. Do not use `git checkout`, `git reset`, or `git add -A`.
