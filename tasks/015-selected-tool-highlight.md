# Task 015: Highlight the selected tool

## Goal
Show which tool is currently selected by highlighting its button. Tapping a tool
highlights it and un-highlights the others.

## Context
`MainActivity` builds the tool buttons in a loop from the `tools` list; each sets
`currentTool` on click, but there is no visual indication of the active tool.
`currentTool` starts at 11 (Park). This is a `MainActivity`-only change.

## Interface (implementation notes for MainActivity.kt)
- Keep references to the tool buttons and a highlight helper. In the loop that
  builds the tool buttons, collect them and apply the highlight on click:
  ```kotlin
  val toolButtons = mutableListOf<Button>()
  for ((label, value) in tools) {
      val button = Button(this).apply {
          text = label
          setOnClickListener {
              currentTool = value
              highlightTool(this, toolButtons)
          }
      }
      toolButtons.add(button)
      barLayout.addView(button)
      if (value == currentTool) button.post { highlightTool(button, toolButtons) }
  }
  ```
- Add the helper (a member function):
  ```kotlin
  private fun highlightTool(selected: android.widget.Button, all: List<android.widget.Button>) {
      for (b in all) b.setBackgroundColor(0xFF666666.toInt())   // unselected gray
      selected.setBackgroundColor(0xFF2E7D32.toInt())           // selected green
  }
  ```
- Only the tool buttons participate; leave the Build/New/Save/Load/Speed/Tax
  buttons as they are.

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
