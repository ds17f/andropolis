# Task 050: Original-style sim speeds (+ Turbo), and migrate the old save slot

## Goal
1. Our speeds are far too fast (Med = a game year every ~10 s). Drive the engine the way
   the originals did: tick at a steady 30 fps and let the engine's own Slow/Medium/Fast
   frame-skipping set the pace (Slow ≈ 2.1 min/year, Medium ≈ 77 s/year, Fast ≈ 26 s/year).
   Keep today's fast rate as a 4th speed, **Turbo**.
2. Saves made before task 043 live at `filesDir/city.cty` and don't appear in Load. Move them.

## Context
- C-ABI already has `void micropolis_set_speed(MicropolisEngine *e, int speed); /* 0..3 */`
  (see `android/engine/include/micropolis_c.h`), implemented in `android/engine/src/micropolis_c.cpp`.
  Engine meaning: 1 = Slow (simulate every 5th tick), 2 = Medium (every 3rd), 3 = Fast (every tick).
  Loading a city resets the engine to speed 3.
- It is NOT yet exposed through JNI. Pattern to copy: `setCityTax` in
  `android/app/src/main/cpp/micropolis_jni.cpp` and `MicropolisNative.kt`.
- `MainActivity`: `speed` (0 Pause, 1 Slow, 2 Med, 3 Fast), `speedNames`,
  `speedTicks = intArrayOf(0, 2, 8, 20)` (sim ticks per loop), `tickLoop()` re-posts itself
  every **100 ms** and runs `repeat(speedTicks[speed]) { MicropolisNative.simTick(handle) }`.
  The Simulation → Speed tab builds one card per `speedNames` entry (glyphs `⏸ ▶ ▶▶ ⏩`).
- 043 stores named saves in `filesDir/cities/<name>.cty`; `savePath` = `filesDir/city.cty` is the old slot.

## Interface

### 1. JNI + Kotlin
- `micropolis_jni.cpp`: `Java_micropolis_port_MicropolisNative_setSpeed(JNIEnv*, jobject, jlong h, jint speed)`
  → `micropolis_set_speed(eng(h), speed)`.
- `MicropolisNative.kt`: `external fun setSpeed(handle: Long, speed: Int)` with a KDoc line.

### 2. New speed model (MainActivity)
- `speedNames = arrayOf("Pause", "Slow", "Med", "Fast", "Turbo")`.
- Replace `speedTicks` with two tables:
  ```kotlin
  private val engineSpeed = intArrayOf(0, 1, 2, 3, 3)   // engine frame-skip mode per UI speed
  private val ticksPerFrame = intArrayOf(0, 1, 1, 1, 7) // Turbo ≈ 210 steps/s like the old Fast
  ```
- `tickLoop()`: post itself every **33 ms** (30 fps) instead of 100 ms, and replace the
  `repeat(speedTicks[speed])` line with `repeat(ticksPerFrame[speed]) { MicropolisNative.simTick(handle) }`.
- Whenever `speed` changes (Speed cards, play/pause button, report-card resume, menu pause/resume)
  AND after every city load/generate, call the engine: add
  ```kotlin
  private fun applyEngineSpeed() { val s = engineSpeed[speed]; if (handle != 0L) sim.post { MicropolisNative.setSpeed(handle, if (s == 0) 3 else s) } }
  ```
  and call it from `updateSpeedChipText()` (which every speed change already calls), and at the
  end of the sim-thread setup / load paths (`loadCity`, `generateRandomCity`, New city, Load dialog, undo/redo loads).
  (Pause is handled by `ticksPerFrame[0] == 0`, so the engine stays at a running mode.)
- Speed tab: add a 5th card, glyph "🚀", label "Turbo". Keep the play/pause button's
  `lastRunSpeed` logic working with index 4.
- The autosave wall-clock check (every 30 s) is unaffected.

### 3. Migrate the old save slot (startup)
Early in `onCreate` (after `prefs`/`citiesDir` are usable):
```kotlin
val legacy = java.io.File(filesDir, "city.cty")
if (legacy.exists() && !prefs.getBoolean("migratedLegacySave", false)) {
    val dest = cityFile("Saved city")
    if (!dest.exists()) legacy.copyTo(dest)
    prefs.edit().putBoolean("migratedLegacySave", true).apply()
}
```
(Leave `city.cty` in place; just copy.)

## Files in scope
- `android/app/src/main/cpp/micropolis_jni.cpp`, `MicropolisNative.kt`, `MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Planner verifies on device: Med advances roughly one month every ~6 s; Turbo is fast;
  a legacy `city.cty` appears in Load as "Saved city".

## Constraints
- Do not change the C-ABI header, engine, CMake, or `build.gradle.kts`. No new dependencies.
- Commit when green; stage only your files by name. Do not use `git checkout`, `git reset`, or `git add -A`.
