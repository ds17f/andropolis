# Task 008: Respect the top and bottom system bars

## Goal
Make the UI respect Android's status bar (top) and navigation bar (bottom) so the
stats HUD is not hidden under the status bar and the tool-picker button bar is not
hidden under the navigation bar, on modern (API 35+) devices.

## Context
`MainActivity.kt` builds a vertical `LinearLayout` as the content view: a stats
`TextView` (`hud`) at top, the `MapView` filling the middle (weight 1), and a
`HorizontalScrollView` of tool `Button`s (the picker) at the bottom.

The project targets `targetSdk = 35` (see `android/app/build.gradle.kts`). From API
35, Android makes apps **edge-to-edge by default**, so the content window now spans
the full screen *under* the status and navigation bars. With no inset handling, the
`hud` sits under the status bar and — the reportable bug — the bottom tool-picker
row sits under the navigation bar, so the buttons are unusable.

`androidx.core:core-ktx:1.13.1` is already a dependency, so `WindowCompat` /
`InsetsCompat` / `ViewCompat` are available; `minSdk = 24`. Do not add dependencies.

This task builds on 006 (tool picker bar) and 007 (stats HUD). It is layout-only.

## Interface (implementation notes for MainActivity.kt)
Make the content view clear both system bars. Preferred minimal change: on the
root vertical `LinearLayout` (`root`), add
```kotlin
root.fitsSystemWindows = true
```
before `setContentView(root)`. This asks the framework to pad the root content out
from the status and navigation bars, so `hud` drops below the status bar and the
tool-picker row lifts above the navigation bar, on API 24 and above.

If a build or on-device check shows `fitsSystemWindows` does not achieve both bars,
use the equivalent explicit form instead (same effect, more explicit):
```kotlin
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
    val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
    v.updatePadding(left = bars.left, top = bars.top,
                    right = bars.right, bottom = bars.bottom)
    insets
}
```
Pick one; do both is not needed. Do not alter `MapView`, the JNI, the C ABI, or the
build files.

## Files in scope
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit. Add the
  inset handling to the `root` LinearLayout. (Imports only if you use the explicit
  `ViewCompat` form.)

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Do not run the emulator; the orchestrator verifies on device that the tool
   buttons are fully above the navigation bar and the HUD is fully below the
   status bar.

## Constraints
- Change only `MainActivity.kt`.
- Do not change `MapView`, the JNI bridge, the C ABI, the CMake files, the theme,
   `build.gradle.kts`, or the engine.
- No new dependencies (core-ktx is already present).
- Commit when green; stage only your file by name. Do not use `git add -A`,
   `git commit -a`, `git checkout`, `git reset`, `git restore`, `git stash`, or
   `git clean`.
- If the build does not go green, or a decision is missing from this spec, do not
   guess: write `tasks/008-systray-insets.BLOCKED.md` (what you tried, the exact
   error, the question you need answered) and stop.
