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
    var onStrokeStart: (() -> Unit)? = null
    var onViewportChanged: ((Float, Float, Float, Float) -> Unit)? = null
    var onUserNavigate: (() -> Unit)? = null
    
    var toolFootprint: Int = 1            // set by MainActivity; >1 == place-on-lift
    private var ghostX = -1               // tile coords of the ghost anchor; -1 == no ghost
    private var ghostY = -1
    private val ghostFill = Paint().apply { color = 0x55F5A623; style = Paint.Style.FILL }
    private val ghostStroke = Paint().apply {
        color = 0xFFF5A623.toInt(); style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true
    }
    
    private var scale = 1f
    private var panX = 0f
    private var panY = 0f
    private var tileSize = 0f
    private var fillOnLayout = false
    private var panning = false
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var lastBuiltTile: Pair<Int, Int>? = null
    private var built = false

    // Overlay state + heatmap LUT
    private var overlayMode = 0                 // 0 == off
    private var overlayData: ByteArray? = null
    private val overlayPaint = Paint()
    // intensity 0..255 -> ARGB heat colour (transparent at 0, green→yellow→red as it rises)
    private val heatLut = IntArray(256) { i ->
        if (i == 0) 0 else {
            val t = i / 255f
            val r = (255 * kotlin.math.min(1f, t * 2f)).toInt()
            val g = (255 * kotlin.math.min(1f, (1f - t) * 2f)).toInt()
            val a = (60 + 140 * t).toInt().coerceIn(0, 200)
            (a shl 24) or (r shl 16) or (g shl 8)
        }
    }

    fun setOverlay(mode: Int, data: ByteArray?) {
        overlayMode = mode
        overlayData = data
        postInvalidate()
    }

    fun centerOnTile(tx: Int, ty: Int) {
        panX = width / 2f - (tx + 0.5f) * tileSize * scale
        panY = height / 2f - (ty + 0.5f) * tileSize * scale
        clampPan(); invalidate()
    }

    /** Zoom so the map fills the whole view (no bands), centred. Safe to call before layout. */
    fun zoomToFill() {
        if (width == 0 || height == 0) { fillOnLayout = true; return }
        tileSize = width.toFloat() / cols
        if (!navLocked) scale = maxOf(1f, height / (tileSize * rows)).coerceIn(1f, 8f)
        panX = (width - tileSize * cols * scale) / 2f
        panY = (height - tileSize * rows * scale) / 2f
        clampPan(); invalidate()
    }

    /** Centre on a tile, zooming in to at least `minScale` (unless navigation is locked). */
    fun zoomToTile(tx: Int, ty: Int, minScale: Float = 3f) {
        if (!navLocked && scale < minScale) scale = minScale.coerceIn(1f, 8f)
        centerOnTile(tx, ty)
    }

    private var sprites = IntArray(0)          // [type, frame, left, top] * spriteCount
    private var spriteCount = 0
    private val spriteBitmaps = HashMap<String, Bitmap?>()   // asset name -> bitmap (null = missing)

    /** Current view as [panX, panY, scale]. */
    fun saveView(): FloatArray = floatArrayOf(panX, panY, scale)

    /** Restore a view captured by saveView(). */
    fun restoreView(v: FloatArray) {
        if (v.size < 3) return
        panX = v[0]; panY = v[1]; scale = v[2]
        clampPan(); invalidate()
    }

    // Build-gesture state. The first touch is DEFERRED: a lone finger that turns into a
    // two-finger pan must not lay a tile, so a tap builds on UP and a drag builds on MOVE.
    private var pendingDown = false        // one finger down, not yet built (tap vs pan undecided)
    private var downX = 0f
    private var downY = 0f
    private var anchorX = -1               // stroke start tile (straight-line anchor)
    private var anchorY = -1
    private var axisLock = 0               // 0 undecided, 1 horizontal, 2 vertical
    private val strokeBuilt = HashSet<Long>()
    var straightLineTool = false           // set by MainActivity for road/rail/wire
    var tapOnlyTool = false                 // e.g. Query: act on a clean tap, never on drag/pinch
    var moveMode = false                    // no tool: one finger pans, nothing is built
    private var refocus = false             // a finger went up mid-pan: re-read the focus, don't jump
    private var navLocked = false          // minimap-navigation mode: fixed zoom, no pinch/pan

    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())

    /** Lock to a fixed zoom and disable pinch/two-finger pan (minimap navigation), or unlock. */
    fun setNavLocked(locked: Boolean, fixedScale: Float) {
        navLocked = locked
        if (locked) { scale = fixedScale; clampPan(); invalidate() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        tileSize = w.toFloat() / cols
        if (fillOnLayout) { fillOnLayout = false; zoomToFill() }
        clampPan()
        invalidate()
    }

    fun update(newTiles: ShortArray) {
        tiles = newTiles
        postInvalidate()
    }

    /** New sprite list from the sim (see MicropolisNative.copySprites). */
    fun updateSprites(data: IntArray, count: Int) {
        sprites = data; spriteCount = count
        invalidate()
    }

    private fun spriteBitmap(type: Int, frame: Int): Bitmap? {
        val name = "sprites/sprite_${type}_${frame - 1}.png"
        return spriteBitmaps.getOrPut(name) {
            try { context.assets.open(name).use { BitmapFactory.decodeStream(it) } } catch (e: Exception) { null }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!navLocked) scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                onStrokeStart?.invoke()
                built = false
                lastBuiltTile = null
                strokeBuilt.clear()
                axisLock = 0
                if (moveMode) {
                    pendingDown = false
                    lastFocusX = event.x
                    lastFocusY = event.y
                } else if (toolFootprint > 1) {
                    ghostX = tileXat(event.x)
                    ghostY = tileYat(event.y)
                    pendingDown = false
                    invalidate()
                } else {
                    // Defer: don't build yet — this may become a two-finger pan.
                    pendingDown = true
                    downX = event.x
                    downY = event.y
                    anchorX = tileXat(event.x)
                    anchorY = tileYat(event.y)
                }
            }
            // A second finger always cancels the build stroke. In minimap-nav mode it
            // then does nothing at all (the pan branch below is gated off), so two-finger
            // touches never place objects.
            MotionEvent.ACTION_POINTER_DOWN -> {
                built = false
                panning = true
                pendingDown = false      // cancel the tentative tap: no stray tile
                lastBuiltTile = null
                ghostX = -1
                ghostY = -1
                invalidate()
                lastFocusX = focusX(event)
                lastFocusY = focusY(event)
                if (!navLocked) onUserNavigate?.invoke()
            }
            MotionEvent.ACTION_POINTER_UP -> refocus = true
            MotionEvent.ACTION_MOVE -> {
                if (refocus) {
                    lastFocusX = focusX(event); lastFocusY = focusY(event); refocus = false
                } else if ((event.pointerCount >= 2 || moveMode) && !navLocked) {
                    if (moveMode && event.pointerCount == 1 && !panning) {
                        // one-finger pan starts after a small slop, so a tap stays a tap
                        if (kotlin.math.hypot(event.x - lastFocusX, event.y - lastFocusY) < 12f) return true
                        panning = true
                        onUserNavigate?.invoke()
                    }
                    val fx = focusX(event)
                    val fy = focusY(event)
                    panX += fx - lastFocusX
                    panY += fy - lastFocusY
                    lastFocusX = fx
                    lastFocusY = fy
                    clampPan()
                    invalidate()
                } else if (!panning) {
                    if (toolFootprint > 1) {
                        ghostX = tileXat(event.x)
                        ghostY = tileYat(event.y)
                        invalidate()
                    } else if (tapOnlyTool) {
                        // Query acts only on a clean tap: sliding off the tile cancels it.
                        if (tileXat(event.x) != anchorX || tileYat(event.y) != anchorY) pendingDown = false
                    } else {
                        pendingDown = false      // committed to a drag
                        val curX = tileXat(event.x)
                        val curY = tileYat(event.y)
                        if (straightLineTool) {
                            // Lock to the dominant axis on first movement; keep it straight.
                            if (axisLock == 0 && (curX != anchorX || curY != anchorY)) {
                                axisLock = if (kotlin.math.abs(curX - anchorX) >= kotlin.math.abs(curY - anchorY)) 1 else 2
                            }
                            val endX = if (axisLock == 2) anchorX else curX
                            val endY = if (axisLock == 1) anchorY else curY
                            buildLine(anchorX, anchorY, endX, endY)
                        } else {
                            buildAt(event.x, event.y)
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (toolFootprint > 1 && !panning && ghostX >= 0) {
                    onTileTap?.invoke(ghostX, ghostY)
                    built = true
                } else if (pendingDown && !panning && event.actionMasked == MotionEvent.ACTION_UP) {
                    // A tap on a line/continuous tool: build the single tile under the finger.
                    buildTile(tileXat(downX), tileYat(downY))
                }
                ghostX = -1
                ghostY = -1
                panning = false
                refocus = false
                pendingDown = false
                axisLock = 0
                lastBuiltTile = null
                strokeBuilt.clear()
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

    /** Build one tile at most once per stroke (dedup avoids re-posting doTool while dragging). */
    private fun buildTile(tx: Int, ty: Int) {
        val key = ty.toLong() * cols + tx
        if (strokeBuilt.add(key)) {
            onTileTap?.invoke(tx, ty)
            built = true
        }
    }

    /** Build an axis-aligned straight line of tiles from (x0,y0) to (x1,y1). */
    private fun buildLine(x0: Int, y0: Int, x1: Int, y1: Int) {
        if (y0 == y1) {
            val step = if (x1 >= x0) 1 else -1
            var x = x0
            while (true) { buildTile(x, y0); if (x == x1) break; x += step }
        } else {
            val step = if (y1 >= y0) 1 else -1
            var y = y0
            while (true) { buildTile(x0, y); if (y == y1) break; y += step }
        }
    }

    private fun tileXat(px: Float) = (((px - panX) / scale) / tileSize).toInt().coerceIn(0, cols - 1)
    private fun tileYat(py: Float) = (((py - panY) / scale) / tileSize).toInt().coerceIn(0, rows - 1)

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
        
        // Draw overlay heatmap tint
        val ov = overlayData
        if (overlayMode != 0 && ov != null && ov.size >= cols * rows) {
            for (x in 0 until cols) {
                val base = x * rows
                for (y in 0 until rows) {
                    val v = ov[base + y].toInt() and 0xFF
                    if (v == 0) continue
                    overlayPaint.color = heatLut[v]
                    dstRect.set(x * tileSize, y * tileSize, (x + 1) * tileSize, (y + 1) * tileSize)
                    canvas.drawRect(dstRect, overlayPaint)
                }
            }
        }
        
        // Draw sprites (moving objects) in map space, over tiles and overlay
        val px = tileSize / 16f                       // screen units per world pixel
        for (i in 0 until spriteCount) {
            val bmp = spriteBitmap(sprites[i * 4], sprites[i * 4 + 1]) ?: continue
            val left = sprites[i * 4 + 2] * px
            val top = sprites[i * 4 + 3] * px
            dstRect.set(left, top, left + bmp.width * px, top + bmp.height * px)
            canvas.drawBitmap(bmp, null, dstRect, paint)
        }
        
        canvas.restore()
        
        // Draw ghost for place-on-lift tools
        if (toolFootprint > 1 && ghostX >= 0) {
            val n = toolFootprint
            val tlx = ghostX - 1
            val tly = ghostY - 1
            val left = panX + tlx * tileSize * scale
            val top  = panY + tly * tileSize * scale
            val side = n * tileSize * scale
            val r = RectF(left, top, left + side, top + side)
            canvas.drawRect(r, ghostFill)
            canvas.drawRect(r, ghostStroke)
        }
        
        // Report visible region in tile coords
        onViewportChanged?.invoke(
            (-panX / scale) / tileSize,
            (-panY / scale) / tileSize,
            ((width - panX) / scale) / tileSize,
            ((height - panY) / scale) / tileSize
        )
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
