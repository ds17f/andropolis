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
            "Bulldozer" to 7,
            "Road" to 9,
            "Rail" to 8,
            "Wire" to 6,
            "Residential" to 0,
            "Commercial" to 1,
            "Industrial" to 2,
            "Police" to 4,
            "Fire" to 3,
            "Park" to 11
        )

        val barLayout = LinearLayout(this)
        barLayout.orientation = LinearLayout.HORIZONTAL
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
        repeat(8) { MicropolisNative.simTick(handle) }
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
