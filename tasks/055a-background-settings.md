# Task 055: Settings → "Background" tab (preferences only)

## Goal
Add a second tab, **Background**, to the Settings panel. It holds the settings for the
coming background-play feature (DESIGN.md §12):
- **Run in background** (on/off).
- **Pace while closed**: 1, 2, 5 or 10 game-years per real hour.
- For each event group — **Disasters, New year, City problems, Milestones** — two
  check boxes: **Notify** and **Pause**.

This task only shows and saves the settings. Nothing reads them yet (tasks 056–057 do).

## Context (read only these parts)
- `android/app/src/main/java/micropolis/port/Panels.kt` lines 268–290:
  `showSettingsPanel()` calls `showPanel("Settings", listOf(PanelTab("General") { … }), onDismiss)`.
- `android/app/src/main/java/micropolis/port/UiKit.kt`:
  - line 84 `panelCard(glyph, label, onClick): CardHandle` — a selectable card;
    `CardHandle.setSelected(Boolean)` highlights it.
  - line 111 `addCard(grid: GridLayout, h: CardHandle)`.
  - line 281 `settingsToggle(title, desc, checked, onChange): View` — title, muted
    description, switch on the right.
- `MainActivity` has `prefs` (SharedPreferences) and `dp(Int)`. Look at
  `showSimulationPanel()` in `Panels.kt` for the card-grid + `select()` pattern.
- Do NOT attach or read all of `MainActivity.kt`; you do not need it.

## Interface

### 1. New file `BackgroundPrefs.kt` (copy exactly)
```kotlin
package micropolis.port

import android.content.SharedPreferences

/** Settings for background play (DESIGN.md §12). Task 055 shows them; 056/057 read them. */
object BackgroundPrefs {
    const val KEY_ENABLED = "bgEnabled"
    const val KEY_PACE = "bgPace"                     // index into PACE_YEARS_PER_HOUR
    val PACE_YEARS_PER_HOUR = intArrayOf(1, 2, 5, 10)
    const val DEFAULT_PACE = 2                        // 5 game-years per hour

    /** Event groups: id (used in pref keys), title, description, default notify, default pause. */
    class Group(val id: String, val title: String, val desc: String, val notify: Boolean, val pause: Boolean)
    val GROUPS = listOf(
        Group("disasters",  "Disasters",      "Fire, flood, tornado, earthquake, monster, meltdown, crashes.", true, true),
        Group("newYear",    "New year",       "The annual report is ready.",                                    true, false),
        Group("problems",   "City problems",  "Brownouts, traffic jams, high crime or pollution, low funds.",   true, false),
        Group("milestones", "Milestones",     "Your city reaches a new size: town, city, capital, …",          true, false),
    )

    fun notifyKey(g: Group) = "bgNotify_${g.id}"
    fun pauseKey(g: Group) = "bgPause_${g.id}"

    fun enabled(p: SharedPreferences) = p.getBoolean(KEY_ENABLED, false)
    fun paceIndex(p: SharedPreferences) = p.getInt(KEY_PACE, DEFAULT_PACE).coerceIn(0, PACE_YEARS_PER_HOUR.size - 1)
    fun notify(p: SharedPreferences, g: Group) = p.getBoolean(notifyKey(g), g.notify)
    fun pause(p: SharedPreferences, g: Group) = p.getBoolean(pauseKey(g), g.pause)
}
```

### 2. New file `BackgroundSettings.kt`
```kotlin
package micropolis.port
// imports as needed (android.view.View, android.widget.*)

/** Settings → Background tab (task 055). */
internal fun MainActivity.backgroundSettingsTab(): View
```
It returns a vertical `LinearLayout` with, in order:
1. `settingsToggle("Run in background", "Keep the city going while the app is closed. You get a notification when something happens.", BackgroundPrefs.enabled(prefs)) { c -> prefs.edit().putBoolean(BackgroundPrefs.KEY_ENABLED, c).apply() }`
2. A section label **"Pace while closed"** (TextView, color `0xFF9AA7B4`, 13sp, padding top 12dp, bottom 4dp).
3. A `GridLayout` with `columnCount = 4` and one `panelCard` per pace:
   glyphs `"🐢", "🚶", "🚗", "🚀"`, labels `"1 yr/h", "2 yrs/h", "5 yrs/h", "10 yrs/h"`.
   Tapping card `i` saves `prefs.edit().putInt(BackgroundPrefs.KEY_PACE, i).apply()` and
   selects it; the card for `BackgroundPrefs.paceIndex(prefs)` starts selected
   (same `handles` + `select()` pattern as the Speed tab in `showSimulationPanel()`).
4. A section label **"Events"** (same style as 2).
5. For each `g` in `BackgroundPrefs.GROUPS`, one row: a horizontal `LinearLayout`
   (padding 8dp top/bottom, gravity center-vertical) with
   - left, weight 1: a vertical `LinearLayout` with `g.title` (color `0xFFEEF2F6`, 15sp,
     bold) and `g.desc` (color `0xFF9AA7B4`, 12sp);
   - right: two `android.widget.CheckBox`es with text `"Notify"` and `"Pause"`
     (text color `0xFFEEF2F6`), checked from `BackgroundPrefs.notify(prefs, g)` /
     `BackgroundPrefs.pause(prefs, g)`; on change, save the Boolean under
     `BackgroundPrefs.notifyKey(g)` / `BackgroundPrefs.pauseKey(g)`.

### 3. `Panels.kt` — one small change only
In `showSettingsPanel()`, add a second tab after the General tab:
`PanelTab("Background") { backgroundSettingsTab() }`
Do not change anything else in `Panels.kt`.

## Files in scope
- `android/app/src/main/java/micropolis/port/BackgroundPrefs.kt`: new.
- `android/app/src/main/java/micropolis/port/BackgroundSettings.kt`: new.
- `android/app/src/main/java/micropolis/port/Panels.kt`: add the one tab (above).

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Planner verifies on device: Settings has General and Background tabs; the Background
  tab shows the switch, the 4 pace cards and 4 event rows with two check boxes each;
  changes survive closing and reopening Settings.

## Constraints
- **Copy the code blocks above exactly.** Everything you need is in this spec. Do not
  read engine sources (`MicropolisCore/`) or git history, and do not read whole large
  files — read only the line ranges named above.
- Only ADD code. Do not delete or restructure existing code. Do not edit this spec.
- Do not change the C/C++ code, CMake, or `build.gradle.kts`.
- Commit when green; stage only your files by name. Do not use `git checkout`, `git reset`, or `git add -A`.
