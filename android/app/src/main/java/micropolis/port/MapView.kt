package micropolis.port

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
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
    var buildEnabled: Boolean = true

    private var scale = 1f
    private var panX = 0f
    private var panY = 0f
    private var tileSize = 0f
    
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureListener = GestureListener()
    private val gestureDetector = GestureDetector(context, gestureListener)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        tileSize = w.toFloat() / cols
        clampPan()
        invalidate()
    }

    fun update(newTiles: ShortArray) {
        tiles = newTiles
        postInvalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    private fun clampPan() {
        val mapW = tileSize * cols * scale
        if (mapW <= width) {
            panX = (width - mapW) / 2f
        } else {
            panX = panX.coerceIn(width - mapW, 0f)
        }
        
        val mapH = tileSize * rows * scale
        if (mapH <= height) {
            panY = (height - mapH) / 2f
        } else {
            panY = panY.coerceIn(height - mapH, 0f)
        }
    }

    private fun convertToTile(e: MotionEvent): Pair<Int, Int> {
        val worldX = (e.x - panX) / scale
        val worldY = (e.y - panY) / scale
        val tileX = (worldX / tileSize).toInt().coerceIn(0, cols - 1)
        val tileY = (worldY / tileSize).toInt().coerceIn(0, rows - 1)
        return Pair(tileX, tileY)
    }

    override fun onDraw(canvas: Canvas) {
        tileSize = width.toFloat() / cols
        
        canvas.save()
        canvas.translate(panX, panY)
        canvas.scale(scale, scale)
        
        for (x in 0 until cols) {
            val base = x * rows
            for (y in 0 until rows) {
                val tileIdx = tiles[base + y].toInt() and 0x03FF
                val idx = if (tileIdx >= 960) 0 else tileIdx
                val col = idx % 16
                val row = idx / 16
                srcRect.set(col * 16, row * 16, col * 16 + 16, row * 16 + 16)
                dstRect.set(x * tileSize, y * tileSize, (x + 1) * tileSize, (y + 1) * tileSize)
                canvas.drawBitmap(atlas, srcRect, dstRect, paint)
            }
        }
        
        canvas.restore()
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scale = (scale * detector.scaleFactor).coerceIn(1f, 8f)
            clampPan()
            invalidate()
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            panX -= dx
            panY -= dy
            clampPan()
            invalidate()
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (buildEnabled) {
                val (tileX, tileY) = convertToTile(e)
                onTileTap?.invoke(tileX, tileY)
            }
            performClick()
            return true
        }
    }
}
