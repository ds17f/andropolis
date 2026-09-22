package micropolis.port

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * Renderer: draws the 120x100 tile map using the real tile atlas.
 * The atlas is a 256×960 PNG with 16×16 tiles in a 16-column, 60-row grid.
 */
class MapView(context: Context) : View(context) {
    private val cols = MicropolisNative.mapWidth()
    private val rows = MicropolisNative.mapHeight()
    private var tiles = ShortArray(cols * rows)
    private val paint = Paint().apply { isFilterBitmap = false }
    
    private val atlas: Bitmap = BitmapFactory.decodeStream(context.assets.open("tiles.png"))
    private val srcRect = Rect()
    private val dstRect = RectF()

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
                val tileIdx = tiles[base + y].toInt() and 0x03FF
                val idx = if (tileIdx >= 960) 0 else tileIdx
                val col = idx % 16
                val row = idx / 16
                srcRect.set(col * 16, row * 16, col * 16 + 16, row * 16 + 16)
                dstRect.set(x * cw, y * ch, (x + 1) * cw, (y + 1) * ch)
                canvas.drawBitmap(atlas, srcRect, dstRect, paint)
            }
        }
    }
}
