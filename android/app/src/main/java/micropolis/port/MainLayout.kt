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

// Builds the main screen views (called once from onCreate).
// Extension functions on MainActivity; state lives in MainActivity.kt.

/** Build the whole screen: top bar, HUD chips, map, banner, bottom controls, panel pills. */
internal fun MainActivity.buildLayout() {
    mapView = MapView(this)

    val root = LinearLayout(this)
    root.orientation = LinearLayout.VERTICAL
    root.fitsSystemWindows = true

    // ===== Top app bar =====
    topBar = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(0xFF12161C.toInt())
        setPadding(16, 40, 16, 12)
        elevation = dp(6).toFloat()
    }

            // Row 1: city title, play/pause, overflow
    val row1 = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, 0, 0, 8)
    }

    // Left vertical block (city title and subtitle)
    val cityBlock = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
    }

    cityTitle = android.widget.TextView(this).apply {
        text = "Micropolis"
        setTextSize(19f)
        setTypeface(null, android.graphics.Typeface.BOLD)
        setTextColor(0xFFEEF2F6.toInt())
        setPadding(0, 0, 16, 0)
    }
    cityBlock.addView(cityTitle)

    subtitle = android.widget.TextView(this).apply {
        setTextSize(13f)
        setTextColor(0xFF9AA7B4.toInt())
    }
    cityBlock.addView(subtitle)
    row1.addView(cityBlock)

    // Play/Pause button
    playPauseBtn = Button(this).apply {
        setText("⏸")
        background = roundedBg(0xFFF5A623.toInt(), 12)
        setTextColor(0xFF1A1207.toInt())
        setOnClickListener {
            if (speed == 0) {
                speed = lastRunSpeed
            } else {
                lastRunSpeed = speed
                speed = 0
            }
            updatePlayPauseText()
            updateSpeedChipText()
        }
        setPadding(dp(14), dp(8), dp(14), dp(8))
        stateListAnimator = null
        setTextSize(14f)
    }
    row1.addView(playPauseBtn, LayoutParams(dp(48), LayoutParams.WRAP_CONTENT).apply { setMargins(8, 0, 0, 0) })

    // Overflow button
    overflowBtn = Button(this).apply {
        setText("⋮")
        background = roundedBg(0x1FFFFFFF.toInt(), 12)
        setTextColor(0xFFEEF2F6.toInt())
        setOnClickListener {
            val resume = pauseForUi()
            val pm = PopupMenu(this@buildLayout, it as Button)
            pm.menu.add("Redo").isEnabled = redoStack.isNotEmpty()
            pm.menu.add("New city")
            pm.menu.add("Save city")
            pm.menu.add("Load city")
            pm.menu.add(if (annualReportEnabled) "Annual report: On" else "Annual report: Off")
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
                    else -> if (item.title.toString().startsWith("Annual report")) {
                        annualReportEnabled = !annualReportEnabled
                        prefs.edit().putBoolean("annualReport", annualReportEnabled).apply()
                    }
                }
                true
            }
            pm.setOnDismissListener { if (!handedOff) resume() }
            pm.show()
        }
        setPadding(dp(14), dp(8), dp(14), dp(8))
        stateListAnimator = null
        setTextSize(14f)
    }
    row1.addView(overflowBtn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { setMargins(8, 0, 0, 0) })
    topBar.addView(row1)

    // Row 2: HUD chips (Funds, Population, Score)
    val row2 = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
    }

    // Funds chip
    fundsChip = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedBg(0x14FFFFFF.toInt(), 12)
        layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ).apply { setMargins(8, 0, 0, 0) }
        setPadding(dp(12), dp(8), dp(12), dp(8))
    }
    val fundsLabel = android.widget.TextView(this).apply {
        setText("Funds")
        setTextSize(10f)
        setTextColor(0xFF9AA7B4.toInt())
    }
    fundsChip.addView(fundsLabel)
    fundsValue = android.widget.TextView(this).apply {
        setText("$0")
        setTextSize(15f)
        setTypeface(null, android.graphics.Typeface.BOLD)
        setTextColor(0xFFF5A623.toInt())
    }
    fundsChip.addView(fundsValue)
    row2.addView(fundsChip)

    // Population chip
    popChip = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedBg(0x14FFFFFF.toInt(), 12)
        layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ).apply { setMargins(8, 0, 0, 0) }
        setPadding(dp(12), dp(8), dp(12), dp(8))
    }
    val popLabel = android.widget.TextView(this).apply {
        setText("Population")
        setTextSize(10f)
        setTextColor(0xFF9AA7B4.toInt())
    }
    popChip.addView(popLabel)
    popValue = android.widget.TextView(this).apply {
        setText("0")
        setTextSize(15f)
        setTypeface(null, android.graphics.Typeface.BOLD)
        setTextColor(0xFFEEF2F6.toInt())
    }
    popChip.addView(popValue)
    row2.addView(popChip)

    // Score chip
    scoreChip = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedBg(0x14FFFFFF.toInt(), 12)
        layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ).apply { setMargins(8, 0, 0, 0) }
        setPadding(dp(12), dp(8), dp(12), dp(8))
    }
    val scoreLabel = android.widget.TextView(this).apply {
        setText("Score")
        setTextSize(10f)
        setTextColor(0xFF9AA7B4.toInt())
    }
    scoreChip.addView(scoreLabel)
    scoreValue = android.widget.TextView(this).apply {
        setText("0")
        setTextSize(15f)
        setTypeface(null, android.graphics.Typeface.BOLD)
        setTextColor(0xFFEEF2F6.toInt())
    }
    scoreChip.addView(scoreValue)
    row2.addView(scoreChip)

    topBar.addView(row2)
    root.addView(topBar, 0, LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT))

    minimap = MinimapView(this)
    mapContainer = android.widget.FrameLayout(this)
    mapContainer.addView(mapView, android.widget.FrameLayout.LayoutParams(
        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
        android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
    mapContainer.addView(minimap, android.widget.FrameLayout.LayoutParams(dp(120), dp(100)).apply {
        gravity = android.view.Gravity.TOP or android.view.Gravity.END
        setMargins(0, dp(8), dp(8), 0)
    })
    messageBanner = TextView(this).apply {
        visibility = View.GONE
        setTextColor(0xFFEEF2F6.toInt()); textSize = 13f
        background = roundedBg(0xE6202A36.toInt(), 12)
        setPadding(dp(14), dp(10), dp(14), dp(10))
        setOnClickListener {
            if (viewBeforeJump != null) {
                mapView.restoreView(viewBeforeJump!!)
                viewBeforeJump = null
                messageBanner.visibility = View.GONE
            } else {
                lastEventTile?.let { mapView.centerOnTile(it.first, it.second) }
            }
        }
    }
    mapContainer.addView(messageBanner, android.widget.FrameLayout.LayoutParams(
        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            setMargins(dp(8), dp(8), dp(8), 0)
        })
    root.addView(mapContainer, LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

    // ===== Bottom controls =====
    bottom = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundColor(0xFF12161C.toInt())
        setPadding(12, 10, 12, 14)
        elevation = dp(6).toFloat()
    }

    // Tool pill (opens palette)
    toolPill = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        background = roundedBg(0xFF1A222A.toInt(), 18)
        setPadding(dp(14), dp(8), dp(14), dp(8))
        setOnClickListener { openPalette() }
        layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT).apply { weight = 1f }
    }

    // Pill icon in rounded amber container
    val pillIconContainer = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        background = roundedBg(0x33F5A623.toInt(), 12)
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
    }
    pillIcon = ImageView(this).apply {
        imageTintList = android.content.res.ColorStateList.valueOf(0xFFF5A623.toInt())
        layoutParams = LayoutParams(dp(44), dp(44)).apply { gravity = android.view.Gravity.CENTER }
        scaleType = ImageView.ScaleType.CENTER
    }
    pillIconContainer.addView(pillIcon)
    toolPill.addView(pillIconContainer)

    val pillInfo = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        setPadding(dp(12), 0, dp(12), 0)
    }

    val pillLabel = TextView(this).apply {
        text = "Current tool"
        setTextColor(0xFF9AA7B4.toInt())
        setTextSize(11f)
    }
    pillInfo.addView(pillLabel)

    pillName = TextView(this).apply {
        text = ""
        setTextColor(0xFFEEF2F6.toInt())
        setTextSize(16f)
        setTypeface(null, android.graphics.Typeface.BOLD)
    }
    pillInfo.addView(pillName)

    toolPill.addView(pillInfo)

    bottom.addView(toolPill)

    // Undo / Redo buttons
    undoBtn = Button(this).apply {
        text = "↶"
        background = roundedBg(0xFF1A222A.toInt(), 18)
        setTextColor(0xFFEEF2F6.toInt())
        stateListAnimator = null
        setTextSize(18f)
        setPadding(dp(14), dp(8), dp(14), dp(8))
        setOnClickListener { undo() }
    }
    bottom.addView(undoBtn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { setMargins(dp(8), 0, 0, 0) })

    // Panel pill row
    simState = TextView(this).apply {
        text = speedNames[speed]
        setTextColor(0xFF9AA7B4.toInt())
        textSize = 11f
    }
    val cityState = TextView(this).apply {
        text = "Budget · stats"
        setTextColor(0xFF9AA7B4.toInt())
        textSize = 11f
    }
    overlayState = TextView(this).apply {
        text = "Off"
        setTextColor(0xFF9AA7B4.toInt())
        textSize = 11f
    }
    panelBar = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundColor(0xFF12161C.toInt())
        setPadding(dp(8), dp(6), dp(8), dp(6))
    }
    panelBar.addView(buildPill("⏩", "Simulation", simState) { showSimulationPanel() })
    panelBar.addView(buildPill("📊", "City", cityState) { showCityPanel() })
    panelBar.addView(buildPill("🗺", "Overlay", overlayState) { showOverlayPanel() })
    root.addView(panelBar, root.indexOfChild(bottom))

    root.addView(bottom)

    setContentView(root)
}
