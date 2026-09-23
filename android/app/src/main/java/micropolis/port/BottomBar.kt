package micropolis.port

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton

/** The bottom bar: Simulation, City, Overlay, Undo. Must set simState, overlayState and undoBtn. */
internal fun MainActivity.buildBottomBar(): View {
    val bar = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        background = GradientDrawable().apply {
            setColor(0xFF12161C.toInt())
        }
        elevation = dp(8).toFloat()
        setPadding(0, dp(4), 0, dp(8))
    }

    // Helper to create a bottom bar item
    fun createBarItem(iconRes: Int, label: String, onClick: () -> Unit): Pair<View, TextView?> {
        val item = LinearLayout(this@buildBottomBar).apply {
            orientation = LinearLayout.VERTICAL
            setGravity(Gravity.CENTER)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            isClickable = true

            // Ripple background
            val tv = android.util.TypedValue().also {
                theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
            }
            val rippleDrawable = ContextCompat.getDrawable(this@buildBottomBar, tv.resourceId)
            background = rippleDrawable
        }

        val icon = ImageView(this@buildBottomBar).apply {
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
            setImageResource(iconRes)
            imageTintList = android.content.res.ColorStateList.valueOf(0xFFEEF2F6.toInt())
        }
        item.addView(icon)

        val labelView = TextView(this@buildBottomBar).apply {
            text = label
            textSize = 12f
            setTextColor(0xFF9AA7B4.toInt())
            isSingleLine = true
            setGravity(Gravity.CENTER)
            setPadding(0, dp(2), 0, 0)
        }
        item.addView(labelView)

        item.setOnClickListener { onClick() }

        return Pair(item, if (label == label) labelView else null)
    }

    // Create the four items
    val speedLabel = TextView(this).apply {
        text = speedNames[speed]
        textSize = 12f
        setTextColor(0xFF9AA7B4.toInt())
        isSingleLine = true
        setGravity(Gravity.CENTER)
        setPadding(0, dp(2), 0, 0)
    }
    simState = speedLabel

    val cityLabel = "City"
    val overlayLabel = "Overlay"
    val undoLabel = "Undo"

    val (simItem, _) = createBarItem(R.drawable.ic_fast_forward, speedNames[speed]) {
        showSimulationPanel()
    }
    bar.addView(simItem, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

    val (cityItem, _) = createBarItem(R.drawable.ic_bar_chart, cityLabel) {
        showCityPanel()
    }
    bar.addView(cityItem, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

    val overlayStateView = TextView(this).apply {
        text = overlayLabel
        textSize = 12f
        setTextColor(0xFF9AA7B4.toInt())
        isSingleLine = true
        setGravity(Gravity.CENTER)
        setPadding(0, dp(2), 0, 0)
    }
    overlayState = overlayStateView

    val (overlayItem, _) = createBarItem(R.drawable.ic_layers, overlayLabel) {
        showOverlayPanel()
    }
    bar.addView(overlayItem, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

    val (undoItem, _) = createBarItem(R.drawable.ic_undo, undoLabel) {
        undo()
    }
    undoBtn = undoItem
    bar.addView(undoItem, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

    return bar
}

/** The tool FAB (tap = palette) with a small ✕ above it. Must set toolFab and dropToolFab. */
internal fun MainActivity.buildToolFab(): View {
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setGravity(Gravity.END)
    }

    // dropToolFab - small ✕ above the tool FAB
    dropToolFab = FloatingActionButton(this).apply {
        size = FloatingActionButton.SIZE_MINI
        setImageResource(R.drawable.ic_close)
        backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF2A333D.toInt())
        imageTintList = android.content.res.ColorStateList.valueOf(0xFFEEF2F6.toInt())
        contentDescription = "Drop tool"
        setOnClickListener { selectTool(MOVE_TOOL) }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.END
            rightMargin = dp(8)
            bottomMargin = dp(12)
        }
    }

    // toolFab - Extended FAB for current tool
    toolFab = com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton(this).apply {
        backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFF5A623.toInt())
        setTextColor(0xFF1A1207.toInt())
        iconTint = android.content.res.ColorStateList.valueOf(0xFF1A1207.toInt())
        contentDescription = "Current tool"
        setOnClickListener { openPalette() }
    }

    col.addView(dropToolFab)
    col.addView(toolFab)

    return col
}
