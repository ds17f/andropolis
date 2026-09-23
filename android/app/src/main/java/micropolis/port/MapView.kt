package micropolis.port

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
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
    var onStrokeEnd: ((built: Boolean) -> Unit)? = null
    
    private var scale = 1f
    private var panX = 0f
    private var panY = 0f
    private var tileSize = 0f
    private var panning = false
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var lastBuiltTile: Pair<Int, Int>? = null
    private var built = false
    
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())

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
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                built = false
                lastBuiltTile = null
                buildAt(event.x, event.y)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                built = false
                panning = true
                lastBuiltTile = null
                lastFocusX = focusX(event)
                lastFocusY = focusY(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val fx = focusX(event)
                    val fy = focusY(event)
                    panX += fx - lastFocusX
                    panY += fy - lastFocusY
                    lastFocusX = fx
                    lastFocusY = fy
                    clampPan()
                    invalidate()
                } else if (!panning) {
                    buildAt(event.x, event.y)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                panning = false
                lastBuiltTile = null
                onStrokeEnd?.invoke(built)
                performClick()
            }
        }
        return true
    }

    private fun focusX(e: MotionEvent): Float {
        var s = 0f
        for (i in 0 until e.pointerCount) s += e.getX(i)
        return s / e.pointerCount
    }

    private fun focusY(e: MotionEvent): Float {
        var s = 0f
        for (i in 0 until e.pointerCount) s += e.getY(i)
        return s / e.pointerCount
    }

    private fun buildAt(px: Float, py: Float) {
        val tileX = (((px - panX) / scale) / tileSize).toInt().coerceIn(0, cols - 1)
        val tileY = (((py - panY) / scale) / tileSize).toInt().coerceIn(0, rows - 1)
        if (lastBuiltTile?.let { it.first == tileX && it.second == tileY } != true) {
            lastBuiltTile = Pair(tileX, tileY)
            onTileTap?.invoke(tileX, tileY)
            built = true
        }
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
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val newScale = (scale * d.scaleFactor).coerceIn(1f, 8f)
            val f = newScale / scale
            panX = d.focusX - (d.focusX - panX) * f
            panY = d.focusY - (d.focusY - panY) * f
            scale = newScale
            clampPan()
            invalidate()
            return true
        }
    }
}
