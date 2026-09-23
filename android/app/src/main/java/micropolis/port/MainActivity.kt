package micropolis.port

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupMenu
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
    private var buildMode = true
    @Volatile private var speed = 2   // 0=Pause 1=Slow 2=Med 3=Fast
    private var lastRunSpeed = 2
    private val speedNames = arrayOf("Pause", "Slow", "Med", "Fast")
    private val speedTicks = intArrayOf(0, 2, 8, 20)
    private val taxRates = intArrayOf(0, 5, 7, 9, 12, 15, 20)
    private var taxIdx = 2   // start at 7%
    private val savePath by lazy { java.io.File(filesDir, "city.cty").absolutePath }
    private var previewMode = false
    private val pending = mutableListOf<Triple<Int, Int, Int>>()  // x, y, tool
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
        }
        row1.addView(playPauseBtn)

        // Speed chip
        speedChip = Button(this).apply {
            setText(speedNames[speed])
            setOnClickListener {
                val next = if (speed == 3) 1 else speed + 1
                speed = next
                lastRunSpeed = next
                updateSpeedChipText()
                updatePlayPauseText()
            }
        }
        row1.addView(speedChip)

        // Overflow button
        overflowBtn = Button(this).apply {
            setText("⋮")
            setOnClickListener {
                val pm = PopupMenu(this@MainActivity, it as Button)
                pm.menu.add("New city")
                pm.menu.add("Save city")
                pm.menu.add("Load city")
                pm.menu.add("Budget")
                pm.menu.add("City evaluation")
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
                    }
                    true
                }
                pm.show()
            }
        }
        row1.addView(overflowBtn)
        topBar.addView(row1)

        // Row 2: HUD chips (Funds, Population, Score)
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        // Funds chip
        fundsChip = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0x14FFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            setPadding(8, 6, 8, 6)
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
            setBackgroundColor(0x14FFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            setPadding(8, 6, 8, 6)
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
            setBackgroundColor(0x14FFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            setPadding(8, 6, 8, 6)
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

        val tools = listOf(
            "Bulldozer" to 7, "Road" to 9, "Rail" to 8, "Wire" to 6, "Park" to 11,
            "Resid" to 0, "Comm" to 1, "Ind" to 2, "Police" to 4, "Fire" to 3,
            "Stadium" to 10, "Seaport" to 12, "Airport" to 15,
            "Coal" to 13, "Nuclear" to 14, "Query" to 5
        )

        val barLayout = LinearLayout(this)
        barLayout.orientation = LinearLayout.HORIZONTAL

        // Tool buttons (collected for highlighting)
        val toolButtons = mutableListOf<Button>()

        // Mode toggle button (first in bar)
        val modeButton = Button(this).apply {
            text = "Build"
            setOnClickListener {
                buildMode = !buildMode
                mapView.buildEnabled = buildMode
                text = if (buildMode) "Build" else "Move"
            }
        }
        barLayout.addView(modeButton)

        val taxBtn = Button(this).apply {
            text = "Tax: ${taxRates[taxIdx]}%"
            setOnClickListener {
                taxIdx = (taxIdx + 1) % taxRates.size
                text = "Tax: ${taxRates[taxIdx]}%"
                val t = taxRates[taxIdx]
                sim.post { MicropolisNative.setCityTax(handle, t) }
            }
        }
        barLayout.addView(taxBtn)

        val previewBtn = Button(this).apply {
            text = "Preview: Off"
            setOnClickListener {
                previewMode = !previewMode
                text = if (previewMode) "Preview: On" else "Preview: Off"
            }
        }
        val confirmBtn = Button(this).apply {
            text = "Confirm"
            setOnClickListener {
                val snapshot = pending.toList()
                pending.clear(); mapView.setPendingTiles(emptyList())
                sim.post { for (t in snapshot) MicropolisNative.doTool(handle, t.third, t.first, t.second) }
            }
        }
        val cancelBtn = Button(this).apply {
            text = "Cancel"
            setOnClickListener { pending.clear(); mapView.setPendingTiles(emptyList()) }
        }
        barLayout.addView(previewBtn)
        barLayout.addView(confirmBtn)
        barLayout.addView(cancelBtn)

        for ((label, value) in tools) {
            val button = Button(this).apply {
                text = label
                setOnClickListener {
                    currentTool = value
                    highlightTool(this, toolButtons)
                }
            }
            toolButtons.add(button)
            barLayout.addView(button)
            if (value == currentTool) button.post { highlightTool(button, toolButtons) }
        }

        val scroll = HorizontalScrollView(this)
        scroll.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        scroll.addView(barLayout)
        root.addView(scroll)

        setContentView(root)

        // Set up tap listener
        mapView.onTileTap = { tileX, tileY ->
            if (previewMode) {
                pending.add(Triple(tileX, tileY, currentTool))
                mapView.setPendingTiles(pending.map { it.first to it.second })
            } else {
                val tool = currentTool
                sim.post { MicropolisNative.doTool(handle, tool, tileX, tileY) }
            }
        }

        // Setup on sim thread
        sim.post {
            handle = MicropolisNative.create()
            MicropolisNative.init(handle)
            MicropolisNative.generateRandomCity(handle)
        }

        // Start tick loop on sim thread
        sim.post({ tickLoop() })
    }

    private fun highlightTool(selected: android.widget.Button, all: List<android.widget.Button>) {
        for (b in all) b.setBackgroundColor(0xFF666666.toInt())   // unselected gray
        selected.setBackgroundColor(0xFF2E7D32.toInt())           // selected green
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
            fundsValue.text = "$\$$funds"
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
