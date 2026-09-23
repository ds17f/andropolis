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
    private val snapDir by lazy { java.io.File(filesDir, "undo").apply { mkdirs() } }
    private val history = mutableListOf<String>()   // snapshot file paths, oldest..newest
    private var cursor = -1                          // index of the current live state
    private var snapSeq = 0
    private val undoCap = 24
    private lateinit var topBar: LinearLayout
    private lateinit var cityTitle: android.widget.TextView
    private lateinit var subtitle: android.widget.TextView
    private lateinit var playPauseBtn: Button
    private lateinit var speedChip: Button
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
    private lateinit var toolPill: LinearLayout
    private lateinit var undoBtn: Button
    private lateinit var redoBtn: Button
    private val toolCategories = linkedMapOf(
        "Zones" to listOf(ToolItem("Residential", 0, R.drawable.ic_residential), ToolItem("Commercial", 1, R.drawable.ic_commercial), ToolItem("Industrial", 2, R.drawable.ic_industrial), ToolItem("Park", 11, R.drawable.ic_park)),
        "Transport" to listOf(ToolItem("Road", 9, R.drawable.ic_road), ToolItem("Rail", 8, R.drawable.ic_rail), ToolItem("Wire", 6, R.drawable.ic_wire), ToolItem("Bulldozer", 7, R.drawable.ic_bulldozer)),
        "Services & Power" to listOf(ToolItem("Police", 4, R.drawable.ic_police), ToolItem("Fire", 3, R.drawable.ic_fire), ToolItem("Coal", 13, R.drawable.ic_coal), ToolItem("Nuclear", 14, R.drawable.ic_nuclear)),
        "Special" to listOf(ToolItem("Stadium", 10, R.drawable.ic_stadium), ToolItem("Seaport", 12, R.drawable.ic_seaport), ToolItem("Airport", 15, R.drawable.ic_airport), ToolItem("Query", 5, R.drawable.ic_query))
    )
    private val allTools by lazy { toolCategories.values.flatten() }

    data class ToolItem(val label: String, val value: Int, val icon: Int)

    private fun roundedBg(color: Int, radiusDp: Int): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(color)
        d.cornerRadius = radiusDp * resources.displayMetrics.density
        return d
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

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

        // Row 1: city title, play/pause, speed chip, overflow
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

        // Speed chip
        speedChip = Button(this).apply {
            setText(speedNames[speed])
            background = roundedBg(0x1FFFFFFF.toInt(), 12)
            setTextColor(0xFFEEF2F6.toInt())
            setOnClickListener {
                val next = if (speed == 3) 1 else speed + 1
                speed = next
                lastRunSpeed = next
                updateSpeedChipText()
                updatePlayPauseText()
            }
            setPadding(dp(14), dp(8), dp(14), dp(8))
            stateListAnimator = null
            setTextSize(14f)
        }
        row1.addView(speedChip, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { setMargins(8, 0, 0, 0) })

        // Overflow button
        overflowBtn = Button(this).apply {
            setText("⋮")
            background = roundedBg(0x1FFFFFFF.toInt(), 12)
            setTextColor(0xFFEEF2F6.toInt())
            setOnClickListener {
                val pm = PopupMenu(this@MainActivity, it as Button)
                pm.menu.add("New city")
                pm.menu.add("Save city")
                pm.menu.add("Load city")
                pm.menu.add("Budget")
                pm.menu.add("City evaluation")
                pm.menu.add("Tax rate — ${taxRates[taxIdx]}%")
                pm.setOnMenuItemClickListener { item ->
                    when (item.title) {
                        "New city" -> sim.post { MicropolisNative.generateRandomCity(handle) }
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
                        else -> if (item.title.toString().startsWith("Tax")) {
                            taxIdx = (taxIdx + 1) % taxRates.size
                            val t = taxRates[taxIdx]
                            sim.post { MicropolisNative.setCityTax(handle, t) }
                            true
                        } else {
                            false
                        }
                    }
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

        root.addView(
            mapView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

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

        redoBtn = Button(this).apply {
            text = "↷"
            background = roundedBg(0xFF1A222A.toInt(), 18)
            setTextColor(0xFFEEF2F6.toInt())
            stateListAnimator = null
            setTextSize(18f)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setOnClickListener { redo() }
        }
        bottom.addView(redoBtn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { setMargins(dp(8), 0, 0, 0) })

        root.addView(bottom)

        setContentView(root)

        updatePill()
        updateUndoButtons()

        // Set up tap listener
        mapView.onTileTap = { tileX, tileY ->
            val tool = currentTool
            sim.post { MicropolisNative.doTool(handle, tool, tileX, tileY) }
        }
        // One snapshot per build stroke drives Undo/Redo.
        mapView.onStrokeEnd = { built -> if (built) commitSnapshot() }

        // Setup on sim thread
        sim.post {
            handle = MicropolisNative.create()
            MicropolisNative.init(handle)
            MicropolisNative.generateRandomCity(handle)
            // Seed the undo history with the starting city (touch history on main thread).
            ui.post { commitSnapshot() }
        }

        // Start tick loop on sim thread
        sim.post({ tickLoop() })
    }

    private fun updatePill() {
        val ti = allTools.first { it.value == currentTool }
        pillIcon.setImageResource(ti.icon)
        pillName.text = ti.label
    }

    private fun newSnapPath(): String { snapSeq++; return java.io.File(snapDir, "s$snapSeq.cty").absolutePath }

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
        val canRedo = cursor < history.size - 1
        undoBtn.isEnabled = canUndo; undoBtn.alpha = if (canUndo) 1f else 0.35f
        redoBtn.isEnabled = canRedo; redoBtn.alpha = if (canRedo) 1f else 0.35f
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

    private fun updateSpeedChipText() {
        speedChip.text = speedNames[speed]
    }

    private fun tickLoop() {
        repeat(speedTicks[speed]) { MicropolisNative.simTick(handle) }
        MicropolisNative.copyTiles(handle, buf)
        val tilesCopy = buf.copyOf()
        ui.post { mapView.update(tilesCopy) }
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
