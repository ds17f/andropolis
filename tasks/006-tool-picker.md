# Task 006: Tool picker

## Goal
Add a row of buttons to choose the current tool. A tap on the map uses the
selected tool instead of the hardcoded park.

## Context
`MainActivity` creates the engine on a sim thread and sets `MapView` as the whole
content view. A tap calls `doTool(handle, 11, tileX, tileY)` (11 = park, hardcoded).
Replace that with a selectable current tool and a button bar.

Build the layout in code (no XML resource files). The tap already arrives on the
UI thread through `mapView.onTileTap`; do not change `MapView`.

## Interface (implementation notes for MainActivity.kt)
- Add a field `private var currentTool = 11` (park by default).
- Build this layout in `onCreate` in place of `setContentView(mapView)`:
  - A vertical `LinearLayout` as the content view.
  - `MapView` added first, with `LinearLayout.LayoutParams(MATCH_PARENT, 0, weight=1f)`
    so it fills the space above the bar.
  - A `HorizontalScrollView` (height `WRAP_CONTENT`) containing a horizontal
    `LinearLayout` of `Button`s, one per tool below. Each button's `text` is the
    label; `setOnClickListener { currentTool = value }`.
- Tools (label to int value, which is the C-ABI `MicropolisTool`):
  Bulldozer=7, Road=9, Rail=8, Wire=6, Residential=0, Commercial=1, Industrial=2,
  Police=4, Fire=3, Park=11.
- Change the tap handler so it uses `currentTool`. Capture it on the UI thread and
  pass it to the sim thread:
  ```kotlin
  mapView.onTileTap = { tileX, tileY ->
      val tool = currentTool
      sim.post { MicropolisNative.doTool(handle, tool, tileX, tileY) }
  }
  ```

## Files in scope
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Do not run the emulator; the planner verifies the buttons on the device.

## Constraints
- Change only `MainActivity.kt`.
- Do not change `MapView`, the JNI, the C ABI, the CMake files, `build.gradle.kts`,
  or the engine.
- No new dependencies. Commit when green; stage only your file by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
