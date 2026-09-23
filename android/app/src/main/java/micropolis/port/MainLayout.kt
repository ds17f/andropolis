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

    // ===== Bottom: Material bar (Simulation / City / Overlay / Undo) + tool FAB over the map =====
    root.addView(buildBottomBar())
    mapContainer.addView(buildToolFab(), android.widget.FrameLayout.LayoutParams(
        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
            setMargins(0, 0, dp(16), dp(16))
        })

    setContentView(root)
}
