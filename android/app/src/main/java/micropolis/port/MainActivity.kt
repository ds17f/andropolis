package micropolis.port

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

/**
 * First light: create the engine, generate a random city, then tick and redraw
 * on a timer. The sim runs on a dedicated thread; touch events are marshaled
 * to that thread as messages.
 */
class MainActivity : AppCompatActivity() {

    private var handle: Long = 0L
    private lateinit var mapView: MapView
    private lateinit var buf: ShortArray
    private lateinit var sim: Handler
    private lateinit var ui: Handler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create sim thread with handler
        val simThread = HandlerThread("sim")
        simThread.start()
        sim = Handler(simThread.looper)
        ui = Handler(Looper.getMainLooper())

        buf = ShortArray(MicropolisNative.mapWidth() * MicropolisNative.mapHeight())
        mapView = MapView(this)
        setContentView(mapView)

        // Set up tap listener
        mapView.onTileTap = { tileX, tileY ->
            sim.post {
                MicropolisNative.doTool(handle, 11, tileX, tileY)
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
