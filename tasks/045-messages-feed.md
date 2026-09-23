# Task 045: Messages feed (recent city news, tap to jump)

## Goal
Keep a log of recent engine messages ("Fire reported!", "More roads required", …) and
show it in a **Messages** panel from the ⋮ menu. Each row shows the game date and the
message; tapping a row with a location jumps the map there.

## Context (MainActivity.kt)
- 038 drains engine events in `tickLoop()` and, on the UI thread, handles
  `type 0` (MESSAGE) with `showBanner(msgText(a))` and sets `lastEventTile` when the
  event has a location (`ex >= 0 && ey >= 0`).
- `subtitle` (TextView) shows the current game date, e.g. "May 2077".
- `showPanel(title, tabs)` / `PanelTab(title, glyph, build)`, `roundedBg`, `dp`,
  `mapView.centerOnTile(x, y)` exist. The ⋮ `PopupMenu` adds items with `pm.menu.add(...)`
  and handles them in `when (item.title)`.

## Interface

### Fields + helper
```kotlin
private class LogEntry(val date: String, val text: String, val x: Int, val y: Int)
private val messageLog = ArrayDeque<LogEntry>()
private fun logMessage(text: String, x: Int, y: Int) {
    messageLog.addFirst(LogEntry(subtitle.text.toString(), text, x, y))   // newest first
    while (messageLog.size > 50) messageLog.removeLast()
}
```

### Record messages
In the event-drain `when (type)` branch for `0 ->` (MESSAGE), add a call right after
`showBanner(msgText(a))`:
```kotlin
logMessage(msgText(a), ex, ey)
```

### The panel
```kotlin
private fun showMessagesPanel() {
    showPanel("Messages", listOf(PanelTab("Recent", "📰") {
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            if (messageLog.isEmpty()) {
                addView(TextView(this@MainActivity).apply {
                    text = "No messages yet."; setTextColor(0xFF9AA7B4.toInt()); setPadding(0, dp(8), 0, dp(8))
                })
            }
            for (m in messageLog) {
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    background = roundedBg(0x0DFFFFFF, 12); setPadding(dp(14), dp(10), dp(14), dp(10))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
                    addView(TextView(this@MainActivity).apply {
                        text = m.date; setTextColor(0xFF7D8B99.toInt()); textSize = 11f
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = m.text; setTextColor(0xFFEEF2F6.toInt()); textSize = 14f
                    })
                    if (m.x >= 0 && m.y >= 0) {
                        setOnClickListener { mapView.centerOnTile(m.x, m.y) }
                    }
                })
            }
        }
    }))
}
```

### Menu
Add `pm.menu.add("Messages")` (before `"Settings"`) and a handler branch
`"Messages" -> showMessagesPanel()`.

## Files in scope
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- The planner verifies on device: after a disaster or advisory, ⋮ → Messages lists it with
  its date; tapping a located message jumps the map.

## Constraints
- Change only `MainActivity.kt`. Do not change the JNI, C ABI, engine, `MapView`, drawables,
  or `build.gradle.kts`. Keep the banner, zoom-to-event, and the rest working.
- No new dependencies. Commit when green; stage only your file by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
