# Task 041: Settings screen + minimap navigation mode

## Goal
Add a **Settings** screen (off the ⋮ menu) with a **Minimap** toggle:
- **Minimap ON:** the minimap is shown and the main view locks to a fixed zoom; you move
  around by tapping/dragging the minimap (pinch/two-finger pan disabled).
- **Minimap OFF:** the minimap is hidden and you navigate the main view with pinch-zoom
  and two-finger pan (today's behavior).
The choice persists.

## Context
- `MapView` has private `scale`, `clampPan()`, a `scaleDetector` (pinch) and two-finger
  pan in `onTouchEvent`; one finger builds. `MinimapView minimap` floats in `mapContainer`
  and already does tap-to-jump via `mapView.centerOnTile` (036).
- `MainActivity`: `prefs` (SharedPreferences), `showPanel(title, tabs)`, the ⋮ `PopupMenu`
  (`pm.menu.add(...)` + `when (item.title)` handler ending in `true`), `dp(v)`, `roundedBg`.

## Interface

### 1. MapView.kt — a navigation-lock mode
```kotlin
private var navLocked = false

/** Lock to a fixed zoom and disable pinch/two-finger pan (minimap navigation), or unlock. */
fun setNavLocked(locked: Boolean, fixedScale: Float) {
    navLocked = locked
    if (locked) { scale = fixedScale; clampPan(); invalidate() }
}
```
In `onTouchEvent`, gate the zoom/pan on `!navLocked` (leave one-finger building intact):
- Change `scaleDetector.onTouchEvent(event)` to `if (!navLocked) scaleDetector.onTouchEvent(event)`.
- In `ACTION_POINTER_DOWN`, wrap the body in `if (!navLocked) { ... }` so a second finger
  does not start a pan when locked.
- In the `ACTION_MOVE` two-finger branch, change the condition
  `if (event.pointerCount >= 2)` to `if (event.pointerCount >= 2 && !navLocked)` so extra
  fingers don't pan when locked (they fall through to the build branch harmlessly).

### 2. MainActivity.kt — settings state + apply
Fields:
```kotlin
private var minimapNav = false
private val navZoom = 5f            // fixed zoom when minimap navigation is on
```
Load in `onCreate` (after `prefs` usable, before/around where the minimap is created):
`minimapNav = prefs.getBoolean("minimapNav", false)`.

Apply helper, and call it once after `mapView` + `minimap` exist:
```kotlin
private fun applyMinimapMode() {
    if (minimapNav) {
        minimap.visibility = View.VISIBLE
        mapView.setNavLocked(true, navZoom)
    } else {
        minimap.visibility = View.GONE
        mapView.setNavLocked(false, navZoom)
    }
}
```

### 3. Settings panel (opened from the ⋮ menu)
Add `pm.menu.add("Settings")` and a handler branch `"Settings" -> showSettingsPanel()`.
```kotlin
private fun showSettingsPanel() {
    showPanel("Settings", listOf(PanelTab("General") {
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(10))
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    addView(TextView(this@MainActivity).apply {
                        text = "Minimap navigation"; setTextColor(0xFFEEF2F6.toInt()); textSize = 15f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = "Show the minimap and move with it (fixed zoom). Off = pinch zoom."
                        setTextColor(0xFF9AA7B4.toInt()); textSize = 12f
                    })
                })
                addView(android.widget.Switch(this@MainActivity).apply {
                    isChecked = minimapNav
                    setOnCheckedChangeListener { _, checked ->
                        minimapNav = checked
                        prefs.edit().putBoolean("minimapNav", checked).apply()
                        applyMinimapMode()
                    }
                })
            })
        }
    }))
}
```

## Files in scope
- `android/app/src/main/java/micropolis/port/MapView.kt`: edit.
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- The planner verifies on device: Settings has a Minimap toggle; ON shows the minimap at a
  fixed zoom and disables pinch (minimap moves the view); OFF hides the minimap and restores
  pinch-zoom; the setting survives a restart.

## Constraints
- Change only the two files above. Do not change the JNI, C ABI, engine, drawables, or
  `build.gradle.kts`. Keep building, overlays, ghost, and the other pills working.
- No new dependencies. Commit when green; stage only your files by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
