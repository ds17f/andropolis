package micropolis.port

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.View
import android.widget.Button
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
    private val eventBuf = IntArray(9)
    private lateinit var messageBanner: TextView
    private val bannerHide = Runnable { messageBanner.visibility = View.GONE }
    private var lastEventTile: Pair<Int, Int>? = null
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
    // engine gToolSize, index = tool value; default 1 for anything past the table
    private val toolFootprints = intArrayOf(3,3,3,3, 3,1,1,1, 1,1,4,1, 4,4,4,6, 1,1,1,1)
    private fun footprintOf(tool: Int) = toolFootprints.getOrElse(tool) { 1 }
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
    private var mapContainer: android.widget.FrameLayout = null!!

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

    class PanelTab(val title: String, val build: () -> View)

    private class CityData(
        val funds: Int, val taxRate: Int,
        val roadPct: Int, val firePct: Int, val policePct: Int,
        val pop: Int, val cityClass: Int, val approval: Int,
        val crime: Int, val pollution: Int, val landValue: Int, val traffic: Int, val density: Int
    )

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
            val d = CityData(
                funds = b[0], taxRate = b[1], roadPct = b[5], policePct = b[8], firePct = b[11],
                pop = ev[3], cityClass = ev[2], approval = ev[6],
                crime = avgOverlay(5), pollution = avgOverlay(3), landValue = avgOverlay(4),
                traffic = avgOverlay(2), density = avgOverlay(1)
            )
            ui.post { showCityPanelUI(d) }
        }
    }

    private fun showCityPanelUI(d: CityData) {
        var road = d.roadPct; var fire = d.firePct; var police = d.policePct
        fun label(text: String) = TextView(this).apply {
            this.text = text; setTextColor(0xFFEEF2F6.toInt()); textSize = 14f; setPadding(0, dp(10), 0, dp(2))
        }
        fun muted(text: String) = TextView(this).apply {
            this.text = text; setTextColor(0xFF9AA7B4.toInt()); textSize = 13f; setPadding(0, dp(2), 0, dp(2))
        }
        fun sliderRow(title: String, value: Int, max: Int, suffix: String, onApply: (Int) -> Unit): View {
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val head = label("$title: $value$suffix")
            col.addView(head)
            col.addView(android.widget.SeekBar(this).apply {
                this.max = max; progress = value
                setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: android.widget.SeekBar, p: Int, fromUser: Boolean) { head.text = "$title: $p$suffix" }
                    override fun onStartTrackingTouch(sb: android.widget.SeekBar) {}
                    override fun onStopTrackingTouch(sb: android.widget.SeekBar) { onApply(sb.progress) }
                })
            })
            return col
        }
        showPanel("City", listOf(
            PanelTab("Budget") {
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(muted("Funds: \$${d.funds}"))
                    addView(sliderRow("Tax rate", d.taxRate, 20, "%") { v -> sim.post { MicropolisNative.setCityTax(handle, v) } })
                    addView(sliderRow("Road funding", road, 100, "%") { v -> road = v; sim.post { MicropolisNative.setFunding(handle, road, fire, police) } })
                    addView(sliderRow("Fire funding", fire, 100, "%") { v -> fire = v; sim.post { MicropolisNative.setFunding(handle, road, fire, police) } })
                    addView(sliderRow("Police funding", police, 100, "%") { v -> police = v; sim.post { MicropolisNative.setFunding(handle, road, fire, police) } })
                }
            },
            PanelTab("Stats") {
                fun statRow(name: String, value: String): View = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, dp(6))
                    addView(TextView(this@MainActivity).apply { text = name; setTextColor(0xFF9AA7B4.toInt()); textSize = 14f
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
                    addView(TextView(this@MainActivity).apply { text = value; setTextColor(0xFFEEF2F6.toInt()); textSize = 14f
                        setTypeface(null, android.graphics.Typeface.BOLD) })
                }
                fun pct(v: Int) = "${v * 100 / 255}"
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(statRow("Population", "${d.pop}"))
                    addView(statRow("City class", cityClassNames.getOrElse(d.cityClass) { "—" }))
                    addView(statRow("Approval", "${d.approval}%"))
                    addView(statRow("Crime", pct(d.crime)))
                    addView(statRow("Pollution", pct(d.pollution)))
                    addView(statRow("Land value", pct(d.landValue)))
                    addView(statRow("Traffic", pct(d.traffic)))
                    addView(statRow("Density", pct(d.density)))
                }
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

    private fun showPanel(title: String, tabs: List<PanelTab>) {
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
            fun select(idx: Int) {
                content.removeAllViews()
                val sv = ScrollView(this)
                sv.addView(tabs[idx].build())
                content.addView(sv)
                chips.forEachIndexed { i, c ->
                    val on = i == idx
                    c.background = roundedBg(if (on) 0xFFF5A623.toInt() else 0x1FFFFFFF, 10)
                    c.setTextColor(if (on) 0xFF1A1207.toInt() else 0xFF9AA7B4.toInt())
                }
            }
            tabs.forEachIndexed { i, t ->
                val chip = TextView(this).apply {
                    text = t.title
                    textSize = 13f
                    setPadding(dp(14), dp(8), dp(14), dp(8))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { rightMargin = dp(8) }
                    setOnClickListener { select(i) }
                }
                chips.add(chip)
                tabRow.addView(chip)
            }
            col.addView(tabRow)
            col.addView(content)
            select(0)
        } else {
            val sv = ScrollView(this)
            sv.addView(tabs[0].build())
            col.addView(sv)
        }
        sheet.setContentView(col)
        sheet.show()
    }

    private fun promptCityName(isFirst: Boolean) {
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
            .show()
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
                val pm = PopupMenu(this@MainActivity, it as Button)
                pm.menu.add("Redo").isEnabled = cursor < history.size - 1
                pm.menu.add("New city")
                pm.menu.add("Save city")
                pm.menu.add("Load city")
                pm.menu.add("Budget")
                pm.menu.add("City evaluation")
                pm.menu.add(if (annualReportEnabled) "Annual report: On" else "Annual report: Off")
                pm.menu.add("Tax rate — ${taxRates[taxIdx]}%")
                pm.setOnMenuItemClickListener { item ->
                    when (item.title) {
                        "Redo" -> redo()
                        "New city" -> {
                            cityReady = false
                            sim.post {
                                MicropolisNative.generateRandomCity(handle)
                                MicropolisNative.saveCity(handle, autosavePath)   // reset autosave to the new city
                                cityReady = true
                                ui.post {
                                    resetHistory()                 // drop the old city's undo snapshots
                                    promptCityName(isFirst = true)
                                    commitSnapshot()               // seed with the new city
                                }
                            }
                        }
                        "Save city" -> sim.post { MicropolisNative.saveCity(handle, savePath) }
                        "Load city" -> sim.post { MicropolisNative.loadCity(handle, savePath) }
                        "Budget" -> sim.post {
                            val b = IntArray(12); MicropolisNative.getBudget(handle, b)
                            ui.post { showBudgetDialog(b) }
                        }
                        "City evaluation" -> sim.post {
                            val ev = IntArray(7); MicropolisNative.getEvaluation(handle, ev)
                            ui.post { showEvalDialog(ev) }
                        }
                        else -> when {
                            item.title.toString().startsWith("Annual report") -> {
                                annualReportEnabled = !annualReportEnabled
                                prefs.edit().putBoolean("annualReport", annualReportEnabled).apply()
                            }
                            item.title.toString().startsWith("Tax") -> {
                                taxIdx = (taxIdx + 1) % taxRates.size
                                val t = taxRates[taxIdx]
                                sim.post { MicropolisNative.setCityTax(handle, t) }
                            }
                        }
                    }
                    true
                }
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
            setOnClickListener { lastEventTile?.let { mapView.centerOnTile(it.first, it.second) } }
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
            sim.post { MicropolisNative.doTool(handle, tool, tileX, tileY) }
        }
        // One snapshot per build stroke drives Undo/Redo.
        mapView.onStrokeEnd = { built -> if (built) commitSnapshot() }
        // Wire minimap
        minimap.onTileSelected = { tx, ty -> mapView.centerOnTile(tx, ty) }
        mapView.onViewportChanged = { l, t, r, b -> minimap.setViewport(l, t, r, b) }

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
        sim.post({ tickLoop() })
    }

    private fun updatePill() {
        val ti = allTools.first { it.value == currentTool }
        pillIcon.setImageResource(ti.icon)
        pillName.text = ti.label
        mapView.toolFootprint = footprintOf(currentTool)
        mapView.straightLineTool = isStraightLineTool(currentTool)
    }

    private fun newSnapPath(): String { snapSeq++; return java.io.File(snapDir, "s$snapSeq.cty").absolutePath }

    private fun showBanner(text: String) {
        messageBanner.text = text
        messageBanner.visibility = View.VISIBLE
        ui.removeCallbacks(bannerHide)
        ui.postDelayed(bannerHide, 6000)
    }

    private fun levelName(i: Int) = arrayOf("None","Low","Medium","High","Very high").getOrElse(i) { "$i" }

    private fun showZoneStatusDialog(x: Int, y: Int, cat: Int, pop: Int, lv: Int, crime: Int, poll: Int, growth: Int) {
        val msg = """
            Tile category: $cat
            Population density: ${levelName(pop)}
            Land value: ${levelName(lv)}
            Crime rate: ${levelName(crime)}
            Pollution: ${levelName(poll)}
            Growth rate: ${levelName(growth)}
        """.trimIndent()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Zone at ($x, $y)").setMessage(msg)
            .setPositiveButton("OK", null).show()
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
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = roundedBg(0xFF1A222A.toInt(), 18)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT).apply { weight = 1f; setMargins(dp(4), 0, dp(4), 0) }
            setOnClickListener { onClick() }
        }
        pill.addView(TextView(this).apply {
            text = glyph
            textSize = 18f
            setTextColor(0xFFF5A623.toInt())
            setPadding(0, 0, dp(8), 0)
        })
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        info.addView(TextView(this).apply {
            text = label
            setTextColor(0xFFEEF2F6.toInt())
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        info.addView(state)
        pill.addView(info)
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
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    for (i in speedNames.indices) {
                        val on = speed == i
                        addView(Button(this@MainActivity).apply {
                            text = speedNames[i]
                            background = roundedBg(if (on) 0xFFF5A623.toInt() else 0x1FFFFFFF, 12)
                            setTextColor(if (on) 0xFF1A1207.toInt() else 0xFFEEF2F6.toInt())
                            stateListAnimator = null
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { topMargin = dp(8) }
                            setOnClickListener {
                                speed = i
                                if (i > 0) lastRunSpeed = i
                                updatePlayPauseText()
                                updateSpeedChipText()
                            }
                        })
                    }
                }
            },
            PanelTab("Disasters") {
                val names = listOf("Fire", "Flood", "Tornado", "Earthquake", "Monster", "Meltdown")
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    names.forEachIndexed { kind, n ->
                        addView(Button(this@MainActivity).apply {
                            text = n
                            background = roundedBg(0x1FFFFFFF, 12)
                            setTextColor(0xFFEEF2F6.toInt())
                            stateListAnimator = null
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { topMargin = dp(8) }
                            setOnClickListener { sim.post { MicropolisNative.makeDisaster(handle, kind) } }
                        })
                    }
                }
            }
        ))
    }

    private fun showOverlayPanel() {
        showPanel("Map overlay", listOf(PanelTab("Mode") {
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                overlayNames.forEachIndexed { kind, name ->
                    addView(Button(this@MainActivity).apply {
                        text = name
                        val on = kind == currentOverlay
                        background = roundedBg(if (on) 0xFFF5A623.toInt() else 0x1FFFFFFF, 12)
                        setTextColor(if (on) 0xFF1A1207.toInt() else 0xFFEEF2F6.toInt())
                        stateListAnimator = null
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
                        setOnClickListener {
                            currentOverlay = kind
                            overlayState.text = name
                            if (kind == 0) mapView.setOverlay(0, null)
                        }
                    })
                }
            }
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
                        showBanner(msgText(a))
                        if (ex >= 0 && ey >= 0) {
                            lastEventTile = Pair(ex, ey)
                            if (c == 1) mapView.centerOnTile(ex, ey)   // important → auto-zoom
                        }
                    }
                    1 -> showZoneStatusDialog(ex, ey, a, b, c, d, e2, f) // Query result
                    2 -> { lastEventTile = Pair(ex, ey); mapView.centerOnTile(ex, ey) } // AUTO_GOTO
                    3 -> showBanner("Earthquake! (strength $a)")
                    4 -> showBanner("Your city has fallen.")
                    5 -> showBanner("You won!")
                }
            }
        }
        sim.postDelayed({ tickLoop() }, 100)
    }

    private fun showBudgetDialog(b: IntArray) {
        val msg = """
            Funds: $${b[0]}
            Tax rate: ${b[1]}%     Tax income: $${b[2]}

            Roads:  $${b[4]} / $${b[3]}   (${b[5]}%)
            Police: $${b[7]} / $${b[6]}   (${b[8]}%)
            Fire:   $${b[10]} / $${b[9]}   (${b[11]}%)
        """.trimIndent()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("City Budget").setMessage(msg)
            .setPositiveButton("OK", null).show()
    }

    private fun showEvalDialog(ev: IntArray) {
        val classes = arrayOf("Village","Town","City","Capital","Metropolis","Megalopolis")
        val cls = classes.getOrElse(ev[2]) { "?" }
        val msg = """
            Score: ${ev[0]}  (Δ ${ev[1]})
            Class: $cls
            Population: ${ev[3]}  (Δ ${ev[4]})
            Assessed value: $${ev[5]}
            Approval: ${ev[6]}%
        """.trimIndent()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("City Evaluation").setMessage(msg)
            .setPositiveButton("OK", null).show()
    }

    private fun showReportCard(year: Int, resume: Boolean) {
        sim.post {
            val ev = IntArray(7); MicropolisNative.getEvaluation(handle, ev)
            val b = IntArray(12); MicropolisNative.getBudget(handle, b)
            ui.post {
                val cls = cityClassNames.getOrElse(ev[2]) { "?" }
                val msg = """
                    Class: $cls
                    Population: ${ev[3]}  (Δ ${ev[4]})
                    Score: ${ev[0]}  (Δ ${ev[1]})
                    Approval: ${ev[6]}%
                    Funds: $${b[0]}    Tax: ${b[1]}%
                """.trimIndent()
                // Resume the sim at its previous speed once the player dismisses the card.
                val onContinue = android.content.DialogInterface.OnClickListener { _, _ ->
                    if (resume && speed == 0) {
                        speed = lastRunSpeed; updatePlayPauseText(); updateSpeedChipText()
                    }
                }
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Annual Report — $year")
                    .setMessage(msg)
                    .setPositiveButton("Continue", onContinue)
                    .setCancelable(false)
                    .show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (cityReady && handle != 0L) sim.post { MicropolisNative.saveCity(handle, autosavePath) }
    }

    override fun onDestroy() {
        super.onDestroy()
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
