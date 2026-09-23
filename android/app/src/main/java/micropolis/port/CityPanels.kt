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

// City panel (Overview/Budget/Graphs), the citizens' poll, and the evaluation/budget/annual-report dialogs.
// Extension functions on MainActivity; state lives in MainActivity.kt.

internal fun MainActivity.avgOverlay(kind: Int): Int {
    val a = ByteArray(120 * 100)
    val n = MicropolisNative.copyOverlay(handle, kind, a)
    if (n <= 0) return 0
    var sum = 0L
    for (b in a) sum += (b.toInt() and 0xFF)
    return (sum / a.size).toInt()
}

internal fun MainActivity.showCityPanel() {
    sim.post {
        val b = IntArray(12); MicropolisNative.getBudget(handle, b)
        val ev = IntArray(7); MicropolisNative.getEvaluation(handle, ev)
        val crime = avgOverlay(5); val poll = avgOverlay(3); val land = avgOverlay(4)
        val traffic = avgOverlay(2); val density = avgOverlay(1)
        val p = IntArray(8); val np = MicropolisNative.getProblems(handle, p)
        ui.post { showCityPanelUI(b, ev, crime, poll, land, traffic, density, p, np) }
    }
}

internal fun MainActivity.levelWord(v: Int) = when {
    v < 26 -> "None"; v < 77 -> "Low"; v < 128 -> "Medium"; v < 191 -> "High"; else -> "Very high"
}

/** A labeled stat as a proportional coloured bar. v is 0..255. */
internal fun MainActivity.statBar(label: String, v: Int): View {
    val pct = (v * 100 / 255).coerceIn(0, 100)
    val color = when { v < 77 -> 0xFF4CAF50.toInt(); v < 160 -> 0xFFF5A623.toInt(); else -> 0xFFE5533D.toInt() }
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(8))
        addView(LinearLayout(this@statBar).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(this@statBar).apply {
                text = label; setTextColor(0xFF9AA7B4.toInt()); textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(this@statBar).apply {
                text = levelWord(v); setTextColor(0xFFEEF2F6.toInt()); textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
        })
        addView(LinearLayout(this@statBar).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedBg(0x1FFFFFFF, 6)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8)).apply { topMargin = dp(4) }
            addView(View(this@statBar).apply { background = roundedBg(color, 6)
                layoutParams = LinearLayout.LayoutParams(0, dp(8), pct.toFloat()) })
            addView(View(this@statBar).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(8), (100 - pct).toFloat()) })
        })
    }
}

/** A horizontal bar split by weights: pairs of (color, weight). */
internal fun MainActivity.splitBar(vararg parts: Pair<Int, Int>, height: Int = 10): View =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        background = roundedBg(0x1FFFFFFF, height / 2)
        clipToOutline = true
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(height)).apply { topMargin = dp(4) }
        for ((color, w) in parts) addView(View(this@splitBar).apply {
            setBackgroundColor(color); layoutParams = LinearLayout.LayoutParams(0, dp(height), w.coerceAtLeast(0).toFloat()) })
    }

/** City → Overview: the citizens' poll (approval split and ranked worst problems). */
internal fun MainActivity.citizenPoll(ev: IntArray, p: IntArray, np: Int): View = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL; setPadding(0, dp(12), 0, 0)
    fun muted(t: String, size: Float) = TextView(this@citizenPoll).apply { text = t; setTextColor(0xFF7D8B99.toInt()); textSize = size }
    addView(muted("What citizens say", 11f))
    addView(TextView(this@citizenPoll).apply {
        text = "Is the mayor doing a good job?"; setTextColor(0xFFEEF2F6.toInt()); textSize = 14f; setPadding(0, dp(6), 0, 0) })
    val yes = ev[6].coerceIn(0, 100)
    addView(splitBar(0xFF4CAF50.toInt() to yes, 0xFFE5533D.toInt() to 100 - yes))
    addView(LinearLayout(this@citizenPoll).apply {
        orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(2), 0, dp(10))
        addView(TextView(this@citizenPoll).apply { text = "Yes $yes%"; setTextColor(0xFF4CAF50.toInt()); textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        addView(TextView(this@citizenPoll).apply { text = "No ${100 - yes}%"; setTextColor(0xFFE5533D.toInt()); textSize = 12f })
    })
    addView(TextView(this@citizenPoll).apply {
        text = "Worst problems"; setTextColor(0xFFEEF2F6.toInt()); textSize = 14f })
    if (np == 0) addView(muted("No survey yet — check back after the next evaluation.", 13f).apply { setPadding(0, dp(6), 0, 0) })
    for (i in 0 until np) {
        val id = p[i]; val v = p[4 + i].coerceIn(0, 100)
        addView(LinearLayout(this@citizenPoll).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0)
            addView(TextView(this@citizenPoll).apply {
                text = "${problemIcons.getOrElse(id) { "•" }}  ${problemNames.getOrElse(id) { "?" }}"
                setTextColor(0xFF9AA7B4.toInt()); textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
            addView(TextView(this@citizenPoll).apply { text = "$v%"; setTextColor(0xFFEEF2F6.toInt()); textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD) })
        })
        addView(splitBar(0xFFF5A623.toInt() to v, 0 to 100 - v, height = 4))
    }
}

