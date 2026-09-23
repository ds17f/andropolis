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

/** Whole-map overview: a downscaled colour image of the tiles + a viewport rectangle. */
class MinimapView(context: Context) : View(context) {
    private val cols = MicropolisNative.mapWidth()
    private val rows = MicropolisNative.mapHeight()
    private val mini = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
    private val pixels = IntArray(cols * rows)
    private val lut = IntArray(1024)
    private val srcRect = Rect(0, 0, cols, rows)
    private val dstRect = RectF()
    private val bmpPaint = Paint().apply { isFilterBitmap = true }
    private val frame = Paint().apply { color = 0xFFF5A623.toInt(); style = Paint.Style.STROKE; strokeWidth = 3f }
    private val border = Paint().apply { color = 0xFF0B0E12.toInt(); style = Paint.Style.STROKE; strokeWidth = 4f }
    private var vpL = 0f; private var vpT = 0f; private var vpR = 0f; private var vpB = 0f
    var onTileSelected: ((Int, Int) -> Unit)? = null

    init {
        // representative colour per tile = centre pixel of its 16x16 atlas cell
        val atlas = BitmapFactory.decodeStream(context.assets.open("tiles.png"))
        for (idx in 0 until 960) {
            val c = idx % 16; val r = idx / 16
            lut[idx] = atlas.getPixel(c * 16 + 8, r * 16 + 8)
        }
        atlas.recycle()
    }

    fun update(tiles: ShortArray) {
        for (x in 0 until cols) {
            val base = x * rows
            for (y in 0 until rows) {
                val idx = tiles[base + y].toInt() and 0x03FF
                pixels[y * cols + x] = lut[if (idx >= 960) 0 else idx]
            }
        }
        mini.setPixels(pixels, 0, cols, 0, 0, cols, rows)
        postInvalidate()
    }

    /** Visible region in TILE coords, from MapView. Dedup to avoid redraw churn. */
    fun setViewport(l: Float, t: Float, r: Float, b: Float) {
        if (l == vpL && t == vpT && r == vpR && b == vpB) return
        vpL = l; vpT = t; vpR = r; vpB = b; postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        dstRect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawBitmap(mini, srcRect, dstRect, bmpPaint)
        val sx = width.toFloat() / cols; val sy = height.toFloat() / rows
        canvas.drawRect(vpL * sx, vpT * sy, vpR * sx, vpB * sy, frame)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), border)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val tx = (event.x / width * cols).toInt().coerceIn(0, cols - 1)
                val ty = (event.y / height * rows).toInt().coerceIn(0, rows - 1)
                onTileSelected?.invoke(tx, ty)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
