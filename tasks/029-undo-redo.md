# Task 029: Always-on Undo/Redo — replaces trial mode

## Goal
Remove the "trial" concept entirely and replace it with **always-on Undo/Redo**.
Every build action is live and reversible: after each build stroke the app snapshots
the city; Undo/Redo step through the snapshots. Nothing to arm or toggle.

## Context
Building goes: `MapView` touch → `onTileTap(x,y)` → `MainActivity` posts
`MicropolisNative.doTool(...)` on the `sim` Handler thread. A one-finger tap or drag
is ONE build stroke (see `MapView.onTouchEvent`: `ACTION_DOWN`/`ACTION_MOVE` call
`buildAt`, `ACTION_UP` ends it; two-finger gestures pan, not build). Snapshots use the
existing `MicropolisNative.saveCity(handle, path)` / `loadCity(handle, path)` (both run
on `sim`, FIFO after the queued `doTool` calls, so a snapshot posted at stroke end
captures the finished result). `roundedBg(color,radiusDp)`, `dp(v)`, `sim`, `handle`,
and `filesDir` all exist.

Trial today: fields `trialActive`, `trialPath`; the `trialPill`/`trialState` views in
the bottom bar; `showTrialPanel()`; `updateTrialPill()`. REMOVE ALL of these.

## Interface

### MapView.kt — signal stroke end with whether a build happened:
- Add `var onStrokeEnd: ((built: Boolean) -> Unit)? = null`.
- Add a private `var built = false`.
- Set `built = false` in `ACTION_DOWN` and in `ACTION_POINTER_DOWN`.
- In `buildAt`, set `built = true` right where it calls `onTileTap?.invoke(...)`
  (only when a tile is actually built).
- In `ACTION_UP, ACTION_CANCEL`: call `onStrokeEnd?.invoke(built)` (keep the existing
  `panning = false; lastBuiltTile = null; performClick()`).

### MainActivity.kt — snapshot history with a cursor:
Add fields:
```kotlin
private val snapDir by lazy { java.io.File(filesDir, "undo").apply { mkdirs() } }
private val history = mutableListOf<String>()   // snapshot file paths, oldest..newest
private var cursor = -1                          // index of the current live state
private var snapSeq = 0
private val UNDO_CAP = 24
private lateinit var undoBtn: Button
private lateinit var redoBtn: Button
```
Helpers:
```kotlin
private fun newSnapPath(): String { snapSeq++; return java.io.File(snapDir, "s$snapSeq.cty").absolutePath }

/** Snapshot the current (post-build) state as the new head, dropping any redo branch. */
private fun commitSnapshot() {
    // drop redo branch (everything after cursor)
    while (history.size > cursor + 1) { java.io.File(history.removeAt(history.size - 1)).delete() }
    val path = newSnapPath()
    sim.post { MicropolisNative.saveCity(handle, path) }
    history.add(path); cursor = history.size - 1
    // trim oldest if over cap
    while (history.size > UNDO_CAP) { java.io.File(history.removeAt(0)).delete(); cursor-- }
    updateUndoButtons()
}
private fun undo() { if (cursor > 0) { cursor--; sim.post { MicropolisNative.loadCity(handle, history[cursor]) }; updateUndoButtons() } }
private fun redo() { if (cursor < history.size - 1) { cursor++; sim.post { MicropolisNative.loadCity(handle, history[cursor]) }; updateUndoButtons() } }
private fun updateUndoButtons() {
    val canUndo = cursor > 0; val canRedo = cursor < history.size - 1
    undoBtn.isEnabled = canUndo; undoBtn.alpha = if (canUndo) 1f else 0.35f
    redoBtn.isEnabled = canRedo; redoBtn.alpha = if (canRedo) 1f else 0.35f
}
```
Wiring:
- `mapView.onStrokeEnd = { built -> if (built) commitSnapshot() }`.
- Take the INITIAL snapshot once, after the city is generated/loaded at startup: on the
  `sim` thread, right after the existing generate/init sequence, post `commitSnapshot()`
  back on the `ui`/main thread (so `history`/`cursor` are only touched on the main
  thread). If simpler, call `commitSnapshot()` at the end of `onCreate` after the map is
  set up — the city already exists by then. Ensure `history` starts with exactly one
  entry (`cursor == 0`) so Undo is disabled until the first build.

### Bottom bar — replace the trial pill with Undo/Redo buttons:
In the bottom bar row that currently holds `toolPill` (weight 1) + `trialPill`, remove
`trialPill` and add two compact icon buttons after the tool pill:
```kotlin
undoBtn = Button(this).apply {
    text = "↶"; background = roundedBg(0xFF1A222A.toInt(), 18); setTextColor(0xFFEEF2F6.toInt())
    stateListAnimator = null; setTextSize(18f); setPadding(dp(14), dp(8), dp(14), dp(8))
    setOnClickListener { undo() }
}
redoBtn = Button(this).apply {
    text = "↷"; background = roundedBg(0xFF1A222A.toInt(), 18); setTextColor(0xFFEEF2F6.toInt())
    stateListAnimator = null; setTextSize(18f); setPadding(dp(14), dp(8), dp(14), dp(8))
    setOnClickListener { redo() }
}
```
Add them to the row with `dp(8)` left margins (WRAP_CONTENT width). Call
`updateUndoButtons()` once after building them (both start disabled).

### Remove all trial code:
Delete `trialActive`, `trialPath`, the `trialPill`/`trialState` fields and their view
construction, `showTrialPanel()`, `updateTrialPill()`, and every call to them.

## Files in scope
- `android/app/src/main/java/micropolis/port/MapView.kt`: edit (stroke-end signal).
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Undo/redo can't be tested here; the planner verifies on device.

## Constraints
- Change only the two files above. Do not change the JNI, C ABI, CMake, engine,
  drawables, or `build.gradle.kts`.
- No new dependencies. Commit when green; stage only your files by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
