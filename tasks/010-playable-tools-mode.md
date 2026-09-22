# Task 010: Complete the tool bar and add a Move/Build mode

## Goal
Make the city actually buildable: expose all the build tools (including the power
plants, which are missing), and add a Move/Build toggle so panning/zooming the map
does not accidentally build.

## Context
`MainActivity` has a hardcoded `tools` list for the bottom button bar, and a tap on
the map always builds with the current tool. Two problems:
1. The `tools` list is missing key tools — notably the **power plants**, so a
   working city (which needs power) cannot be built.
2. A single tap always builds. The user needs to pan/zoom to line up a spot
   without building, then build precisely.

Two files change: `MainActivity.kt` (tool list + a mode toggle button) and
`MapView.kt` (only fire taps when building is enabled).

## Interface

### MapView.kt
- Add `var buildEnabled: Boolean = true`.
- In the `GestureListener.onSingleTapUp`, only call `onTileTap` when
  `buildEnabled` is true. (Pan and zoom must keep working regardless.)

### MainActivity.kt
- Replace the `tools` list with this fuller set (label to C-ABI `MicropolisTool`
  value). Keep the existing button-building loop:
  ```kotlin
  val tools = listOf(
      "Bulldozer" to 7, "Road" to 9, "Rail" to 8, "Wire" to 6, "Park" to 11,
      "Resid" to 0, "Comm" to 1, "Ind" to 2, "Police" to 4, "Fire" to 3,
      "Stadium" to 10, "Seaport" to 12, "Airport" to 15,
      "Coal" to 13, "Nuclear" to 14, "Query" to 5
  )
  ```
- Add a **mode toggle** as the FIRST button in the bar (before the tool buttons):
  a `Button` whose text is `"Build"` when building and `"Move"` when not. On click
  it flips a `private var buildMode = true`, sets `mapView.buildEnabled = buildMode`,
  and updates its own text. Start in Build mode.

## Files in scope
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.
- `android/app/src/main/java/micropolis/port/MapView.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Do not run the emulator; the planner verifies on the device.

## Constraints
- Change only the two files above.
- Do not change the JNI, the C ABI, the CMake files, `build.gradle.kts`, or the engine.
- No new dependencies. Commit when green; stage only your files by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
