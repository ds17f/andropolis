# Task 013: Speed of play (pause / slow / med / fast)

## Goal
Add a speed control, including Pause. It changes how fast the simulation runs.

## Context
`MainActivity` runs the sim on the `sim` thread with `tickLoop()`, which currently
does `repeat(8) { MicropolisNative.simTick(handle) }` every 100 ms. Speed is best
implemented here — by how many ticks run per frame (Pause = 0) — not via the
engine. One cycling button changes the speed.

## Interface (implementation notes for MainActivity.kt)
- Add fields:
  ```kotlin
  @Volatile private var speed = 2   // 0=Pause 1=Slow 2=Med 3=Fast
  private val speedNames = arrayOf("Pause", "Slow", "Med", "Fast")
  private val speedTicks = intArrayOf(0, 2, 8, 20)
  ```
  (`@Volatile` because it is set on the UI thread and read on the sim thread.)
- Add a cycling button to `barLayout` (after the Load button):
  ```kotlin
  val speedBtn = Button(this).apply {
      text = "Speed: ${speedNames[speed]}"
      setOnClickListener {
          speed = (speed + 1) % 4
          text = "Speed: ${speedNames[speed]}"
      }
  }
  barLayout.addView(speedBtn)
  ```
- In `tickLoop`, replace the fixed `repeat(8)` with the speed-driven count:
  ```kotlin
  repeat(speedTicks[speed]) { MicropolisNative.simTick(handle) }
  ```
  Keep the rest of `tickLoop` (copyTiles, render, getStats/HUD, re-post) unchanged.
  When `speed == 0` this ticks zero times (paused) but still copies + renders, so
  the map stays visible and un-pausing resumes cleanly.

## Files in scope
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Do not run the emulator; the planner verifies on device.

## Constraints
- Change only `MainActivity.kt`.
- Do not change `MapView`, the JNI, the C ABI, the CMake files, `build.gradle.kts`,
  or the engine.
- No new dependencies. Commit when green; stage only your file by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
