package micropolis.port

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

// Bottom panels and small dialogs: Simulation, Overlay, Messages, Settings, tool palette, city name, zone status.
// Extension functions on MainActivity; state lives in MainActivity.kt.

internal fun MainActivity.showMessagesPanel(onDismiss: (() -> Unit)? = null) {
    showPanel("Messages", listOf(PanelTab("Recent", "📰") {
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            if (messageLog.isEmpty()) {
                addView(TextView(this@showMessagesPanel).apply {
                    text = "No messages yet."; setTextColor(0xFF9AA7B4.toInt()); setPadding(0, dp(8), 0, dp(8))
                })
            }
            for (m in messageLog) {
                addView(LinearLayout(this@showMessagesPanel).apply {
                    orientation = LinearLayout.VERTICAL
                    background = roundedBg(0x0DFFFFFF, 12); setPadding(dp(14), dp(10), dp(14), dp(10))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
                    addView(TextView(this@showMessagesPanel).apply {
                        text = m.date; setTextColor(0xFF7D8B99.toInt()); textSize = 11f
                    })
                    addView(TextView(this@showMessagesPanel).apply {
                        text = m.text; setTextColor(0xFFEEF2F6.toInt()); textSize = 14f
                    })
                    if (m.x >= 0 && m.y >= 0) {
                        setOnClickListener { mapView.centerOnTile(m.x, m.y) }
                    }
                })
            }
        }
    }), onDismiss)
}

internal fun MainActivity.promptCityName(isFirst: Boolean, onDismiss: (() -> Unit)? = null) {
    val input = android.widget.EditText(this).apply {
        setText(if (isFirst) "" else cityName)
        hint = "Name your city"
        setSingleLine()
    }
    androidx.appcompat.app.AlertDialog.Builder(this)
        .setTitle(if (isFirst) "Name your city" else "Rename city")
        .setView(input)
        .setPositiveButton("OK") { _, _ ->
            val name = input.text.toString().trim().ifEmpty { "Micropolis" }
            cityName = name
            prefs.edit().putString("cityName", name).apply()
            toolbar.title = name
        }
        .setCancelable(false)
        .create().also { dialog ->
            dialog.setOnDismissListener { onDismiss?.invoke() }
            dialog.show()
        }
}

internal fun MainActivity.pauseForUi(): () -> Unit {
    if (speed == 0) return {}  // already paused: never auto-resume
    val prev = speed
    speed = 0; updatePlayPauseText(); updateSpeedChipText()
    var done = false
    return {
        if (!done && speed == 0) {
            speed = prev; lastRunSpeed = prev; updatePlayPauseText(); updateSpeedChipText()
        }
        done = true
    }
}

internal fun MainActivity.updatePill() {
    val move = currentTool == MOVE_TOOL
    val ti = if (move) moveItem else allTools.first { it.value == currentTool }
    pillIcon.setImageResource(ti.icon)
    pillName.text = ti.label
    pillClose.visibility = if (move) View.GONE else View.VISIBLE
    mapView.moveMode = move
    mapView.toolFootprint = if (move) 1 else footprintOf(currentTool)
    mapView.straightLineTool = !move && isStraightLineTool(currentTool)
    mapView.tapOnlyTool = currentTool == 5   // Query: tap to inspect, never on drag/pinch
}

/** Pick a tool (or MOVE_TOOL) and restart the idle timer. */
internal fun MainActivity.selectTool(tool: Int) {
    currentTool = tool
    lastToolUseMs = android.os.SystemClock.uptimeMillis()
    updatePill()
}

internal fun MainActivity.autoJumpTo(x: Int, y: Int) {
    if (viewBeforeJump == null) viewBeforeJump = mapView.saveView()
    mapView.centerOnTile(x, y)
    messageBanner.text = messageBanner.text.toString() + "   ↩ Back"
}

internal fun MainActivity.statusWord(i: Int) = zoneStatusWords.getOrElse(i - 1) { "—" }

internal fun MainActivity.showZoneStatusDialog(x: Int, y: Int, cat: Int, pop: Int, lv: Int, crime: Int, poll: Int, growth: Int) {
    styledDialog(tileCategoryNames.getOrElse(cat - 1) { "Clear" }, listOf(
        "Population density" to statusWord(pop),
        "Land value" to statusWord(lv), "Crime" to statusWord(crime),
        "Pollution" to statusWord(poll), "Growth" to statusWord(growth)),
        subtitle = "Tile ($x, $y)")
}

