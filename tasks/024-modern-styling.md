# Task 024: Modern styling pass (round the boxy UI)

## Goal
Make the UI look modern instead of boxy: rounded corners, spacing, and depth on
every panel, chip, button, segmented control and the tool pill. Match the mockup
`build/mockups/project/Main.dc.html` (note its `border-radius`, gaps, and soft
panels).

## Context
`MainActivity` builds the top bar (city title, play/pause, speed chip, overflow,
Funds/Pop/Score chips) and the bottom controls (Move/Build segmented toggle,
Preview, Confirm/Cancel, tool pill) with **flat `setBackgroundColor` rectangles** —
that is why it looks boxy. Replace those flat backgrounds with **rounded**
backgrounds and add spacing. Do not change behavior/logic, only styling.

## Interface (implementation notes for MainActivity.kt)

### Add a rounded-background helper (member function):
```kotlin
private fun roundedBg(color: Int, radiusDp: Int): android.graphics.drawable.GradientDrawable {
    val d = android.graphics.drawable.GradientDrawable()
    d.setColor(color)
    d.cornerRadius = radiusDp * resources.displayMetrics.density
    return d
}
private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
```

### Apply throughout (replace every `setBackgroundColor(...)` on these with
`background = roundedBg(...)`, and add margins via LayoutParams):
- **HUD chips** (Funds/Pop/Score): `background = roundedBg(0x14FFFFFF, 12)`, padding
  `(dp(12),dp(8),dp(12),dp(8))`, and `8dp` gaps between them (LayoutParams margins).
- **Top-bar buttons** (playPause, speedChip, overflow): `background =
  roundedBg(0x1FFFFFFF, 12)` (playPause keeps amber: `roundedBg(0xFFF5A623.toInt(),
  12)` with dark text), remove their default button background, padding
  `(dp(14),dp(8),dp(14),dp(8))`, `8dp` left margins, `textColor` `#EEF2F6`, and
  `stateListAnimator = null` so they sit flat without the default button shadow box.
- **Move/Build segmented**: the container `background = roundedBg(0xFF1A222A.toInt(),
  14)`, padding `dp(4)`; the ACTIVE segment `background =
  roundedBg(0xFFF5A623.toInt(), 10)` (dark text `#1A1207`), the INACTIVE segment
  `background = null` (muted text `#9AA7B4`); update this in `styleModeSegments()`.
  Segments padding `(dp(20),dp(8),dp(20),dp(8))`.
- **Preview / Confirm / Cancel buttons**: `background = roundedBg(0x1FFFFFFF, 12)`
  (Confirm amber: `roundedBg(0xFFF5A623.toInt(), 12)` dark text), `stateListAnimator
  = null`, padding `(dp(16),dp(8),dp(16),dp(8))`, `8dp` margins.
- **Tool pill**: make it prominent and full-width — `layoutParams` width
  `MATCH_PARENT` with `dp(12)` top margin; `background =
  roundedBg(0xFF1A222A.toInt(), 18)`; padding `(dp(10),dp(8),dp(14),dp(8))`; the
  `pillIcon` sits in a rounded amber-tinted square: wrap it (or set its container)
  `background = roundedBg(0x33F5A623, 12)`, size `dp(44)`, and center the icon.
- **top bar / bottom containers**: keep the dark `#12161C` fill but add
  `elevation = dp(6).toFloat()` for depth.

Add `import android.view.View` if needed. Keep every existing click handler,
field, and the tick/HUD updates unchanged.

## Files in scope
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Do not run the emulator; the planner verifies the look on device.

## Constraints
- Change only `MainActivity.kt`, styling only (no behavior changes). Do not change
  drawables, JNI, C ABI, CMake, engine, `MapView`, or `build.gradle.kts`.
- No new dependencies. Commit when green; stage only your file by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
