# Task 062: Material bottom bar + tool FAB

## Goal
Replace the placeholder in `BottomBar.kt` with a Material 3 look:
- A **bottom bar** (like a Material NavigationBar) with four equal items, each an icon over a
  small label: **Simulation** (label = current speed), **City**, **Overlay** (label = current
  overlay), **Undo**.
- The **current tool** as an **Extended FAB** at the bottom-right of the map (icon + tool
  name). Tap = open the palette. A **small FAB with ✕** sits above it while a tool is
  selected; tap = back to Move.

## Context (read only these)
- `BottomBar.kt` (small): the placeholder you replace. Keep the two function signatures and
  what each must assign (see its comments).
- Already wired elsewhere (do not change): `MainLayout.kt` adds `buildBottomBar()` below the
  map and puts `buildToolFab()` in the map's bottom-right corner. `updatePill()` (Panels.kt)
  sets `toolFab.text`, `toolFab.setIconResource(...)` and `dropToolFab.visibility`.
  `updateSpeedChipText()` sets `simState.text`; the overlay panel sets `overlayState.text`;
  `updateUndoButtons()` sets `undoBtn.isEnabled` and `undoBtn.alpha`.
- Actions: `showSimulationPanel()`, `showCityPanel()`, `showOverlayPanel()`, `undo()`,
  `openPalette()`, `selectTool(MOVE_TOOL)`. Helpers: `dp(Int)`, `speedNames[speed]`.
- Icons in `res/drawable`: `ic_fast_forward`, `ic_bar_chart`, `ic_layers`, `ic_undo`, `ic_close`.
- Colours: surface `0xFF12161C`, on-surface `0xFFEEF2F6`, muted `0xFF9AA7B4`,
  accent (amber) `0xFFF5A623`, dark on-accent `0xFF1A1207`.

## Interface — rewrite `BottomBar.kt` completely
```kotlin
package micropolis.port

/** The bottom bar: Simulation, City, Overlay, Undo. Must set simState, overlayState and undoBtn. */
internal fun MainActivity.buildBottomBar(): View

/** The tool FAB (tap = palette) with a small ✕ above it. Must set toolFab and dropToolFab. */
internal fun MainActivity.buildToolFab(): View
```

### `buildBottomBar()`
- A horizontal `LinearLayout`, background `0xFF12161C`, elevation 8dp, padding 4dp top,
  8dp bottom. Four items, each `LayoutParams(0, WRAP_CONTENT, 1f)`.
- Each item: a vertical `LinearLayout`, gravity center, padding 6dp, clickable, with
  a ripple background: `background = android.util.TypedValue().let { tv ->
  theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
  androidx.core.content.ContextCompat.getDrawable(this, tv.resourceId) }`. Children:
  1. `ImageView` 24dp × 24dp with the icon, `imageTintList = ColorStateList.valueOf(0xFFEEF2F6)`.
  2. `TextView` label, 12sp, colour `0xFF9AA7B4`, single line, centred, top padding 2dp.
- Items and their labels / actions:
  - `ic_fast_forward`, label = `simState` (a TextView you create with text `speedNames[speed]`) → `showSimulationPanel()`
  - `ic_bar_chart`, label "City" → `showCityPanel()`
  - `ic_layers`, label = `overlayState` (TextView, initial text "Overlay") → `showOverlayPanel()`
  - `ic_undo`, label "Undo" → `undo()`; assign **the whole item** to `undoBtn`.

### `buildToolFab()`
- A vertical `LinearLayout` with `gravity = android.view.Gravity.END`.
- `dropToolFab` = `com.google.android.material.floatingactionbutton.FloatingActionButton(this)`
  with `size = FloatingActionButton.SIZE_MINI`, image `ic_close`,
  `backgroundTintList = ColorStateList.valueOf(0xFF2A333D)`, `imageTintList = ColorStateList.valueOf(0xFFEEF2F6)`,
  `contentDescription = "Drop tool"`, click → `selectTool(MOVE_TOOL)`; bottom margin 12dp;
  in its `LinearLayout.LayoutParams` set `gravity = Gravity.END` and right margin 8dp.
- `toolFab` = `com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton(this)`
  with `backgroundTintList = ColorStateList.valueOf(0xFFF5A623)`, `setTextColor(0xFF1A1207)`,
  `iconTint = ColorStateList.valueOf(0xFF1A1207)`, `contentDescription = "Current tool"`,
  click → `openPalette()`.
- Add `dropToolFab` then `toolFab`; return the column. (`updatePill()` fills in the text,
  icon and ✕ visibility.)

## Files in scope
- `android/app/src/main/java/micropolis/port/BottomBar.kt` only.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Planner verifies on device: the bar shows four items; the amber tool FAB at the map's
  bottom-right shows the tool; ✕ appears only with a tool selected.

## Constraints
- Only `BottomBar.kt` changes. Do not read engine sources or git history. Do not edit this spec.
- No new dependencies (Material components are already in the project).
- Commit when green; stage only your file by name. Do not use `git checkout`, `git reset`, or `git add -A`.
