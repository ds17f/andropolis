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

/**
 * First light: create the engine, generate a random city, then tick and redraw
 * on a timer. The sim runs on a dedicated thread; touch events are marshaled
 * to that thread as messages.
 */
class MainActivity : AppCompatActivity() {

    private var handle: Long = 0L
    private var currentTool = 11
    private lateinit var mapView: MapView
    private lateinit var buf: ShortArray
    private lateinit var sim: Handler
    private lateinit var ui: Handler
    private lateinit var sfx: SoundFx
    private val statsBuf = IntArray(10)
    @Volatile private var speed = 2   // 0=Pause 1=Slow 2=Med 3=Fast
    private var lastRunSpeed = 2
    private val speedNames = arrayOf("Pause", "Slow", "Med", "Fast")
    private val speedTicks = intArrayOf(0, 2, 8, 20)
    private val taxRates = intArrayOf(0, 5, 7, 9, 12, 15, 20)
    private var taxIdx = 2   // start at 7%
    private val savePath by lazy { java.io.File(filesDir, "city.cty").absolutePath }
    private val autosavePath by lazy { java.io.File(filesDir, "autosave.cty").absolutePath }
    private val prefs by lazy { getSharedPreferences("micropolis", MODE_PRIVATE) }
    private val citiesDir by lazy { java.io.File(filesDir, "cities").apply { mkdirs() } }
    private fun sanitize(name: String) = name.trim().replace(Regex("[^A-Za-z0-9 _-]"), "").ifEmpty { "City" }
    private fun cityFile(name: String) = java.io.File(citiesDir, "$name.cty")
    private var cityName: String = "Micropolis"
    @Volatile private var cityReady = false      // true once a city exists (guard autosave)
    private var lastAutosaveMs = 0L
    private val snapDir by lazy { java.io.File(filesDir, "undo").apply { mkdirs() } }
    private val history = mutableListOf<String>()   // snapshot file paths, oldest..newest
    private var cursor = -1                          // index of the current live state
    private var snapSeq = 0
    private val undoCap = 24
    private var currentOverlay = 0
    private val overlayBuf = ByteArray(120 * 100)
    private val overlayNames = arrayOf("Off","Population","Traffic","Pollution","Land value","Crime","Growth","Power")
    private var annualReportEnabled = true
    private var lastReportYear = -1
    private var lastReportFunds = -1        // for the annual-report funds delta
    private var lastReportApproval = -1     // for the annual-report approval delta
    private var minimapNav = false          // Settings: minimap navigation mode
    private var autoGoto = true             // Settings: jump the map to events as they happen
    private val navZoom = 5f                 // fixed zoom when minimap navigation is on
    private val eventBuf = IntArray(9)
    private lateinit var messageBanner: TextView
    private val bannerHide = Runnable { messageBanner.visibility = View.GONE; viewBeforeJump = null }
    private var lastEventTile: Pair<Int, Int>? = null
    private var viewBeforeJump: FloatArray? = null
    private class LogEntry(val date: String, val text: String, val x: Int, val y: Int)
    private val messageLog = ArrayDeque<LogEntry>()
    private fun logMessage(text: String, x: Int, y: Int) {
        messageLog.addFirst(LogEntry(subtitle.text.toString(), text, x, y))   // newest first
        while (messageLog.size > 50) messageLog.removeLast()
    }
    private val messageText = arrayOf(
        "", "More residential zones needed", "More commercial zones needed",
        "More industrial zones needed", "More roads required", "Inadequate rail system",
        "Build a power plant", "Residents demand a stadium", "Industry requires a seaport",
        "Commerce requires an airport", "Pollution very high", "Crime very high",
        "Frequent traffic jams reported", "Citizens demand a fire department",
        "Citizens demand a police department", "Blackouts reported — check the power map",
        "Citizens upset: taxes too high", "Roads deteriorating — underfunded",
        "Fire departments need funding", "Police departments need funding", "Fire reported!",
        "A monster has been sighted!", "Tornado reported!", "Major earthquake reported!",
        "A plane has crashed!", "Shipwreck reported!", "A train crashed!",
        "A helicopter crashed!", "Unemployment is high", "YOUR CITY HAS GONE BROKE!",
        "Firebombing reported!", "Need more parks", "Explosion detected!",
        "Insufficient funds to build that", "Area must be bulldozed first",
        "Population has reached 2,000", "Population has reached 10,000",
        "Population has reached 50,000", "Population has reached 100,000",
        "Population has reached 500,000", "Brownouts — build another power plant",
        "Heavy traffic reported", "Flooding reported!", "A nuclear meltdown has occurred!",
        "They're rioting in the streets!", "Started a new city", "Restored a saved city",
        "You won the scenario!", "You lost the scenario", "About Micropolis"
    )
    private fun msgText(i: Int) = messageText.getOrElse(i) { "City update" }
    /** Icon for an engine message index (1..57) — shown in the toast and the feed. */
    private fun msgIcon(i: Int): String = when (i) {
        1 -> "🏠"; 2 -> "🏢"; 3 -> "🏭"; 4 -> "🛣"; 5, 26 -> "🚆"; 6, 15, 40 -> "⚡"
        7 -> "🏟"; 8, 25 -> "⚓"; 9, 24 -> "✈"; 10 -> "☁"; 11 -> "🚨"; 12, 41 -> "🚗"
        13 -> "🚒"; 14 -> "🚓"; 16, 17, 18, 19 -> "💰"; 20 -> "🔥"; 21 -> "👾"; 22 -> "🌪"
        23 -> "⛰"; 27 -> "🚁"; 28 -> "📉"; 29, 33 -> "💸"; 30 -> "💣"; 31 -> "🌳"; 32 -> "💥"
        34 -> "🚜"; in 35..39 -> "🎉"; 42 -> "🌊"; 43 -> "☢"; 44 -> "✊"; 45, 46 -> "🏙"
        47 -> "🏆"; 48 -> "💀"; else -> "ℹ"
    }
    // engine gToolSize, index = tool value; default 1 for anything past the table
    private val toolFootprints = intArrayOf(3,3,3,3, 3,1,1,1, 1,1,4,1, 4,4,4,6, 1,1,1,1)
    private fun footprintOf(tool: Int) = toolFootprints.getOrElse(tool) { 1 }
    // engine gCostOf, index = tool value
    private val toolCosts = intArrayOf(100,100,100,500, 500,0,5,1, 20,10,5000,10, 3000,3000,5000,10000, 100,0,0,0)
    private fun costOf(tool: Int) = toolCosts.getOrElse(tool) { 0 }
    // history graph colors and labels (res, com, ind, money, crime, poll)
    private val histColors = intArrayOf(0xFF4CAF50.toInt(), 0xFF42A5F5.toInt(), 0xFFF5A623.toInt(),
        0xFFEEF2F6.toInt(), 0xFFE5533D.toInt(), 0xFF9C6ADE.toInt())   // money = white (distinct from residential green)
    private val histVisible = BooleanArray(6) { true }   // graph line toggles (persist while app runs)
    private val histNames = arrayOf("Residential", "Commercial", "Industrial", "Money", "Crime", "Pollution")
    private lateinit var topBar: LinearLayout
    private lateinit var cityTitle: android.widget.TextView
    private lateinit var subtitle: android.widget.TextView
    private lateinit var playPauseBtn: Button
    private lateinit var overflowBtn: Button
    private lateinit var fundsChip: LinearLayout
    private lateinit var fundsValue: android.widget.TextView
    private lateinit var popChip: LinearLayout
    private lateinit var popValue: android.widget.TextView
    private lateinit var scoreChip: LinearLayout
    private lateinit var scoreValue: android.widget.TextView
    private lateinit var pillIcon: ImageView
    private lateinit var pillName: TextView
    private lateinit var bottom: LinearLayout
    private lateinit var minimap: MinimapView
    private lateinit var toolPill: LinearLayout
    private lateinit var undoBtn: Button
    private lateinit var panelBar: LinearLayout
    private lateinit var simState: TextView
    private lateinit var overlayState: TextView
    private lateinit var mapContainer: android.widget.FrameLayout

