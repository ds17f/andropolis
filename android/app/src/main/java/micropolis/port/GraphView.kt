package micropolis.port

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Draws several normalised line series (each a colour + 120 samples). */
class GraphView(context: Context) : View(context) {
    private var series: List<Pair<Int, IntArray>> = emptyList()
    private val line = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 3f }
    private val grid = Paint().apply { color = 0x1FFFFFFF; style = Paint.Style.STROKE; strokeWidth = 1f }
    private val path = Path()

    fun setSeries(s: List<Pair<Int, IntArray>>) { series = s; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        for (i in 0..4) { val y = h * i / 4f; canvas.drawLine(0f, y, w, y, grid) }   // 4 grid rows
        var maxV = 1
        for ((_, d) in series) for (v in d) if (v > maxV) maxV = v
        for ((color, d) in series) {
            if (d.isEmpty()) continue
            line.color = color
            path.reset()
            for (i in d.indices) {
                val x = if (d.size > 1) w * i / (d.size - 1) else 0f
                val y = h - (d[i].coerceAtLeast(0).toFloat() / maxV) * h
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, line)
        }
    }
}