internal fun MainActivity.showCityPanelUI(b: IntArray, ev: IntArray, crime: Int, poll: Int, land: Int, traffic: Int, density: Int, p: IntArray, np: Int) {
    fun row(k: String, v: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(7), 0, dp(7))
        addView(TextView(this@showCityPanelUI).apply { text = k; setTextColor(0xFF9AA7B4.toInt()); textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        addView(TextView(this@showCityPanelUI).apply { text = v; setTextColor(0xFFEEF2F6.toInt()); textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD) })
    }
    showPanel("City", listOf(
        PanelTab("Overview", "🏛") {
            // Evaluation + city-wide stats in one tab.
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(row("Class", cityClassNames.getOrElse(ev[2]) { "?" }))
                addView(row("Population", "${ev[3]}  (Δ ${ev[4]})"))
                addView(row("Score", "${ev[0]}  (Δ ${ev[1]})"))
                addView(row("Approval", "${ev[6]}%"))
                addView(row("Assessed value", "$${ev[5]}"))
                addView(View(this@showCityPanelUI).apply {
                    setBackgroundColor(0x1FFFFFFF)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
                        .apply { topMargin = dp(8); bottomMargin = dp(4) }
                })
                addView(statBar("Crime", crime)); addView(statBar("Pollution", poll))
                addView(statBar("Land value", land)); addView(statBar("Traffic", traffic))
                addView(statBar("Population density", density))
                addView(citizenPoll(ev, p, np))
            }
        },
        PanelTab("Budget", "💰") {
            var road = b[5]; var fire = b[11]; var police = b[8]; var tax = b[1]; val curTax = b[1]
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val proj = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; background = roundedBg(0xFF12161C.toInt(), 12)
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }
            val income = TextView(this).apply { setTextColor(0xFFEEF2F6.toInt()) }
            val rSpend = TextView(this).apply { setTextColor(0xFF9AA7B4.toInt()) }
            val fSpend = TextView(this).apply { setTextColor(0xFF9AA7B4.toInt()) }
            val pSpend = TextView(this).apply { setTextColor(0xFF9AA7B4.toInt()) }
            val net = TextView(this).apply { setTypeface(null, android.graphics.Typeface.BOLD) }
            proj.addView(TextView(this).apply { text = "Projection"; setTextColor(0xFF7D8B99.toInt()); textSize = 11f })
            proj.addView(income); proj.addView(rSpend); proj.addView(fSpend); proj.addView(pSpend); proj.addView(net)
            fun update() {
                val inc = if (curTax > 0) (b[2].toLong() * tax / curTax).toInt() else b[2]
                val rs = b[3] * road / 100; val fs = b[9] * fire / 100; val ps = b[6] * police / 100
                income.text = "Tax income: $$inc"; rSpend.text = "Roads: $$rs"
                fSpend.text = "Fire: $$fs"; pSpend.text = "Police: $$ps"
                val n = inc - rs - fs - ps
                net.text = "Net: ${if (n >= 0) "+" else ""}$$n"
                net.setTextColor(if (n >= 0) 0xFF4CAF50.toInt() else 0xFFE5533D.toInt())
            }
            fun slider(title: String, value: Int, max: Int, onLive: (Int) -> Unit, onApply: (Int) -> Unit): View {
                val head = TextView(this).apply { text = "$title: $value%"; setTextColor(0xFFEEF2F6.toInt()); setPadding(0, dp(10), 0, dp(2)) }
                return LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; addView(head)
                    addView(android.widget.SeekBar(this@showCityPanelUI).apply {
                        this.max = max; progress = value
                        setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(s: android.widget.SeekBar, p: Int, u: Boolean) { head.text = "$title: $p%"; onLive(p); update() }
                            override fun onStartTrackingTouch(s: android.widget.SeekBar) {}
                            override fun onStopTrackingTouch(s: android.widget.SeekBar) { onApply(s.progress) }
                        })
                    })
                }
            }
            col.addView(row("Funds", "$${b[0]}"))
            col.addView(slider("Tax rate", tax, 20, { tax = it }, { sim.post { MicropolisNative.setCityTax(handle, it) } }))
            col.addView(slider("Road funding", road, 100, { road = it }, { sim.post { MicropolisNative.setFunding(handle, road, fire, police) } }))
            col.addView(slider("Fire funding", fire, 100, { fire = it }, { sim.post { MicropolisNative.setFunding(handle, road, fire, police) } }))
            col.addView(slider("Police funding", police, 100, { police = it }, { sim.post { MicropolisNative.setFunding(handle, road, fire, police) } }))
            col.addView(proj, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) })
            update()
            col
        },
        PanelTab("Graphs", "📈") {
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val scaleState = intArrayOf(0)                    // 0 = 10yr, 1 = 120yr
            val graph = GraphView(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(180))
            }
            val data = Array(6) { IntArray(120) }
            fun redraw() {
                graph.setSeries((0..5).filter { histVisible[it] }.map { histColors[it] to data[it] })
            }
            fun load() {
                sim.post {
                    val fresh = Array(6) { t -> IntArray(120).also { MicropolisNative.getHistory(handle, t, scaleState[0], it) } }
                    ui.post { for (t in 0..5) data[t] = fresh[t]; redraw() }
                }
            }
            // scale toggle
            val toggle = Button(this).apply {
                text = "10-year"; background = roundedBg(0x1FFFFFFF, 10); setTextColor(0xFFEEF2F6.toInt())
                stateListAnimator = null
                setOnClickListener { scaleState[0] = 1 - scaleState[0]; text = if (scaleState[0] == 0) "10-year" else "120-year"; load() }
            }
            col.addView(toggle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
            col.addView(graph)
            // legend
            // Compact 3-column legend.
            val legend = android.widget.GridLayout(this).apply { columnCount = 3; setPadding(0, dp(10), 0, 0) }
            for (i in histNames.indices) {
                legend.addView(LinearLayout(this).apply {
                    layoutParams = android.widget.GridLayout.LayoutParams().apply {
                        width = 0
                        columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                    }
                    orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, dp(6))
                    addView(View(this@showCityPanelUI).apply { background = roundedBg(histColors[i], 3)
                        layoutParams = LinearLayout.LayoutParams(dp(14), dp(14)).apply { rightMargin = dp(8) } })
                    addView(TextView(this@showCityPanelUI).apply { text = histNames[i]; setTextColor(0xFF9AA7B4.toInt()); textSize = 13f })
                    // Tap to toggle this line; hidden series are dimmed.
                    alpha = if (histVisible[i]) 1f else 0.35f
                    setOnClickListener { histVisible[i] = !histVisible[i]; alpha = if (histVisible[i]) 1f else 0.35f; redraw() }
                })
            }
            col.addView(legend)
            load()
            col
        }
    ))
}