    // Road / rail / wire draw straight axis-locked lines when dragged.
    private fun isStraightLineTool(tool: Int) = tool == 6 || tool == 8 || tool == 9
    private val toolCategories = linkedMapOf(
        "Zones" to listOf(ToolItem("Residential", 0, R.drawable.ic_residential), ToolItem("Commercial", 1, R.drawable.ic_commercial), ToolItem("Industrial", 2, R.drawable.ic_industrial), ToolItem("Park", 11, R.drawable.ic_park)),
        "Transport" to listOf(ToolItem("Road", 9, R.drawable.ic_road), ToolItem("Rail", 8, R.drawable.ic_rail), ToolItem("Wire", 6, R.drawable.ic_wire), ToolItem("Bulldozer", 7, R.drawable.ic_bulldozer)),
        "Services & Power" to listOf(ToolItem("Police", 4, R.drawable.ic_police), ToolItem("Fire", 3, R.drawable.ic_fire), ToolItem("Coal", 13, R.drawable.ic_coal), ToolItem("Nuclear", 14, R.drawable.ic_nuclear)),
        "Special" to listOf(ToolItem("Stadium", 10, R.drawable.ic_stadium), ToolItem("Seaport", 12, R.drawable.ic_seaport), ToolItem("Airport", 15, R.drawable.ic_airport), ToolItem("Query", 5, R.drawable.ic_query))
    )
    private val allTools by lazy { toolCategories.values.flatten() }

