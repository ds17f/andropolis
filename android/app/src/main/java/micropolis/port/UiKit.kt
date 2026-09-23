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

// Shared view builders: rounded backgrounds, dp, styled dialogs, panel cards, bottom panels, pills, banner.
// Extension functions on MainActivity; state lives in MainActivity.kt.

internal fun MainActivity.roundedBg(color: Int, radiusDp: Int): GradientDrawable {
    val d = GradientDrawable()
    d.setColor(color)
    d.cornerRadius = radiusDp * resources.displayMetrics.density
    return d
}

internal fun MainActivity.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

/** A reusable styled dialog: dark rounded panel with bold title, row labels/values, amber button. */
internal fun MainActivity.styledDialog(
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
            addView(TextView(this@styledDialog).apply {
                text = k; setTextColor(0xFF9AA7B4.toInt()); textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(this@styledDialog).apply {
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

/** A tool-style card: glyph in a tinted square + label; call setSelected to highlight. */
internal fun MainActivity.panelCard(glyph: String, label: String, onClick: () -> Unit): CardHandle {
    val glyphTv = TextView(this).apply { text = glyph; textSize = 20f }
    val iconBox = LinearLayout(this).apply {
        gravity = android.view.Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
        addView(glyphTv)
    }
    val labelTv = TextView(this).apply {
        text = label; textSize = 11f; gravity = android.view.Gravity.CENTER
        setTextColor(0xFFCDD6E0.toInt()); setPadding(0, dp(6), 0, 0)
    }
    val card = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER_HORIZONTAL
        setPadding(dp(8), dp(10), dp(8), dp(10))
        addView(iconBox); addView(labelTv)
        setOnClickListener { onClick() }
    }
    val setSelected = { on: Boolean ->
        card.background = roundedBg(if (on) 0x33F5A623 else 0x0DFFFFFF, 14)
        iconBox.background = roundedBg(if (on) 0x55F5A623 else 0x22F5A623, 12)
        glyphTv.setTextColor(if (on) 0xFFF5A623.toInt() else 0xFFC3CCD6.toInt())
    }
    setSelected(false)
    return CardHandle(card, setSelected)
}

/** Put a card into a GridLayout cell (equal columns). */
internal fun MainActivity.addCard(grid: android.widget.GridLayout, h: CardHandle) {
    val lp = android.widget.GridLayout.LayoutParams()
    lp.width = 0
    lp.columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
    lp.setMargins(dp(4), dp(4), dp(4), dp(4))
    h.view.layoutParams = lp
    grid.addView(h.view)
}

internal fun MainActivity.showPanel(title: String, tabs: List<PanelTab>, onDismiss: (() -> Unit)? = null) {
    val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(16), dp(20), dp(24))
        setBackgroundColor(0xFF12161C.toInt())
    }
    col.addView(TextView(this).apply {
        text = title
        setTextColor(0xFFEEF2F6.toInt())
        textSize = 18f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(0, 0, 0, dp(12))
    })
    val content = android.widget.FrameLayout(this)
    if (tabs.size > 1) {
        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(12))
        }
        val chips = ArrayList<TextView>()
        // Build every tab once (keeps slider/toggle state across tab switches), then
        // size the content area to the TALLEST tab so the sheet never jumps.
        val pages = tabs.map { t -> ScrollView(this).apply { addView(t.build()) } }
        val innerW = resources.displayMetrics.widthPixels - dp(40)
        val tallest = pages.maxOf { p ->
            p.getChildAt(0).measure(
                View.MeasureSpec.makeMeasureSpec(innerW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            p.getChildAt(0).measuredHeight
        }
        val contentH = minOf(tallest, (resources.displayMetrics.heightPixels * 0.72f).toInt())
        fun select(idx: Int) {
            content.removeAllViews()
            content.addView(pages[idx])
            chips.forEachIndexed { i, c ->
                val on = i == idx
                c.background = roundedBg(if (on) 0xFFF5A623.toInt() else 0x1FFFFFFF, 10)
                c.setTextColor(if (on) 0xFF1A1207.toInt() else 0xFF9AA7B4.toInt())
            }
        }
        tabs.forEachIndexed { i, t ->
            // Equal-width chips, icon stacked over the title; the text auto-sizes to fit
            // so titles never wrap or truncate at large system font scales.
            val chip = androidx.appcompat.widget.AppCompatTextView(this).apply {
                text = if (t.glyph.isEmpty()) t.title else "${t.glyph}\n${t.title}"
                gravity = android.view.Gravity.CENTER
                maxLines = if (t.glyph.isEmpty()) 1 else 2
                setPadding(dp(4), dp(6), dp(4), dp(6))
                layoutParams = LinearLayout.LayoutParams(0, dp(if (t.glyph.isEmpty()) 40 else 58), 1f)
                    .apply { setMargins(dp(3), 0, dp(3), 0) }
                setOnClickListener { select(i) }
            }
            androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                chip, 9, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            chips.add(chip)
            tabRow.addView(chip)
        }
        // After layout, give every chip the smallest auto-sized text so they match.
        tabRow.post {
            val minPx = chips.minOf { it.textSize }
            chips.forEach {
                androidx.core.widget.TextViewCompat.setAutoSizeTextTypeWithDefaults(
                    it, androidx.core.widget.TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE)
                it.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, minPx)
            }
        }
        col.addView(tabRow)
        col.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, contentH))
        select(0)
    } else {
        val sv = ScrollView(this)
        sv.addView(tabs[0].build())
        col.addView(sv)
    }
    sheet.setContentView(col)
    // Open fully (not the half-height peek) so the fixed-height content is all visible.
    sheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
    sheet.behavior.skipCollapsed = true
    sheet.setOnDismissListener { onDismiss?.invoke() }
    sheet.show()
}

