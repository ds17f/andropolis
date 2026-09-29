package micropolis.port

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Overlay intensity 0..255 -> ARGB heat colour (transparent at 0, green→yellow→red as it rises). */
internal val HEAT_LUT = IntArray(256) { i ->
    if (i == 0) 0 else {
        val t = i / 255f
        val r = (255 * kotlin.math.min(1f, t * 2f)).toInt()
        val g = (255 * kotlin.math.min(1f, (1f - t) * 2f)).toInt()
        val a = (60 + 140 * t).toInt().coerceIn(0, 200)
        (a shl 24) or (r shl 16) or (g shl 8)
    }
}

/** Scale end labels (low, high) per overlay kind; the index is the overlay kind. Empty low == on/off overlay. */
internal val OVERLAY_SCALE = arrayOf(
    "" to "",                   // 0 Off
    "Sparse" to "Dense",        // 1 Population
    "Light" to "Heavy",         // 2 Traffic
    "Clean" to "Polluted",      // 3 Pollution
    "Low" to "High",            // 4 Land value
    "Low" to "High",            // 5 Crime
    "Shrinking" to "Growing",   // 6 Growth
    "" to "Powered",            // 7 Power
    "Weak" to "Strong",         // 8 Fire
    "Weak" to "Strong"          // 9 Police
)

/** Key over the map: overlay name, colour bar, low/high labels. Hidden when kind == 0. */
internal class OverlayLegend(ctx: Context) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6202A36.toInt() }
    private val bar = Paint()
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEEF2F6.toInt(); textSize = 12f * d; isFakeBoldText = true
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9AA7B4.toInt(); textSize = 11f * d }
    private val r = RectF()
    private var kind = 0
    private var name = ""

    init { visibility = GONE }

    /** Show the key for this overlay kind, or hide it when kind == 0. */
    fun show(kind: Int, name: String) {
        this.kind = kind; this.name = name
        visibility = if (kind == 0) GONE else VISIBLE
        invalidate()
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension((150 * d).toInt(), (58 * d).toInt())

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val pad = 10 * d
        r.set(0f, 0f, w, height.toFloat())
        canvas.drawRoundRect(r, 10 * d, 10 * d, bg)
        canvas.drawText(name, pad, 18 * d, title)
        val (lo, hi) = OVERLAY_SCALE.getOrElse(kind) { "Low" to "High" }
        val top = 24 * d; val bottom = 34 * d; val span = w - 2 * pad
        if (lo.isEmpty()) {
            // On/off overlay (Power): one swatch of the full-intensity colour
            bar.color = HEAT_LUT[255] or (0xFF shl 24)
            canvas.drawRect(pad, top, pad + span, bottom, bar)
        } else {
            val n = 64
            for (i in 0 until n) {
                bar.color = HEAT_LUT[1 + i * 254 / (n - 1)] or (0xFF shl 24)
                canvas.drawRect(pad + span * i / n, top, pad + span * (i + 1) / n + 1f, bottom, bar)
            }
        }
        label.textAlign = Paint.Align.LEFT; canvas.drawText(lo, pad, 50 * d, label)
        label.textAlign = Paint.Align.RIGHT; canvas.drawText(hi, w - pad, 50 * d, label)
    }
}