    data class ToolItem(val label: String, val value: Int, val icon: Int)

    class PanelTab(val title: String, val glyph: String = "", val build: () -> View)

    private class CardHandle(val view: LinearLayout, val setSelected: (Boolean) -> Unit)

    private val cityClassNames = arrayOf("Village","Town","City","Capital","Metropolis","Megalopolis")

    private fun avgOverlay(kind: Int): Int {
        val a = ByteArray(120 * 100)
        val n = MicropolisNative.copyOverlay(handle, kind, a)
        if (n <= 0) return 0
        var sum = 0L
        for (b in a) sum += (b.toInt() and 0xFF)
        return (sum / a.size).toInt()
    }

    private fun showCityPanel() {
        sim.post {
            val b = IntArray(12); MicropolisNative.getBudget(handle, b)
            val ev = IntArray(7); MicropolisNative.getEvaluation(handle, ev)
            val crime = avgOverlay(5); val poll = avgOverlay(3); val land = avgOverlay(4)
            val traffic = avgOverlay(2); val density = avgOverlay(1)
            ui.post { showCityPanelUI(b, ev, crime, poll, land, traffic, density) }
        }
    }

    private fun levelWord(v: Int) = when {
        v < 26 -> "None"; v < 77 -> "Low"; v < 128 -> "Medium"; v < 191 -> "High"; else -> "Very high"
    }

