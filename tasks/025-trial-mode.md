# Task 025: Trial mode (build → keep / revert) — replaces tile-painting preview

## Goal
Replace the confusing per-tile "preview" (which paints individual tiles — wrong for
multi-tile buildings like a coal plant) with a **trial**: snapshot the city, build
normally (real buildings, applied and visible), then **Keep** or **Revert**.

## Context
Today `MainActivity` has `previewMode` + a `pending` list: in preview, `onTileTap`
adds tiles to `pending` and `MapView` draws a yellow overlay; Confirm applies them
one-by-one. This is wrong. Remove it. Building should ALWAYS apply immediately (so
a coal plant appears as a coal plant). Trial uses the existing
`saveCity`/`loadCity` for rollback. There is already a `savePath`
(`filesDir/city.cty`).

## Interface

### MainActivity.kt
- Remove `previewMode`, `pending`, and the preview branch in the `onTileTap`
  handler — `onTileTap` now ALWAYS builds:
  ```kotlin
  mapView.onTileTap = { tileX, tileY ->
      val tool = currentTool
      sim.post { MicropolisNative.doTool(handle, tool, tileX, tileY) }
  }
  ```
- Add fields:
  ```kotlin
  private var trialActive = false
  private val trialPath by lazy { java.io.File(filesDir, "trial.cty").absolutePath }
  ```
- Rename the Preview button to a **Trial** button (`trialBtn`, text "Trial"). On
  click, if not already active, START a trial:
  ```kotlin
  if (!trialActive) {
      trialActive = true
      sim.post { MicropolisNative.saveCity(handle, trialPath) }
      updateTrialControls()
  }
  ```
- Rename Confirm→**Keep** (`keepBtn`) and Cancel→**Revert** (`revertBtn`):
  ```kotlin
  keepBtn:   setOnClickListener { trialActive = false; java.io.File(trialPath).delete(); updateTrialControls() }
  revertBtn: setOnClickListener { sim.post { MicropolisNative.loadCity(handle, trialPath) }; trialActive = false; updateTrialControls() }
  ```
- Replace `updatePendingBar()` with:
  ```kotlin
  private fun updateTrialControls() {
      keepBtn.visibility = if (trialActive) View.VISIBLE else View.GONE
      revertBtn.visibility = if (trialActive) View.VISIBLE else View.GONE
      trialBtn.isEnabled = !trialActive
      trialBtn.alpha = if (trialActive) 0.5f else 1f
  }
  ```
  Call it once after building the controls (start hidden). Keep the buttons' rounded
  styling from the last task (Keep can use the amber rounded background).

### MapView.kt
- Remove the staged-preview overlay: delete `pendingTiles`, `setPendingTiles(...)`,
  `overlayPaint`, and the overlay-drawing loop in `onDraw` (they are unused now).

## Files in scope
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.
- `android/app/src/main/java/micropolis/port/MapView.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Do not run the emulator; the planner verifies trial keep/revert on device.

## Constraints
- Change only the two files above. Do not change the JNI, C ABI, CMake, engine, or
  `build.gradle.kts`.
- No new dependencies. Commit when green; stage only your files by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
