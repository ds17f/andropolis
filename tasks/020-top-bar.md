# Task 020: Top app bar + actions menu (UI redesign, stage 1)

## Goal
Add a modern top app bar and move the infrequent game actions off the crowded
bottom row into it. Match the mockup.

## Design reference
Read `build/mockups/project/Main.dc.html` and `build/mockups/project/Menu.dc.html`
(HTML mockups) for the exact look: dark translucent top bar, city name + date,
a Funds/Population/Score HUD row, a play/pause button, a speed chip, and a ⋮
overflow menu (New / Save / Load / Budget / City Evaluation). Colors are theme
resources: `@color/amber` (#F5A623), `@color/surface_dark` (#12161C),
`@color/on_surface` (#EEF2F6), `@color/on_surface_muted` (#9AA7B4).

## Context
`MainActivity` is fully programmatic. Today the root `LinearLayout` has: a `hud`
TextView, the `MapView` (weight 1), then a `HorizontalScrollView` (`barLayout`)
holding Build/New/Save/Load/Speed/Tax/Budget/Eval/Preview/Confirm/Cancel + 16 tool
buttons. Move the game-management actions into a new top bar; keep the tools and
build controls at the bottom for now (later stages).

The tick loop (`tickLoop`) reads `getStats(handle, statsBuf)` and currently sets
`hud.text`. Repoint it to the new HUD fields. `speed` (0..3), `speedNames`,
`speedTicks` already exist (from the speed feature). Engine calls stay on `sim`.

## Interface (implementation notes for MainActivity.kt)

### Build a top bar (replace the plain `hud` TextView), added as the FIRST child of root:
A vertical `LinearLayout` (`topBar`), background `#EE12161C`, padding (16,40,16,12):
- **Row 1** (horizontal, center-vertical):
  - Left vertical block (weight 1): `cityTitle` TextView "Micropolis" (19sp, bold,
    `@color/on_surface`); `subtitle` TextView (13sp, `@color/on_surface_muted`),
    updated each tick to `"$monthName $year"`.
  - `playPauseBtn` Button: text `"⏸"` when running, `"▶"` when paused. On click
    toggle pause: if `speed == 0` set `speed = lastRunSpeed`; else `lastRunSpeed =
    speed; speed = 0`. Update its text. Add `private var lastRunSpeed = 2`.
  - `speedChip` Button: text `speedNames[speed]`. On click cycle among 1→2→3→1
    (never 0): pick next, set `speed = next; lastRunSpeed = next`, update text (and
    the play/pause text since it un-pauses).
  - `overflowBtn` Button: text `"⋮"`. On click open a `PopupMenu` (below).
- **Row 2** (horizontal, gap via layout margins): three HUD chips — small vertical
  blocks, each a label TextView (10sp, muted, caps) over a value TextView (15sp,
  bold): `fundsChip` ("Funds" / `"$"+funds`, value color `@color/amber`),
  `popChip` ("Population" / pop), `scoreChip` ("Score" / score). Give the chips a
  subtle rounded background if easy (`setBackgroundColor(0x14FFFFFF)`), else plain.

### Overflow PopupMenu (built programmatically, no XML):
```kotlin
val pm = android.widget.PopupMenu(this, overflowBtn)
pm.menu.add("New city"); pm.menu.add("Save city"); pm.menu.add("Load city")
pm.menu.add("Budget"); pm.menu.add("City evaluation")
pm.setOnMenuItemClickListener { item ->
    when (item.title) {
        "New city" -> sim.post { MicropolisNative.generateRandomCity(handle) }
        "Save city" -> sim.post { MicropolisNative.saveCity(handle, savePath) }
        "Load city" -> sim.post { MicropolisNative.loadCity(handle, savePath) }
        "Budget" -> sim.post { val b = IntArray(12); MicropolisNative.getBudget(handle, b); ui.post { showBudgetDialog(b) } }
        "City evaluation" -> sim.post { val ev = IntArray(7); MicropolisNative.getEvaluation(handle, ev); ui.post { showEvalDialog(ev) } }
    }
    true
}
pm.show()
```

### Remove from the bottom `barLayout`:
Delete the New, Save, Load, Budget, Eval, and Speed buttons (now in the top bar /
menu). KEEP: the Move/Build toggle, Tax, Preview, Confirm, Cancel, and all 16 tool
buttons (they are handled in later stages). Keep the existing `showBudgetDialog`,
`showEvalDialog`, `savePath`, and tool logic.

### tickLoop HUD update:
Replace the `hud.text = ...` block with updates to `subtitle` (`"$monthName $year"`),
`fundsChip` value (`"$"+funds`), `popChip` value (`pop`), `scoreChip` value (`score`).

## Files in scope
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Do not run the emulator; the planner verifies the look on device.

## Constraints
- Change only `MainActivity.kt`.
- Do not change the JNI, C ABI, CMake, engine, `MapView`, or `build.gradle.kts`.
- No new dependencies (Material is already added). Commit when green; stage only
  your file by name. Do not use `git checkout`, `git reset`, or `git add -A`.