internal fun MainActivity.showBudgetDialog(b: IntArray) {
    styledDialog("City Budget", listOf(
        "Funds" to "$${b[0]}", "Tax rate" to "${b[1]}%", "Tax income" to "$${b[2]}",
        "Roads" to "$${b[4]} / $${b[3]} (${b[5]}%)",
        "Police" to "$${b[7]} / $${b[6]} (${b[8]}%)",
        "Fire" to "$${b[10]} / $${b[9]} (${b[11]}%)"))
}

internal fun MainActivity.showEvalDialog(ev: IntArray) {
    styledDialog("City Evaluation", listOf(
        "Score" to "${ev[0]} (Δ ${ev[1]})", "Class" to cityClassNames.getOrElse(ev[2]) { "?" },
        "Population" to "${ev[3]} (Δ ${ev[4]})", "Assessed value" to "$${ev[5]}",
        "Approval" to "${ev[6]}%"))
}

internal fun MainActivity.showReportCard(year: Int, resume: Boolean) {
    sim.post {
        val ev = IntArray(7); MicropolisNative.getEvaluation(handle, ev)
        val b = IntArray(12); MicropolisNative.getBudget(handle, b)
        val p = IntArray(8); val np = MicropolisNative.getProblems(handle, p)
        ui.post {
            val concerns = (0 until minOf(np, 2)).joinToString("\n") {
                "${problemIcons.getOrElse(p[it]) { "•" }} ${problemNames.getOrElse(p[it]) { "?" }} ${p[4 + it]}%" }
            fun signed(n: Int) = if (n >= 0) "+$n" else "$n"
            val fundsDelta = if (lastReportFunds >= 0) b[0] - lastReportFunds else 0
            val apprDelta = if (lastReportApproval >= 0) ev[6] - lastReportApproval else 0
            lastReportFunds = b[0]; lastReportApproval = ev[6]
            styledDialog("Annual Report", listOf(
                "Class" to cityClassNames.getOrElse(ev[2]) { "?" },
                "Population" to "${ev[3]} (Δ ${ev[4]})", "Score" to "${ev[0]} (Δ ${ev[1]})",
                "Approval" to "${ev[6]}% (Δ ${signed(apprDelta)}%)",
                "Funds" to "$${b[0]} (Δ ${signed(fundsDelta)})", "Tax" to "${b[1]}%")
                + (if (np > 0) listOf("Top concerns" to concerns) else emptyList()),
                actionLabel = "Continue", subtitle = "Year $year",
                onAction = { if (resume && speed == 0) { speed = lastRunSpeed; updatePlayPauseText(); updateSpeedChipText() } })
        }
    }
}
