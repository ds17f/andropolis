# Task 063: "New game" screen — new map, scenarios, sample cities

## Goal
Replace the placeholder `showNewGame()` with a bottom sheet that has three tabs:
**New map** (terrain choices + Generate), **Scenarios** (the 8 original scenarios), and
**Cities** (the sample cities that ship with Micropolis).

## Context (read only these)
- `NewGameScreen.kt` (small): the placeholder you replace. Keep the signature.
- `NewGame.kt` (small, read it all): `SCENARIOS` (list of `ScenarioInfo(id, name, year,
  asset, challenge)`), `sampleCities(): List<String>`, and the three actions
  `startNewMap(trees, lakes, river, island, onDone)`, `startScenario(sc, onDone)`,
  `startSampleCity(name, onDone)`. They do all the engine work; you only build UI.
- `UiKit.kt`: `showPanel(title, tabs: List<PanelTab>, onDismiss)` shows a bottom sheet with
  tabs (`PanelTab(title, glyph) { view }`); `panelCard(glyph, label) { onClick }: CardHandle`
  and `addCard(grid, card)` make selectable cards (`CardHandle.setSelected(Boolean)`);
  `dp(Int)`. See `showSimulationPanel()` in `Panels.kt` for the card-grid + `select()` pattern.

## Interface — rewrite `NewGameScreen.kt`
```kotlin
package micropolis.port

/** ⋮ → New game. `resume` restarts the sim when the sheet closes without starting a game. */
internal fun MainActivity.showNewGame(resume: () -> Unit)
```

Build it with your own `com.google.android.material.bottomsheet.BottomSheetDialog(this)`
(do not use `showPanel`, so you can dismiss it):
- Background `0xFF12161C`, padding 20dp, a title TextView "New game" (18sp, bold, `0xFFEEF2F6`).
- Three tab buttons in a row ("New map", "Scenarios", "Cities"), selected one amber
  (`0xFFF5A623` background, `0xFF1A1207` text), others `0x1FFFFFFF` background,
  `0xFF9AA7B4` text; below them a `FrameLayout` whose content switches with the tab.
- Track `var started = false`. `sheet.setOnDismissListener { if (!started) resume() }`.
  Every start action does `started = true; sheet.dismiss(); <action>(…, resume)`.

### Tab "New map"
Three option rows, each: a label (13sp, `0xFF9AA7B4`) and a `GridLayout` (columnCount 3 or 4)
of `panelCard`s with single selection (default in **bold** below):
- **Trees**: "None" → 0, **"Some" → -1**, "Lots" → 250
- **Lakes**: "None" → 0, **"Some" → -1**, "Lots" → 20
- **River**: "None" → 0, **"Yes" → -1**
- **Island**: "Never" → 0, **"Sometimes" → -1**, "Always" → 1
Glyphs: Trees 🌲, Lakes 💧, River 〰, Island 🏝 (use the same glyph on each card of a row).
Then a full-width `com.google.android.material.button.MaterialButton` "Generate city"
(amber background tint, dark text) → `startNewMap(trees, lakes, river, island, resume)`.

### Tab "Scenarios"
A vertical list, one card per `SCENARIOS` entry: a `LinearLayout` (padding 12dp, rounded
background via `roundedBg(0x14FFFFFF, 12)`, top margin 8dp, clickable) with
`"${sc.name} · ${sc.year}"` (15sp, bold, `0xFFEEF2F6`) and `sc.challenge` (12sp,
`0xFF9AA7B4`). Tap → `startScenario(sc, resume)`.
Put the list in a `ScrollView` (height at most 60% of the screen).

### Tab "Cities"
Same list style for each name in `sampleCities()`: the name with its first letter upper-case
(15sp, bold). Tap → `startSampleCity(name, resume)`. In a `ScrollView` too.

## Files in scope
- `android/app/src/main/java/micropolis/port/NewGameScreen.kt` only.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Planner verifies on device: ⋮ → New game shows the three tabs; Generate makes a new map
  and asks for a name; a scenario loads (e.g. Tokyo · 1957); a sample city loads.

## Constraints
- Only `NewGameScreen.kt` changes. Do not change `NewGame.kt`. Do not edit this spec.
- Do not read engine sources or git history. No new dependencies.
- Commit when green; stage only your file by name. Do not use `git checkout`, `git reset`, or `git add -A`.