internal fun MainActivity.openPalette() {
    val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
    val scroll = android.widget.ScrollView(this)
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(20, 12, 20, 28)
        setBackgroundColor(0xFF12161C.toInt())
    }
    val moveGrid = android.widget.GridLayout(this).apply { columnCount = 4 }
    moveGrid.addView(buildToolCard(moveItem, sheet))
    col.addView(moveGrid)
    for ((cat, items) in toolCategories) {
        col.addView(android.widget.TextView(this).apply {
            text = cat.uppercase()
            setTextColor(0xFF7D8B99.toInt())
            textSize = 11f
            setPadding(4, 20, 0, 8)
            letterSpacing = 0.1f
        })
        val grid = android.widget.GridLayout(this).apply { columnCount = 4 }
        for (ti in items) grid.addView(buildToolCard(ti, sheet))
        col.addView(grid)
    }
    scroll.addView(col)
    sheet.setContentView(scroll)
    sheet.show()
}

/** Toolbar play/pause icon (amber play while paused) and the "date · speed" subtitle. */
internal fun MainActivity.updatePlayPauseText() {
    val item = toolbar.menu.findItem(R.id.action_play_pause)
    item.setIcon(if (speed == 0) R.drawable.ic_play else R.drawable.ic_pause)
    item.title = if (speed == 0) "Resume" else "Pause"
    item.icon?.mutate()?.setTint(if (speed == 0) 0xFFF5A623.toInt() else 0xFFEEF2F6.toInt())
    updateSubtitle()
}

internal fun MainActivity.updateSpeedChipText() { simState.text = speedNames[speed]; updateSubtitle() }

internal fun MainActivity.updateSubtitle() {
    toolbar.subtitle = if (speed == 0) "$dateText · Paused" else "$dateText · ${speedNames[speed]}"
}

/** The ⋮ menu. Opening it pauses the sim; it resumes on dismiss unless a dialog takes over. */
internal fun MainActivity.showOverflowMenu(anchor: View) {
    val resume = pauseForUi()
    val pm = PopupMenu(this@showOverflowMenu, anchor)
    pm.menu.add("Redo").isEnabled = redoStack.isNotEmpty()
    pm.menu.add("New city")
    pm.menu.add("Save city")
    pm.menu.add("Load city")
    pm.menu.add("Messages")
    pm.menu.add("Settings")
    var handedOff = false
    pm.setOnMenuItemClickListener { item ->
        when (item.title) {
            "Redo" -> redo()
            "Messages" -> { handedOff = true; showMessagesPanel(resume) }
            "Settings" -> { handedOff = true; showSettingsPanel(resume) }
            "New city" -> {
                cityReady = false
                sim.post {
                    MicropolisNative.generateRandomCity(handle)
                    MicropolisNative.saveCity(handle, autosavePath)   // reset autosave to the new city
                    cityReady = true
                    ui.post {
                        resetHistory()                 // undo does not cross cities
                        promptCityName(isFirst = true, onDismiss = resume)
                    }
                }
                handedOff = true
            }
            "Save city" -> { handedOff = true; pickerResume = resume; savePicker.launch("${sanitize(cityName)}.cty") }
            "Load city" -> { handedOff = true; pickerResume = resume; loadPicker.launch(arrayOf("*/*")) }
        }
        true
    }
    pm.setOnDismissListener { if (!handedOff) resume() }
    pm.show()
}

