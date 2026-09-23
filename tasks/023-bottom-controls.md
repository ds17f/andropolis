# Task 023: Contextual bottom controls (UI redesign stage 3)

## Goal
Finish the redesign: replace the leftover flat bottom buttons (Build, Tax, Preview,
Confirm, Cancel) with the mockup's clean layout — a styled Move⇄Build segmented
toggle, a prominent tool pill, and Preview with Confirm/Cancel that appear only when
tiles are staged. Move Tax into the ⋮ overflow menu. See
`build/mockups/project/Main.dc.html`.

## Context
`MainActivity` currently builds a `HorizontalScrollView` (`barLayout`) holding the
Move/Build toggle (`modeButton`), a Tax button, Preview/Confirm/Cancel buttons, and
the current-tool pill. `buildMode`, `previewMode`, `pending` (list of staged
tiles), `taxRates`/`taxIdx`, `pillIcon`/`pillName`/`updatePill()`, `openPalette()`,
and the overflow `PopupMenu` (in `overflowBtn`) all exist. Engine calls run on `sim`.

## Interface (implementation notes for MainActivity.kt)

Replace the `barLayout` / bottom section with a vertical `LinearLayout` `bottom`
(background `#EE12161C`, padding 12,10,12,14) containing, in order:

### 1. A contextual control row (`ctrlRow`, horizontal, gravity center):
- **Move⇄Build segmented toggle**: a horizontal container (rounded, bg `#1A222A`,
  padding 4) with two `TextView` "segments" `segMove` ("Move") and `segBuild`
  ("Build"). Style the active one amber bg (`#F5A623`) + dark text (`#1A1207`), the
  inactive muted (`#9AA7B4`). Clicking a segment sets `buildMode` accordingly,
  `mapView.buildEnabled = buildMode`, and restyles both (write a helper
  `styleModeSegments()`). Start with Build active.
- **`previewBtn`** Button: text `"Preview: Off"/"Preview: On"`, toggles
  `previewMode` (keep existing behavior).
- **`confirmBtn`** and **`cancelBtn`** Buttons (keep existing click logic). These
  start `visibility = View.GONE`.

### 2. Pending indicator behavior:
Add `private fun updatePendingBar()` that sets `confirmBtn`/`cancelBtn`
`visibility` to `View.VISIBLE` when `previewMode && pending.isNotEmpty()`, else
`View.GONE`. Call it: after toggling `previewMode`, in the `onTileTap` preview
branch (after adding to `pending`), and in the confirm/cancel handlers. Also give
`confirmBtn` the text `"Confirm (${pending.size})"` in `updatePendingBar()`.

### 3. The tool pill (prominent, styled like the mockup):
A horizontal `LinearLayout` (rounded bg `#0FFFFFFF`, padding, min height ~56dp)
holding: `pillIcon` (ImageView 40dp, amber tint), a vertical block with a small
muted "Current tool" label over `pillName` (16sp bold), and a trailing "▲"
TextView. `setOnClickListener { openPalette() }`. Keep `updatePill()`.

### 4. Move Tax into the overflow menu:
In the `overflowBtn` `PopupMenu` builder, add an item
`"Tax rate — ${taxRates[taxIdx]}%"` (built from the current index each time the menu
opens). In the menu click handler, add a branch: if
`item.title.toString().startsWith("Tax")` → cycle
`taxIdx = (taxIdx + 1) % taxRates.size`, then `sim.post {
MicropolisNative.setCityTax(handle, taxRates[taxIdx]) }`. Remove the old Tax button
from the bottom.

## Files in scope
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Do not run the emulator; the planner verifies the layout on device.

## Constraints
- Change only `MainActivity.kt`. Keep all engine/tool/save/speed logic working.
- Do not change the drawables, JNI, C ABI, CMake, engine, `MapView`, or
  `build.gradle.kts`.
- No new dependencies. Commit when green; stage only your file by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
