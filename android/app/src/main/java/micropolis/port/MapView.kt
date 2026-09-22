package micropolis.port

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/**
 * First-light renderer: draws the 120x100 tile map as colored cells. The palette
 * is a rough approximation by tile-index range, enough to see land, water, and
 * built structure. Real tile bitmaps come later.
 */
class MapView(context: Context) : View(context) {
    private val cols = MicropolisNative.mapWidth()
    private val rows = MicropolisNative.mapHeight()
    private var tiles = ShortArray(cols * rows)
    private val paint = Paint()

    var onTileTap: ((Int, Int) -> Unit)? = null

    fun update(newTiles: ShortArray) {
        tiles = newTiles
        postInvalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val tileX = (event.x / (width.toFloat() / cols)).toInt().coerceIn(0, cols - 1)
            val tileY = (event.y / (height.toFloat() / rows)).toInt().coerceIn(0, rows - 1)
            onTileTap?.invoke(tileX, tileY)
            performClick()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        val cw = width.toFloat() / cols
        val ch = height.toFloat() / rows
        for (x in 0 until cols) {
            val base = x * rows
            for (y in 0 until rows) {
                paint.color = colorFor(tiles[base + y].toInt() and 0x03FF)
                canvas.drawRect(x * cw, y * ch, (x + 1) * cw, (y + 1) * ch, paint)
            }
        }
    }

    private fun colorFor(tile: Int): Int = when {
        tile == 0 -> 0xFF5A8F3A.toInt()        // DIRT -> green
        tile in 2..20 -> 0xFF2E6FB0.toInt()    // river/water -> blue
        tile in 21..43 -> 0xFF2E8B57.toInt()   // woods -> dark green
        tile in 44..47 -> 0xFF7A7A7A.toInt()   // roads-ish -> gray
        else -> {                              // hash so structure is visible
            val r = (tile * 37) and 0xFF
            val g = (tile * 59) and 0xFF
            val b = (tile * 17) and 0xFF
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}
