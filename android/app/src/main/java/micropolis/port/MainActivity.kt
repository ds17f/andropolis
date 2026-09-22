package micropolis.port

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

/**
 * First light: create the engine, generate a random city, then tick and redraw
 * on a timer. The sim runs on the UI thread for now (it is fast); a dedicated
 * sim thread comes later.
 */
class MainActivity : AppCompatActivity() {

    private var handle: Long = 0L
    private lateinit var mapView: MapView
    private lateinit var buf: ShortArray
    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handle = MicropolisNative.create()
        MicropolisNative.init(handle)
        MicropolisNative.generateRandomCity(handle)

        buf = ShortArray(MicropolisNative.mapWidth() * MicropolisNative.mapHeight())
        mapView = MapView(this)
        setContentView(mapView)

        tickLoop()
    }

    private fun tickLoop() {
        repeat(8) { MicropolisNative.simTick(handle) }
        MicropolisNative.copyTiles(handle, buf)
        mapView.update(buf.copyOf())
        ui.postDelayed({ tickLoop() }, 100)
    }

    override fun onDestroy() {
        super.onDestroy()
        ui.removeCallbacksAndMessages(null)
        if (handle != 0L) {
            MicropolisNative.destroy(handle)
            handle = 0L
        }
    }
}
