# Task 012: Save / Load / New city UI

## Goal
Add New, Save, and Load buttons that call the engine. Save writes the city to app
storage; Load restores it; New regenerates a random city.

## Context
`MainActivity` builds the bottom bar (a mode toggle + tool buttons in
`barLayout`), runs the engine on the `sim` thread, and has a `handle`. Task 011
just added `MicropolisNative.saveCity(handle, path)` and
`MicropolisNative.loadCity(handle, path)` (both return 1 on success, 0 on fail),
and `generateRandomCity(handle)` already exists.

All engine calls MUST run on the `sim` thread (the engine is single-threaded).

## Interface (implementation notes for MainActivity.kt)
- Add a save path field (app-internal storage, no permissions needed):
  ```kotlin
  private val savePath by lazy { java.io.File(filesDir, "city.cty").absolutePath }
  ```
- Add three `Button`s to `barLayout`, right AFTER the mode-toggle button and
  BEFORE the tool buttons. Each posts its engine call to `sim`:
  ```kotlin
  val newBtn = Button(this).apply {
      text = "New"; setOnClickListener { sim.post { MicropolisNative.generateRandomCity(handle) } }
  }
  val saveBtn = Button(this).apply {
      text = "Save"; setOnClickListener { sim.post { MicropolisNative.saveCity(handle, savePath) } }
  }
  val loadBtn = Button(this).apply {
      text = "Load"; setOnClickListener { sim.post { MicropolisNative.loadCity(handle, savePath) } }
  }
  barLayout.addView(newBtn); barLayout.addView(saveBtn); barLayout.addView(loadBtn)
  ```
  (Add these where the mode-toggle button is added, right after it, so the bar
  reads: [Build] [New] [Save] [Load] [Bulldozer] [Road] ...)

## Files in scope
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Do not run the emulator; the planner verifies the save/load round-trip on device.

## Constraints
- Change only `MainActivity.kt`.
- Do not change `MapView`, the JNI, the C ABI, the CMake files, `build.gradle.kts`,
  or the engine.
- No new dependencies. Commit when green; stage only your file by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