internal fun MainActivity.showBanner(text: String) {
    messageBanner.text = text
    messageBanner.visibility = View.VISIBLE
    ui.removeCallbacks(bannerHide)
    ui.postDelayed(bannerHide, 6000)
}

internal fun MainActivity.buildPill(glyph: String, label: String, state: TextView, onClick: () -> Unit): LinearLayout {
    // Vertical, centered: glyph on top, then the label gets the pill's full width
    // (so long labels like "Simulation" fit on one line at any font scale), then the state.
    val pill = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = android.view.Gravity.CENTER
        background = roundedBg(0xFF1A222A.toInt(), 18)
        setPadding(dp(6), dp(8), dp(6), dp(8))
        layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT).apply { weight = 1f; setMargins(dp(4), 0, dp(4), 0) }
        setOnClickListener { onClick() }
    }
    pill.addView(TextView(this).apply {
        text = glyph
        textSize = 18f
        setTextColor(0xFFF5A623.toInt())
        gravity = android.view.Gravity.CENTER
    })
    pill.addView(TextView(this).apply {
        text = label
        setTextColor(0xFFEEF2F6.toInt())
        textSize = 12f
        maxLines = 1
        gravity = android.view.Gravity.CENTER
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(0, dp(2), 0, 0)
    })
    state.gravity = android.view.Gravity.CENTER
    state.maxLines = 1
    pill.addView(state)
    return pill
}

internal fun MainActivity.buildToolCard(ti: ToolItem, sheet: com.google.android.material.bottomsheet.BottomSheetDialog): View {
    val selected = ti.value == currentTool
    val card = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = android.view.Gravity.CENTER_HORIZONTAL
        setPadding(8, 10, 8, 10)
        setBackgroundColor(if (selected) 0x33F5A623 else 0x0DFFFFFF)
    }
    card.addView(android.widget.ImageView(this).apply {
        setImageResource(ti.icon)
        imageTintList = android.content.res.ColorStateList.valueOf(if (selected) 0xFFF5A623.toInt() else 0xFFC3CCD6.toInt())
        layoutParams = LinearLayout.LayoutParams(84, 84)
    })
    card.addView(android.widget.TextView(this).apply {
        text = ti.label
        setTextColor(0xFFCDD6E0.toInt())
        textSize = 11f
        gravity = android.view.Gravity.CENTER
        setPadding(0, 6, 0, 0)
    })
    val cost = costOf(ti.value)
    card.addView(android.widget.TextView(this).apply {
        text = if (ti.value == MOVE_TOOL) "Pan map" else if (cost == 0) "Free" else "$$cost"
        setTextColor(0xFFF5A623.toInt())
        textSize = 10f
        gravity = android.view.Gravity.CENTER
        setPadding(0, 2, 0, 0)
    })
    // even 4-column sizing
    val lp = android.widget.GridLayout.LayoutParams()
    lp.width = 0
    lp.columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
    lp.setMargins(6, 6, 6, 6)
    card.layoutParams = lp
    // Tapping the tool that is already selected turns it off (back to Move).
    card.setOnClickListener { selectTool(if (selected) MOVE_TOOL else ti.value); sheet.dismiss() }
    return card
}

/** One Settings row: bold title, muted description, and a switch on the right. */
internal fun MainActivity.settingsToggle(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit): View =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(0, dp(10), 0, dp(10))
        addView(LinearLayout(this@settingsToggle).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(this@settingsToggle).apply {
                text = title; setTextColor(0xFFEEF2F6.toInt()); textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@settingsToggle).apply {
                text = desc; setTextColor(0xFF9AA7B4.toInt()); textSize = 12f
            })
        })
        addView(android.widget.Switch(this@settingsToggle).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, c -> onChange(c) }
        })
    }