internal fun MainActivity.showSimulationPanel() {
    showPanel("Simulation", listOf(
        PanelTab("Speed") {
            val glyphs = arrayOf("⏸", "▶", "▶▶", "⏩", "🚀")
            val grid = android.widget.GridLayout(this).apply { columnCount = 5 }
            val handles = ArrayList<CardHandle>()
            fun select(sel: Int) { handles.forEachIndexed { i, h -> h.setSelected(i == sel) } }
            for (i in speedNames.indices) {
                val h = panelCard(glyphs[i], speedNames[i]) {
                    speed = i; if (i > 0) lastRunSpeed = i
                    updatePlayPauseText(); updateSpeedChipText(); select(i)
                }
                handles.add(h); addCard(grid, h)
            }
            select(speed)
            grid
        },
        PanelTab("Disasters") {
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            fun label(t: String) = TextView(this).apply {
                text = t; setTextColor(0xFF9AA7B4.toInt()); textSize = 13f; setPadding(dp(4), dp(8), 0, dp(4))
            }
            col.addView(label("Random disasters"))
            val freqGrid = android.widget.GridLayout(this).apply { columnCount = 4 }
            val freqGlyphs = arrayOf("🚫", "🌤", "⚠", "🔥")
            val handles = ArrayList<CardHandle>()
            fun select(sel: Int) { handles.forEachIndexed { i, h -> h.setSelected(i == sel) } }
            for (i in disasterFreqNames.indices) {
                val h = panelCard(freqGlyphs[i], disasterFreqNames[i]) {
                    disasterFreq = i; prefs.edit().putInt("disasterFreq", i).apply()
                    select(i)
                }
                handles.add(h); addCard(freqGrid, h)
            }
            select(disasterFreq)
            col.addView(freqGrid)
            col.addView(label("Trigger now"))
            val names = arrayOf("Fire","Flood","Tornado","Earthquake","Monster","Meltdown")
            val glyphs = arrayOf("🔥","🌊","🌪","⛰","👾","☢")
            val grid = android.widget.GridLayout(this).apply { columnCount = 3 }
            names.forEachIndexed { kind, n ->
                addCard(grid, panelCard(glyphs[kind], n) { sim.post { MicropolisNative.makeDisaster(handle, kind) } })
            }
            col.addView(grid)
            col
        }
    ))
}

internal fun MainActivity.showOverlayPanel() {
    val glyphs = arrayOf("⊘","👥","🚗","☁","💲","🚨","📈","⚡")
    showPanel("Map overlay", listOf(PanelTab("Mode") {
        val grid = android.widget.GridLayout(this).apply { columnCount = 4 }
        val handles = ArrayList<CardHandle>()
        fun select(sel: Int) { handles.forEachIndexed { i, h -> h.setSelected(i == sel) } }
        overlayNames.forEachIndexed { kind, name ->
            val h = panelCard(glyphs.getOrElse(kind) { "•" }, name) {
                currentOverlay = kind; overlayState.text = name
                if (kind == 0) mapView.setOverlay(0, null)
                select(kind)
            }
            handles.add(h); addCard(grid, h)
        }
        select(currentOverlay)
        grid
    }))
}

internal fun MainActivity.applyMinimapMode() {
    if (minimapNav) {
        minimap.visibility = View.VISIBLE
        mapView.setNavLocked(true, navZoom)
    } else {
        minimap.visibility = View.GONE
        mapView.setNavLocked(false, navZoom)
    }
}

internal fun MainActivity.showSettingsPanel(onDismiss: (() -> Unit)? = null) {
    showPanel("Settings", listOf(
        PanelTab("General") {
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(settingsToggle("Minimap navigation",
                    "Show the minimap and move with it (fixed zoom). Off = pinch zoom.", minimapNav) { c ->
                    minimapNav = c; prefs.edit().putBoolean("minimapNav", c).apply(); applyMinimapMode()
                })
                addView(settingsToggle("Sound effects",
                    "City sounds and build feedback.", sfx.enabled) { c ->
                    sfx.enabled = c; prefs.edit().putBoolean("sound", c).apply()
                })
                addView(settingsToggle("Annual report",
                    "Pause at each new year and show the city's report card.", annualReportEnabled) { c ->
                    annualReportEnabled = c; prefs.edit().putBoolean("annualReport", c).apply()
                })
                addView(settingsToggle("Drop tool when idle",
                "After 15 s without touching the map, go back to Move.", idleToMove) { c ->
                idleToMove = c; prefs.edit().putBoolean("idleToMove", c).apply()
            })
            addView(settingsToggle("Auto go to events",
                    "Jump the map to fires, disasters and other alerts as they happen.", autoGoto) { c ->
                    autoGoto = c; prefs.edit().putBoolean("autoGoto", c).apply()
                })
            }
        },
        PanelTab("Background") { backgroundSettingsTab() }
    ), onDismiss)
}
