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

    // ===== Top app bar (Material toolbar + flat stats strip) =====
    topBar = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(0xFF12161C.toInt())
        elevation = dp(4).toFloat()
    }
    toolbar = com.google.android.material.appbar.MaterialToolbar(this).apply {
        title = "Micropolis"
        setSubtitleTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
        setTitleTextColor(0xFFEEF2F6.toInt()); setSubtitleTextColor(0xFF9AA7B4.toInt())
        menu.add(0, R.id.action_play_pause, 0, "Pause").setIcon(R.drawable.ic_pause)
            .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.add(0, R.id.action_more, 1, "More").setIcon(R.drawable.ic_more_vert)
            .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
        setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_play_pause -> {
                    if (speed == 0) speed = lastRunSpeed else { lastRunSpeed = speed; speed = 0 }
                    updatePlayPauseText(); updateSpeedChipText()
                }
                // our own popup (not the toolbar overflow) so opening it can pause the sim
                R.id.action_more -> showOverflowMenu(findViewById(R.id.action_more) ?: this)
            }
            true
        }
    }
    topBar.addView(toolbar)

    // Stats strip: plain labelled numbers, not buttons.
    val stats = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(16), 0, dp(16), dp(10))
    }
    fun stat(label: String, valueColor: Int): TextView {
        val v = TextView(this).apply {
            textSize = 16f; setTextColor(valueColor); setTypeface(null, android.graphics.Typeface.BOLD)
        }
        stats.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(this@buildLayout).apply {
                text = label.uppercase(); textSize = 10f; letterSpacing = 0.08f; setTextColor(0xFF7D8B99.toInt()) })
            addView(v)
        })
        return v
    }
    fundsValue = stat("Funds", 0xFFF5A623.toInt())
    popValue = stat("Population", 0xFFEEF2F6.toInt())
    scoreValue = stat("Score", 0xFFEEF2F6.toInt())
    topBar.addView(stats)
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

    pillInfo.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    toolPill.addView(pillInfo)

    // ✕ drops the current tool (back to Move); hidden while in Move.
    pillClose = TextView(this).apply {
        text = "✕"; textSize = 18f; setTextColor(0xFF9AA7B4.toInt())
        gravity = android.view.Gravity.CENTER
        setPadding(dp(12), dp(8), dp(4), dp(8))
        contentDescription = "Drop tool"
        setOnClickListener { selectTool(MOVE_TOOL) }
    }
    toolPill.gravity = android.view.Gravity.CENTER_VERTICAL
    toolPill.addView(pillClose)

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
