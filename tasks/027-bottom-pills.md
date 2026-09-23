# Task 027: Clean bottom bar — pills that open panels

## Goal
Fix the overflowing bottom controls. Make the bottom a clean row of two rounded
pill-buttons (matching the tool pill the user likes): the tool pill (opens the
palette) and a "Build session" pill (opens a panel with the Trial controls). No
inline Trial/Keep/Revert buttons.

## Context
After the gesture task, there is no Move/Build toggle. The bottom still has the
tool pill plus separate `trialBtn`/`keepBtn`/`revertBtn` buttons that do not fit.
`trialActive`, `trialPath`, `savePath`, `updateTrialControls()`, and the pill
(`pillIcon`/`pillName`/`updatePill()`/`openPalette()`) exist. There is a
`roundedBg(color, radiusDp)` and `dp(v)` helper (from the styling task). Material
`BottomSheetDialog` is available (used by `openPalette`).

## Interface (implementation notes for MainActivity.kt)

### Bottom row = two pills:
Lay out the bottom as a horizontal `LinearLayout`:
- The **tool pill** (existing), with `layoutParams` weight 1 so it takes the space.
- A new **trial pill** (`trialPill`, `roundedBg(0xFF1A222A.toInt(), 18)`, padding
  like the tool pill, `dp(8)` left margin, `WRAP_CONTENT` width): an ImageView or a
  small label "⚑" plus a `TextView` "Build" over a small state line ("Trial: on"
  when `trialActive`, else "Trial: off"). `setOnClickListener { showTrialPanel() }`.
- Remove `trialBtn`, `keepBtn`, `revertBtn`, and `updateTrialControls()`.

### The trial panel (bottom sheet):
```kotlin
private fun showTrialPanel() {
    val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(16), dp(20), dp(28)); setBackgroundColor(0xFF12161C.toInt())
    }
    col.addView(TextView(this).apply {
        text = "Build session"; setTextColor(0xFFEEF2F6.toInt()); textSize = 18f
        setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 0, 0, dp(8))
    })
    fun panelButton(label: String, amber: Boolean, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            background = roundedBg(if (amber) 0xFFF5A623.toInt() else 0x1FFFFFFF, 12)
            setTextColor(if (amber) 0xFF1A1207.toInt() else 0xFFEEF2F6.toInt())
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) }
            setOnClickListener { onClick(); sheet.dismiss() }
        }
    if (!trialActive) {
        col.addView(TextView(this).apply {
            text = "Snapshot the city, build freely, then keep or revert."
            setTextColor(0xFF9AA7B4.toInt()); textSize = 13f; setPadding(0, 0, 0, dp(4))
        })
        col.addView(panelButton("Start trial", true) {
            trialActive = true; sim.post { MicropolisNative.saveCity(handle, trialPath) }; updateTrialPill()
        })
    } else {
        col.addView(panelButton("Keep changes", true) {
            trialActive = false; java.io.File(trialPath).delete(); updateTrialPill()
        })
        col.addView(panelButton("Revert changes", false) {
            sim.post { MicropolisNative.loadCity(handle, trialPath) }; trialActive = false; updateTrialPill()
        })
    }
    val sv = ScrollView(this); sv.addView(col); sheet.setContentView(sv); sheet.show()
}

private fun updateTrialPill() {
    // update the trial pill's state line to reflect trialActive (find your state TextView)
}
```
Call `updateTrialPill()` once after building the pill.

## Files in scope
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Do not run the emulator; the planner verifies the layout on device.

## Constraints
- Change only `MainActivity.kt`. Keep all engine/tool/save/trial logic working.
- Do not change the JNI, C ABI, CMake, engine, `MapView`, drawables, or
  `build.gradle.kts`.
- No new dependencies. Commit when green; stage only your file by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
