# Task 048: The ⋮ menu pauses the simulation

## Goal
Opening the ⋮ overflow menu pauses the sim. When the player is done it resumes at the
speed it had before. If the chosen item opens its own screen (Save dialog, Load sheet,
Settings, Messages, New-city name prompt), the sim stays paused until that screen closes.
If the sim was already paused when the menu opened, nothing resumes it.

## Context (MainActivity.kt)
- Speed: `speed` (0 = paused), `lastRunSpeed`, `updatePlayPauseText()`, `updateSpeedChipText()`.
- The menu is a `PopupMenu` built in `overflowBtn`'s click listener; items are handled in
  `pm.setOnMenuItemClickListener { item -> when (item.title) { ... }; true }`.
- Screens opened from the menu: `showSaveDialog()` (AlertDialog), `showLoadDialog()`
  (BottomSheetDialog), `showSettingsPanel()` and `showMessagesPanel()` (both via
  `showPanel(title, tabs)`), and "New city" → `promptCityName(isFirst = true)` (AlertDialog).
- `showPanel` creates a `BottomSheetDialog` named `sheet`.

## Interface

### 1. A pause token
```kotlin
/** Pause now; returns a function that resumes the previous speed (idempotent). */
private fun pauseForUi(): () -> Unit {
    if (speed == 0) return {}                      // already paused: never auto-resume
    val prev = speed
    speed = 0; updatePlayPauseText(); updateSpeedChipText()
    var done = false
    return {
        if (!done && speed == 0) { speed = prev; lastRunSpeed = prev; updatePlayPauseText(); updateSpeedChipText() }
        done = true
    }
}
```

### 2. Let the screens report when they close
- `showPanel(title: String, tabs: List<PanelTab>, onDismiss: (() -> Unit)? = null)` —
  add `sheet.setOnDismissListener { onDismiss?.invoke() }`.
- `showSettingsPanel(onDismiss: (() -> Unit)? = null)` and
  `showMessagesPanel(onDismiss: (() -> Unit)? = null)` pass it to `showPanel`.
- `showLoadDialog(onDismiss: (() -> Unit)? = null)` — `sheet.setOnDismissListener { onDismiss?.invoke() }`.
  (Its ✕-delete path re-opens the sheet: pass the same `onDismiss` through, and don't call it
  for that internal dismiss — e.g. set a local flag before `sheet.dismiss()` on delete.)
- `showSaveDialog(onDismiss: (() -> Unit)? = null)` and
  `promptCityName(isFirst: Boolean, onDismiss: (() -> Unit)? = null)` — build with
  `.create()`, call `dialog.setOnDismissListener { onDismiss?.invoke() }`, then `show()`.
All new parameters default to null so existing callers are unchanged.

### 3. Wire the menu
In `overflowBtn`'s click listener, before `pm.show()`:
```kotlin
val resume = pauseForUi()
var handedOff = false                     // true when an item takes over the resume
pm.setOnDismissListener { if (!handedOff) resume() }
```
In the item handler, for the items that open a screen, set `handedOff = true` and pass
`resume` as that screen's `onDismiss`:
- `"Save city" -> { handedOff = true; showSaveDialog(resume) }`
- `"Load city" -> { handedOff = true; showLoadDialog(resume) }`
- `"Settings" -> { handedOff = true; showSettingsPanel(resume) }`
- `"Messages" -> { handedOff = true; showMessagesPanel(resume) }`
- `"New city"` → keep its existing sim work; in its `ui.post { ... }` call
  `promptCityName(isFirst = true, onDismiss = resume)` and set `handedOff = true` in the branch.
Items that act instantly (Redo, Annual report toggle) leave `handedOff` false, so the
menu's own dismiss resumes.
Note: `PopupMenu` dismisses *after* the click handler runs, so setting `handedOff`
inside the handler is in time.

## Files in scope
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Planner verifies on device: open ⋮ → sim pauses; tap outside → resumes at the old speed;
  ⋮ → Settings → close Settings → resumes; if paused beforehand, stays paused.

## Constraints
- Change only `MainActivity.kt`. No new dependencies. Keep every menu action working.
- Commit when green; stage only your file by name. Do not use `git checkout`,
  `git reset`, or `git add -A`.
