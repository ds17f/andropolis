package micropolis.port

import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

// New game screen: bottom sheet with three tabs (New map, Scenarios, Cities).

/** ⋁ → New game. `resume` restarts the sim when the sheet closes without starting a game. */
internal fun MainActivity.showNewGame(resume: () -> Unit) {
    val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)

    // Pre-build the pages
    val content = FrameLayout(this)
    val pages = ArrayList<View>()

    // Tab 0: New map
    val lastSelected = IntArray(4) { -1 }
    pages.add(LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val options = listOf(
            Triple("Trees", arrayOf("None", "Some", "Lots"), intArrayOf(0, -1, 250)),
            Triple("Lakes", arrayOf("None", "Some", "Lots"), intArrayOf(0, -1, 20)),
            Triple("River", arrayOf("None", "Yes"), intArrayOf(0, -1)),
            Triple("Island", arrayOf("Never", "Sometimes", "Always"), intArrayOf(0, -1, 1))
        )
        val treeGlyph = "🌲"
        val lakeGlyph = "💧"
        val riverGlyph = "〰"
        val islandGlyph = "🏝"

        options.forEachIndexed { idx, (label, choices, values) ->
            // Label
            addView(TextView(this@showNewGame).apply {
                text = label
                setTextColor(0xFF9AA7B4.toInt())
                textSize = 13f
                setPadding(0, dp(4), 0, dp(8))
            })

            // Grid of choices
            val grid = android.widget.GridLayout(this@showNewGame).apply {
                columnCount = if (choices.size == 2) 2 else 3
            }
            val handles = ArrayList<CardHandle>()

            fun select(selIdx: Int) {
                handles.forEachIndexed { i, h ->
                    h.setSelected(i == selIdx)
                }
                lastSelected[idx] = selIdx
            }

            choices.forEachIndexed { i, choice ->
                val glyph = when (idx) {
                    0 -> treeGlyph
                    1 -> lakeGlyph
                    2 -> riverGlyph
                    3 -> islandGlyph
                    else -> "•"
                }
                val h = panelCard(glyph, choice) {
                    select(i)
                }
                handles.add(h)
                addCard(grid, h)
            }
            select(if (choices.size == 2) 1 else 1)
            addView(grid)
            addView(View(this@showNewGame).apply {
                setBackgroundColor(0x1FFFFFFF)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
                setPadding(0, dp(12), 0, dp(12))
            })
        }

        // Generate button
        val genBtn = com.google.android.material.button.MaterialButton(this@showNewGame).apply {
            text = "Generate city"
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFF5A623.toInt())
            setTextColor(0xFF1A1207.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setCornerRadius(dp(12))
            setOnClickListener {
                sheet.dismiss()
                startNewMap(
                    lastSelected[0],
                    lastSelected[1],
                    lastSelected[2],
                    lastSelected[3],
                    resume
                )
            }
        }
        addView(genBtn)
    })

    // Tab 1: Scenarios
    pages.add(ScrollView(this).apply {
        val list = LinearLayout(this@showNewGame).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        SCENARIOS.forEach { sc ->
            val card = LinearLayout(this@showNewGame).apply {
                orientation = LinearLayout.VERTICAL
                background = roundedBg(0x14FFFFFF, 12)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                setPadding(0, 0, 0, dp(8))
                setOnClickListener {
                    sheet.dismiss()
                    startScenario(sc, resume)
                }
                val nameLine = TextView(this@showNewGame).apply {
                    text = "${sc.name} · ${sc.year}"
                    setTextColor(0xFFEEF2F6.toInt())
                    textSize = 15f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                addView(nameLine)
                val descLine = TextView(this@showNewGame).apply {
                    text = sc.challenge
                    setTextColor(0xFF9AA7B4.toInt())
                    textSize = 12f
                }
                addView(descLine)
            }
            list.addView(card)
        }
        addView(list)
        val maxH = (resources.displayMetrics.heightPixels * 0.6).toInt()
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, maxH)
    })

    // Tab 2: Cities
    pages.add(ScrollView(this).apply {
        val list = LinearLayout(this@showNewGame).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        sampleCities().forEach { name ->
            val displayName = name.replaceFirstChar { it.uppercase() }
            val card = LinearLayout(this@showNewGame).apply {
                orientation = LinearLayout.VERTICAL
                background = roundedBg(0x14FFFFFF, 12)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                setPadding(0, 0, 0, dp(8))
                setOnClickListener {
                    sheet.dismiss()
                    startSampleCity(name, resume)
                }
                val nameLine = TextView(this@showNewGame).apply {
                    text = displayName
                    setTextColor(0xFFEEF2F6.toInt())
                    textSize = 15f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                addView(nameLine)
            }
            list.addView(card)
        }
        addView(list)
        val maxH = (resources.displayMetrics.heightPixels * 0.6).toInt()
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, maxH)
    })

    // Build UI
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(16), dp(20), dp(24))
        background = roundedBg(0xFF12161C.toInt(), 20)
        setBackgroundColor(0xFF12161C.toInt())
    }

    // Title
    col.addView(TextView(this).apply {
        text = "New game"
        setTextColor(0xFFEEF2F6.toInt())
        textSize = 18f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(0, 0, 0, dp(12))
    })

    // Tab buttons
    val tabRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, 0, 0, dp(12))
    }
    val tabButtons = ArrayList<TextView>()
    val tabTitles = listOf("New map", "Scenarios", "Cities")

    tabTitles.forEachIndexed { index, title ->
        val btn = TextView(this@showNewGame).apply {
            text = title
            setTextColor(0xFF9AA7B4.toInt())
            textSize = 14f
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = android.view.Gravity.CENTER
            setTag(index)
            setOnClickListener { v ->
                val idx = v.tag as Int
                tabButtons.forEachIndexed { i, b ->
                    val selected = i == idx
                    b.background = roundedBg(if (selected) 0xFFF5A623.toInt() else 0x1FFFFFFF, 8)
                    b.setTextColor(if (selected) 0xFF1A1207.toInt() else 0xFF9AA7B4.toInt())
                }
                content.removeAllViews()
                content.addView(pages[idx])
            }
        }
        btn.background = roundedBg(if (index == 0) 0xFFF5A623.toInt() else 0x1FFFFFFF, 8)
        btn.setTextColor(if (index == 0) 0xFF1A1207.toInt() else 0xFF9AA7B4.toInt())
        tabButtons.add(btn)
        tabRow.addView(btn)
    }

    // Add tab row and content to col
    col.addView(tabRow)
    col.addView(content, LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, dp(400)))

    sheet.setContentView(col)

    var started = false
    sheet.setOnDismissListener {
        if (!started) resume()
    }

    // Select first page initially
    content.addView(pages[0])

    sheet.show()
}