    /** A labeled stat as a proportional coloured bar. v is 0..255. */
    private fun statBar(label: String, v: Int): View {
        val pct = (v * 100 / 255).coerceIn(0, 100)
        val color = when { v < 77 -> 0xFF4CAF50.toInt(); v < 160 -> 0xFFF5A623.toInt(); else -> 0xFFE5533D.toInt() }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(8))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(TextView(this@MainActivity).apply {
                    text = label; setTextColor(0xFF9AA7B4.toInt()); textSize = 13f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(TextView(this@MainActivity).apply {
                    text = levelWord(v); setTextColor(0xFFEEF2F6.toInt()); textSize = 13f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                })
            })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                background = roundedBg(0x1FFFFFFF, 6)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8)).apply { topMargin = dp(4) }
                addView(View(this@MainActivity).apply { background = roundedBg(color, 6)
                    layoutParams = LinearLayout.LayoutParams(0, dp(8), pct.toFloat()) })
                addView(View(this@MainActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(8), (100 - pct).toFloat()) })
            })
        }
    }

    private fun showCityPanelUI(b: IntArray, ev: IntArray, crime: Int, poll: Int, land: Int, traffic: Int, density: Int) {
        fun row(k: String, v: String) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(7), 0, dp(7))
            addView(TextView(this@MainActivity).apply { text = k; setTextColor(0xFF9AA7B4.toInt()); textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
            addView(TextView(this@MainActivity).apply { text = v; setTextColor(0xFFEEF2F6.toInt()); textSize = 14f
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
                    addView(View(this@MainActivity).apply {
                        setBackgroundColor(0x1FFFFFFF)
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
                            .apply { topMargin = dp(8); bottomMargin = dp(4) }
                    })
                    addView(statBar("Crime", crime)); addView(statBar("Pollution", poll))
                    addView(statBar("Land value", land)); addView(statBar("Traffic", traffic))
                    addView(statBar("Population density", density))
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
                        addView(android.widget.SeekBar(this@MainActivity).apply {
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
                        addView(View(this@MainActivity).apply { background = roundedBg(histColors[i], 3)
                            layoutParams = LinearLayout.LayoutParams(dp(14), dp(14)).apply { rightMargin = dp(8) } })
                        addView(TextView(this@MainActivity).apply { text = histNames[i]; setTextColor(0xFF9AA7B4.toInt()); textSize = 13f })
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

    private fun roundedBg(color: Int, radiusDp: Int): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(color)
        d.cornerRadius = radiusDp * resources.displayMetrics.density
        return d
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** A reusable styled dialog: dark rounded panel with bold title, row labels/values, amber button. */
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

    /** A tool-style card: glyph in a tinted square + label; call setSelected to highlight. */
    private fun panelCard(glyph: String, label: String, onClick: () -> Unit): CardHandle {
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
    private fun addCard(grid: android.widget.GridLayout, h: CardHandle) {
        val lp = android.widget.GridLayout.LayoutParams()
        lp.width = 0
        lp.columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
        lp.setMargins(dp(4), dp(4), dp(4), dp(4))
        h.view.layoutParams = lp
        grid.addView(h.view)
    }

    private fun showMessagesPanel(onDismiss: (() -> Unit)? = null) {
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
        }), onDismiss)
    }

    private fun showPanel(title: String, tabs: List<PanelTab>, onDismiss: (() -> Unit)? = null) {
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

    private fun promptCityName(isFirst: Boolean, onDismiss: (() -> Unit)? = null) {
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
                cityTitle.text = name
            }
            .setCancelable(false)
            .create().also { dialog ->
                dialog.setOnDismissListener { onDismiss?.invoke() }
                dialog.show()
            }
    }

    private fun pauseForUi(): () -> Unit {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create sim thread with handler
        val simThread = HandlerThread("sim")
        simThread.start()
        sim = Handler(simThread.looper)
        ui = Handler(Looper.getMainLooper())

        buf = ShortArray(MicropolisNative.mapWidth() * MicropolisNative.mapHeight())
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
                val pm = PopupMenu(this@MainActivity, it as Button)
                pm.menu.add("Redo").isEnabled = cursor < history.size - 1
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
                                    resetHistory()                 // drop the old city's undo snapshots
                                    promptCityName(isFirst = true, onDismiss = resume)
                                    commitSnapshot()               // seed with the new city
                                }
                            }
                            handedOff = true
                        }
                        "Save city" -> { handedOff = true; showSaveDialog(resume) }
                        "Load city" -> { handedOff = true; showLoadDialog(resume) }
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

        mapView.toolFootprint = footprintOf(currentTool)
        mapView.straightLineTool = isStraightLineTool(currentTool)
        updatePill()
        updateUndoButtons()

        // Set up annual report state
        annualReportEnabled = prefs.getBoolean("annualReport", true)

        // Set up tap listener
        mapView.onTileTap = { tileX, tileY ->
            val tool = currentTool
            sim.post {
                val r = MicropolisNative.doTool(handle, tool, tileX, tileY)
                if (r == 1) ui.post { if (tool == 7) sfx.bulldoze() else sfx.build() }
            }
        }
        // One snapshot per build stroke drives Undo/Redo.
        mapView.onStrokeEnd = { built -> if (built) commitSnapshot() }
        // Wire minimap
        minimap.onTileSelected = { tx, ty ->
            mapView.centerOnTile(tx, ty)
            viewBeforeJump = null
        }
        mapView.onViewportChanged = { l, t, r, b -> minimap.setViewport(l, t, r, b) }
        mapView.onUserNavigate = { viewBeforeJump = null }
        minimapNav = prefs.getBoolean("minimapNav", false)
        autoGoto = prefs.getBoolean("autoGoto", true)
        applyMinimapMode()

        // Setup on sim thread
        sim.post {
            handle = MicropolisNative.create()
            MicropolisNative.init(handle)
            val hasSave = java.io.File(autosavePath).exists()
            if (hasSave) {
                MicropolisNative.loadCity(handle, autosavePath)
                cityReady = true
                ui.post {
                    cityName = prefs.getString("cityName", "Micropolis") ?: "Micropolis"
                    cityTitle.text = cityName
                    commitSnapshot()   // seed the undo history with the restored city
                }
            } else {
                MicropolisNative.generateRandomCity(handle)
                cityReady = true
                ui.post {
                    promptCityName(isFirst = true)   // name a brand-new city
                    commitSnapshot()                 // seed the undo history with the starting city
                }
            }
        }

        // Start tick loop on sim thread
        sfx = SoundFx(this)
        sfx.enabled = prefs.getBoolean("sound", true)
        sim.post({ tickLoop() })
    }

    private fun updatePill() {
        val ti = allTools.first { it.value == currentTool }
        pillIcon.setImageResource(ti.icon)
        pillName.text = ti.label
        mapView.toolFootprint = footprintOf(currentTool)
        mapView.straightLineTool = isStraightLineTool(currentTool)
        mapView.tapOnlyTool = currentTool == 5   // Query: tap to inspect, never on drag/pinch
    }

    private fun newSnapPath(): String { snapSeq++; return java.io.File(snapDir, "s$snapSeq.cty").absolutePath }

    private fun showBanner(text: String) {
        messageBanner.text = text
        messageBanner.visibility = View.VISIBLE
        ui.removeCallbacks(bannerHide)
        ui.postDelayed(bannerHide, 6000)
    }

    private fun autoJumpTo(x: Int, y: Int) {
        if (viewBeforeJump == null) viewBeforeJump = mapView.saveView()
        mapView.centerOnTile(x, y)
        messageBanner.text = messageBanner.text.toString() + "   ↩ Back"
    }

    // The engine's zone-status values are 1-based indices into these tables (engine data
    // files stri.202 / stri.219): density 1-4, land value 5-8, crime 9-12,
    // pollution 13-16, growth 17-20.
    private val zoneStatusWords = arrayOf(
        "Low", "Medium", "High", "Very High",
        "Slum", "Lower Class", "Middle Class", "High",
        "Safe", "Light", "Moderate", "Dangerous",
        "None", "Moderate", "Heavy", "Very Heavy",
        "Declining", "Stable", "Slow Growth", "Fast Growth")
    private val tileCategoryNames = arrayOf(
        "Clear", "Water", "Trees", "Rubble", "Flood", "Radioactive Waste", "Fire", "Road",
        "Power", "Rail", "Residential", "Commercial", "Industrial", "Seaport", "Airport",
        "Coal Power", "Fire Department", "Police Department", "Stadium", "Nuclear Power",
        "Draw Bridge", "Radar Dish", "Fountain", "Industrial", "Stadium", "Draw Bridge",
        "Nuclear Waste")
    private fun statusWord(i: Int) = zoneStatusWords.getOrElse(i - 1) { "—" }

    private fun showZoneStatusDialog(x: Int, y: Int, cat: Int, pop: Int, lv: Int, crime: Int, poll: Int, growth: Int) {
        styledDialog(tileCategoryNames.getOrElse(cat - 1) { "Clear" }, listOf(
            "Population density" to statusWord(pop),
            "Land value" to statusWord(lv), "Crime" to statusWord(crime),
            "Pollution" to statusWord(poll), "Growth" to statusWord(growth)),
            subtitle = "Tile ($x, $y)")
    }

    /** Snapshot the current (post-build) state as the new head, dropping any redo branch. */
    private fun commitSnapshot() {
        // drop the redo branch (everything after the cursor)
        while (history.size > cursor + 1) { java.io.File(history.removeAt(history.size - 1)).delete() }
        val path = newSnapPath()
        sim.post { MicropolisNative.saveCity(handle, path) }
        history.add(path); cursor = history.size - 1
        // trim the oldest snapshot if over the cap
        while (history.size > undoCap) { java.io.File(history.removeAt(0)).delete(); cursor-- }
        updateUndoButtons()
    }

    private fun undo() {
        if (cursor > 0) { cursor--; sim.post { MicropolisNative.loadCity(handle, history[cursor]) }; updateUndoButtons() }
    }

    private fun redo() {
        if (cursor < history.size - 1) { cursor++; sim.post { MicropolisNative.loadCity(handle, history[cursor]) }; updateUndoButtons() }
    }

    private fun updateUndoButtons() {
        val canUndo = cursor > 0
        undoBtn.isEnabled = canUndo; undoBtn.alpha = if (canUndo) 1f else 0.35f
    }

    /** Drop all undo snapshots (used when starting a fresh city). */
    private fun resetHistory() {
        for (p in history) java.io.File(p).delete()
        history.clear(); cursor = -1
        updateUndoButtons()
    }

    private fun openPalette() {
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val scroll = android.widget.ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 12, 20, 28)
            setBackgroundColor(0xFF12161C.toInt())
        }
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

    private fun buildPill(glyph: String, label: String, state: TextView, onClick: () -> Unit): LinearLayout {
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

    private fun buildToolCard(ti: ToolItem, sheet: com.google.android.material.bottomsheet.BottomSheetDialog): View {
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
            text = if (cost == 0) "Free" else "$$cost"
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
        card.setOnClickListener { currentTool = ti.value; updatePill(); sheet.dismiss() }
        return card
    }

    private fun updatePlayPauseText() {
        playPauseBtn.text = if (speed == 0) "▶" else "⏸"
    }

    private fun updateSpeedChipText() { simState.text = speedNames[speed] }

    private fun showSimulationPanel() {
        showPanel("Simulation", listOf(
            PanelTab("Speed") {
                val glyphs = arrayOf("⏸", "▶", "▶▶", "⏩")
                val grid = android.widget.GridLayout(this).apply { columnCount = 4 }
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
                val names = arrayOf("Fire","Flood","Tornado","Earthquake","Monster","Meltdown")
                val glyphs = arrayOf("🔥","🌊","🌪","⛰","👾","☢")
                val grid = android.widget.GridLayout(this).apply { columnCount = 3 }
                names.forEachIndexed { kind, n ->
                    addCard(grid, panelCard(glyphs[kind], n) { sim.post { MicropolisNative.makeDisaster(handle, kind) } })
                }
                grid
            }
        ))
    }

    private fun showOverlayPanel() {
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

    private fun tickLoop() {
        if (cityReady && handle != 0L) {
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastAutosaveMs > 30_000L) {
                lastAutosaveMs = now
                MicropolisNative.saveCity(handle, autosavePath)
            }
        }
        repeat(speedTicks[speed]) { MicropolisNative.simTick(handle) }
        MicropolisNative.copyTiles(handle, buf)
        val tilesCopy = buf.copyOf()
        ui.post { mapView.update(tilesCopy); minimap.update(tilesCopy) }

        // Refresh overlay when one is active
        if (currentOverlay != 0 && cityReady && handle != 0L) {
            MicropolisNative.copyOverlay(handle, currentOverlay, overlayBuf)
            val snap = overlayBuf.copyOf()
            ui.post { mapView.setOverlay(currentOverlay, snap) }
        }
        MicropolisNative.getStats(handle, statsBuf)
        val funds = statsBuf[1]; val pop = statsBuf[2]; val score = statsBuf[3]
        val year = statsBuf[4]; val month = statsBuf[5]
        val months = arrayOf("Jan","Feb","Mar","Apr","May","Jun",
                            "Jul","Aug","Sep","Oct","Nov","Dec")
        val monthName = months.getOrElse(month) { "?" }
        ui.post {
            subtitle.text = "$monthName $year"
            fundsValue.text = "\$$funds"
            popValue.text = "$pop"
            scoreValue.text = "$score"
        }
        val yearRolled = lastReportYear != -1 && year > lastReportYear && annualReportEnabled
        lastReportYear = year
        if (yearRolled) {
            ui.post {
                val didPause = speed != 0
                if (didPause) { lastRunSpeed = speed; speed = 0; updatePlayPauseText(); updateSpeedChipText() }
                showReportCard(year, resume = didPause)
            }
        }
        while (MicropolisNative.pollEvent(handle, eventBuf)) {
            val type = eventBuf[0]; val ex = eventBuf[1]; val ey = eventBuf[2]
            val a = eventBuf[3]; val b = eventBuf[4]; val c = eventBuf[5]
            val d = eventBuf[6]; val e2 = eventBuf[7]; val f = eventBuf[8]
            ui.post {
                when (type) {
                    0 -> { // MESSAGE
                        val line = "${msgIcon(a)}  ${msgText(a)}"
                        showBanner(line)
                        logMessage(line, ex, ey)
                        if (ex >= 0 && ey >= 0) {
                            lastEventTile = Pair(ex, ey)
                            if (autoGoto) autoJumpTo(ex, ey)  // Settings → Auto go to events
                        }
                    }
                    1 -> showZoneStatusDialog(ex, ey, a, b, c, d, e2, f) // Query result
                    2 -> { lastEventTile = Pair(ex, ey); if (autoGoto) autoJumpTo(ex, ey) } // AUTO_GOTO
                    3 -> showBanner("⛰  Earthquake! (strength $a)")
                    4 -> showBanner("💀  Your city has fallen.")
                    5 -> showBanner("🏆  You won!")
                    6 -> sfx.engineSound(a) // SOUND
                }
            }
        }
        sim.postDelayed({ tickLoop() }, 100)
    }

    private fun showBudgetDialog(b: IntArray) {
        styledDialog("City Budget", listOf(
            "Funds" to "$${b[0]}", "Tax rate" to "${b[1]}%", "Tax income" to "$${b[2]}",
            "Roads" to "$${b[4]} / $${b[3]} (${b[5]}%)",
            "Police" to "$${b[7]} / $${b[6]} (${b[8]}%)",
            "Fire" to "$${b[10]} / $${b[9]} (${b[11]}%)"))
    }

    private fun showEvalDialog(ev: IntArray) {
        styledDialog("City Evaluation", listOf(
            "Score" to "${ev[0]} (Δ ${ev[1]})", "Class" to cityClassNames.getOrElse(ev[2]) { "?" },
            "Population" to "${ev[3]} (Δ ${ev[4]})", "Assessed value" to "$${ev[5]}",
            "Approval" to "${ev[6]}%"))
    }

    private fun showReportCard(year: Int, resume: Boolean) {
        sim.post {
            val ev = IntArray(7); MicropolisNative.getEvaluation(handle, ev)
            val b = IntArray(12); MicropolisNative.getBudget(handle, b)
            ui.post {
                fun signed(n: Int) = if (n >= 0) "+$n" else "$n"
                val fundsDelta = if (lastReportFunds >= 0) b[0] - lastReportFunds else 0
                val apprDelta = if (lastReportApproval >= 0) ev[6] - lastReportApproval else 0
                lastReportFunds = b[0]; lastReportApproval = ev[6]
                styledDialog("Annual Report", listOf(
                    "Class" to cityClassNames.getOrElse(ev[2]) { "?" },
                    "Population" to "${ev[3]} (Δ ${ev[4]})", "Score" to "${ev[0]} (Δ ${ev[1]})",
                    "Approval" to "${ev[6]}% (Δ ${signed(apprDelta)}%)",
                    "Funds" to "$${b[0]} (Δ ${signed(fundsDelta)})", "Tax" to "${b[1]}%"),
                    actionLabel = "Continue", subtitle = "Year $year",
                    onAction = { if (resume && speed == 0) { speed = lastRunSpeed; updatePlayPauseText(); updateSpeedChipText() } })
            }
        }
    }

    private fun applyMinimapMode() {
        if (minimapNav) {
            minimap.visibility = View.VISIBLE
            mapView.setNavLocked(true, navZoom)
        } else {
            minimap.visibility = View.GONE
            mapView.setNavLocked(false, navZoom)
        }
    }

    private fun showSaveDialog(onDismiss: (() -> Unit)? = null) {
        val input = android.widget.EditText(this).apply { setText(cityName); setSingleLine() }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Save city as")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = sanitize(input.text.toString())
                val path = cityFile(name).absolutePath
                cityName = name; prefs.edit().putString("cityName", name).apply(); cityTitle.text = name
                sim.post { MicropolisNative.saveCity(handle, path); MicropolisNative.saveCity(handle, autosavePath) }
                showBanner("Saved “$name”")
            }
            .setNegativeButton("Cancel", null)
            .create().also { dialog ->
                dialog.setOnDismissListener { onDismiss?.invoke() }
                dialog.show()
            }
    }

    private fun showLoadDialog(onDismiss: (() -> Unit)? = null) {
        val files = citiesDir.listFiles { f: java.io.File -> f.name.endsWith(".cty") }
            ?.sortedWith(compareBy { it.name })
        val fileList = files?.toTypedArray() ?: emptyArray()
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(24))
            setBackgroundColor(0xFF12161C.toInt())
        }
        col.addView(TextView(this).apply {
            text = "Load city"; setTextColor(0xFFEEF2F6.toInt()); textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 0, 0, dp(8))
        })
        if (fileList.isEmpty()) {
            col.addView(TextView(this).apply { text = "No saved cities yet."; setTextColor(0xFF9AA7B4.toInt()); setPadding(0, dp(8), 0, dp(8)) })
        }
        var dismissedInternally = false
        for (f in fileList) {
            val name = f.name.removeSuffix(".cty")
            col.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
                background = roundedBg(0x0DFFFFFF, 12); setPadding(dp(14), dp(12), dp(8), dp(12))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
                addView(TextView(this@MainActivity).apply {
                    text = name; setTextColor(0xFFEEF2F6.toInt()); textSize = 15f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(TextView(this@MainActivity).apply {
                    text = "✕"; setTextColor(0xFF9AA7B4.toInt()); textSize = 16f; setPadding(dp(12), 0, dp(12), 0)
                    setOnClickListener { f.delete(); sheet.dismiss(); dismissedInternally = true; showLoadDialog(onDismiss) }   // refresh
                })
                setOnClickListener {
                    val path = f.absolutePath
                    cityName = name; prefs.edit().putString("cityName", name).apply(); cityTitle.text = name
                    sim.post {
                        MicropolisNative.loadCity(handle, path)
                        MicropolisNative.saveCity(handle, autosavePath)   // make restore-on-launch match
                        ui.post { resetHistory(); commitSnapshot() }       // fresh undo history for the loaded city
                    }
                    showBanner("Loaded “$name”")
                    sheet.dismiss()
                }
            })
        }
        val sv = ScrollView(this); sv.addView(col); sheet.setContentView(sv)
        sheet.setOnDismissListener { if (!dismissedInternally) onDismiss?.invoke() }
        sheet.show()
    }

    /** One Settings row: bold title, muted description, and a switch on the right. */
    private fun settingsToggle(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@MainActivity).apply {
                    text = title; setTextColor(0xFFEEF2F6.toInt()); textSize = 15f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                })
                addView(TextView(this@MainActivity).apply {
                    text = desc; setTextColor(0xFF9AA7B4.toInt()); textSize = 12f
                })
            })
            addView(android.widget.Switch(this@MainActivity).apply {
                isChecked = checked
                setOnCheckedChangeListener { _, c -> onChange(c) }
            })
        }

    private fun showSettingsPanel(onDismiss: (() -> Unit)? = null) {
        showPanel("Settings", listOf(PanelTab("General") {
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
                addView(settingsToggle("Auto go to events",
                    "Jump the map to fires, disasters and other alerts as they happen.", autoGoto) { c ->
                    autoGoto = c; prefs.edit().putBoolean("autoGoto", c).apply()
                })
            }
        }), onDismiss)
    }

    override fun onPause() {
        super.onPause()
        if (cityReady && handle != 0L) sim.post { MicropolisNative.saveCity(handle, autosavePath) }
    }

    override fun onDestroy() {
        super.onDestroy()
        sfx.release()
        ui.removeCallbacksAndMessages(null)
        sim.post {
            if (handle != 0L) {
                MicropolisNative.destroy(handle)
                handle = 0L
            }
        }
        sim.looper.quitSafely()
    }
}
