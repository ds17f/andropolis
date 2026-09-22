package micropolis.port

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
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
    private lateinit var hud: android.widget.TextView
    private lateinit var buf: ShortArray
    private lateinit var sim: Handler
    private lateinit var ui: Handler
    private val statsBuf = IntArray(10)
    private var buildMode = true
    @Volatile private var speed = 2   // 0=Pause 1=Slow 2=Med 3=Fast
    private val speedNames = arrayOf("Pause", "Slow", "Med", "Fast")
    private val speedTicks = intArrayOf(0, 2, 8, 20)
    private val savePath by lazy { java.io.File(filesDir, "city.cty").absolutePath }

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

        hud = android.widget.TextView(this).apply {
            setBackgroundColor(0xCC000000.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(24, 16, 24, 16)
        }
        root.addView(hud, LinearLayout.LayoutParams(
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

        // New, Save, Load buttons (after mode toggle, before tools)
        val newBtn = Button(this).apply {
            text = "New"
            setOnClickListener { sim.post { MicropolisNative.generateRandomCity(handle) } }
        }
        val saveBtn = Button(this).apply {
            text = "Save"
            setOnClickListener { sim.post { MicropolisNative.saveCity(handle, savePath) } }
        }
        val loadBtn = Button(this).apply {
            text = "Load"
            setOnClickListener { sim.post { MicropolisNative.loadCity(handle, savePath) } }
        }
        barLayout.addView(newBtn)
        barLayout.addView(saveBtn)
        barLayout.addView(loadBtn)

        val speedBtn = Button(this).apply {
            text = "Speed: ${speedNames[speed]}"
            setOnClickListener {
                speed = (speed + 1) % 4
                text = "Speed: ${speedNames[speed]}"
            }
        }
        barLayout.addView(speedBtn)

        for ((label, value) in tools) {
            val button = Button(this).apply {
                text = label
                setOnClickListener { currentTool = value }
            }
            barLayout.addView(button)
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
            val tool = currentTool
            sim.post {
                MicropolisNative.doTool(handle, tool, tileX, tileY)
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
        val text = "Funds: \$$funds    $monthName $year   Pop: $pop   Score: $score"
        ui.post { hud.text = text }
        sim.postDelayed({ tickLoop() }, 100)
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
