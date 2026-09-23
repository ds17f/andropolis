# Task 040: Modern styled popups

## Goal
Replace the plain grey `AlertDialog` message boxes (Budget, City evaluation, Annual
report, Zone status/Query) with a modern card: a dark rounded panel, a bold title,
tidy label/value rows, and an amber action button — matching the app's panels.

## Context (MainActivity.kt)
- Current dialogs use `AlertDialog.Builder(this).setTitle(...).setMessage(plainText)
  .setPositiveButton(...)`: `showBudgetDialog(b: IntArray)`, `showEvalDialog(ev: IntArray)`,
  `showReportCard(year: Int, resume: Boolean)`, `showZoneStatusDialog(x,y,cat,pop,lv,crime,poll,growth)`.
- `showReportCard` pauses the sim; on dismiss it must STILL resume when `resume` is true
  (`if (resume && speed == 0) { speed = lastRunSpeed; updatePlayPauseText(); updateSpeedChipText() }`).
- `roundedBg(color,radiusDp)`, `dp(v)`, `cityClassNames`, `levelName(i)` exist.

## Interface

### 1. A reusable styled dialog (add to MainActivity)
```kotlin
private fun styledDialog(
    title: String,
    rows: List<Pair<String, String>>,
    actionLabel: String = "OK",
    onAction: (() -> Unit)? = null,
    subtitle: String? = null
) {
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(22), dp(24), dp(18))
        background = roundedBg(0xFF161B22.toInt(), 20)
    }
    col.addView(TextView(this).apply {
        text = title; setTextColor(0xFFEEF2F6.toInt()); textSize = 20f
        setTypeface(null, android.graphics.Typeface.BOLD)
    })
    if (subtitle != null) col.addView(TextView(this).apply {
        text = subtitle; setTextColor(0xFF9AA7B4.toInt()); textSize = 13f; setPadding(0, dp(2), 0, 0)
    })
    col.addView(View(this).apply {
        setBackgroundColor(0x1FFFFFFF); layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(12); bottomMargin = dp(6) }
    })
    for ((k, v) in rows) {
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(7), 0, dp(7))
            addView(TextView(this@MainActivity).apply {
                text = k; setTextColor(0xFF9AA7B4.toInt()); textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(this@MainActivity).apply {
                text = v; setTextColor(0xFFEEF2F6.toInt()); textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
        })
    }
    val btn = Button(this).apply {
        text = actionLabel; background = roundedBg(0xFFF5A623.toInt(), 12)
        setTextColor(0xFF1A1207.toInt()); stateListAnimator = null
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) }
    }
    col.addView(btn)
    val wrap = FrameLayout(this).apply { setPadding(dp(12), 0, dp(12), 0); addView(col) }
    val dialog = androidx.appcompat.app.AlertDialog.Builder(this).setView(wrap).setCancelable(false).create()
    // transparent window so only our rounded panel shows (no grey box)
    dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0))
    btn.setOnClickListener { onAction?.invoke(); dialog.dismiss() }
    dialog.show()
}
```
(Add imports as needed: `android.widget.FrameLayout`, `android.view.View`.)

### 2. Convert the four dialogs to `styledDialog`
- **Budget** (`showBudgetDialog(b)`):
  ```kotlin
  styledDialog("City Budget", listOf(
      "Funds" to "$${b[0]}", "Tax rate" to "${b[1]}%", "Tax income" to "$${b[2]}",
      "Roads" to "$${b[4]} / $${b[3]} (${b[5]}%)",
      "Police" to "$${b[7]} / $${b[6]} (${b[8]}%)",
      "Fire" to "$${b[10]} / $${b[9]} (${b[11]}%)"))
  ```
- **Evaluation** (`showEvalDialog(ev)`):
  ```kotlin
  styledDialog("City Evaluation", listOf(
      "Score" to "${ev[0]} (Δ ${ev[1]})", "Class" to cityClassNames.getOrElse(ev[2]) { "?" },
      "Population" to "${ev[3]} (Δ ${ev[4]})", "Assessed value" to "$${ev[5]}",
      "Approval" to "${ev[6]}%"))
  ```
- **Annual report** (`showReportCard`): keep the sim-resume behavior in `onAction`:
  ```kotlin
  styledDialog("Annual Report", listOf(
      "Class" to cityClassNames.getOrElse(ev[2]) { "?" },
      "Population" to "${ev[3]} (Δ ${ev[4]})", "Score" to "${ev[0]} (Δ ${ev[1]})",
      "Approval" to "${ev[6]}%", "Funds" to "$${b[0]}", "Tax" to "${b[1]}%"),
      actionLabel = "Continue", subtitle = "Year $year",
      onAction = { if (resume && speed == 0) { speed = lastRunSpeed; updatePlayPauseText(); updateSpeedChipText() } })
  ```
- **Zone status** (`showZoneStatusDialog`):
  ```kotlin
  styledDialog("Zone at ($x, $y)", listOf(
      "Tile category" to "$cat", "Population density" to levelName(pop),
      "Land value" to levelName(lv), "Crime rate" to levelName(crime),
      "Pollution" to levelName(poll), "Growth rate" to levelName(growth)))
  ```

## Files in scope
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- The planner verifies on device: the four popups show as dark rounded cards with rows and
  an amber button; the annual report still resumes the sim on Continue.

## Constraints
- Change only `MainActivity.kt`. Do not change the JNI, C ABI, engine, `MapView`,
  drawables, or `build.gradle.kts`. Keep every dialog's data and the report-resume intact.
- No new dependencies. Commit when green; stage only your file by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
