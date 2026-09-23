package micropolis.port

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

// Per-build undo/redo: each stroke records the tiles it changed and its cost (task 052).
// Extension functions on MainActivity; state lives in MainActivity.kt.

/** Record the stroke just finished: diff its box (+1 tile for re-shaped neighbours) and its cost. */
internal fun MainActivity.commitBuild() {
    if (strokeMaxX < 0) return
    val bx0 = maxOf(0, strokeMinX - 1); val by0 = maxOf(0, strokeMinY - 1)
    val bx1 = minOf(119, strokeMaxX + 1); val by1 = minOf(99, strokeMaxY + 1)
    sim.post {   // FIFO: runs after the stroke's doTool calls
        val after = ShortArray(120 * 100)
        MicropolisNative.copyTiles(handle, after)
        val s = IntArray(10); MicropolisNative.getStats(handle, s)
        val cost = (strokeFundsBefore - s[1]).coerceAtLeast(0)
        val idx = ArrayList<Int>()
        for (x in bx0..bx1) for (y in by0..by1) { val i = x * 100 + y; if (strokeBefore[i] != after[i]) idx.add(i) }
        if (idx.isEmpty()) return@post
        val edit = BuildEdit(idx.toIntArray(),
            ShortArray(idx.size) { strokeBefore[idx[it]] }, ShortArray(idx.size) { after[idx[it]] }, cost)
        ui.post {
            undoStack.addLast(edit)
            while (undoStack.size > undoCap) undoStack.removeFirst()
            redoStack.clear()
            updateUndoButtons()
        }
    }
}

/** Put back the tiles of the last build and refund it. Time and growth elsewhere are untouched. */
internal fun MainActivity.undo() {
    val e = undoStack.removeLastOrNull() ?: return
    redoStack.addLast(e); updateUndoButtons()
    sim.post {
        for (k in e.idx.indices) { val i = e.idx[k]; MicropolisNative.setTile(handle, i / 100, i % 100, e.before[k].toInt() and 0xFFFF) }
        val s = IntArray(10); MicropolisNative.getStats(handle, s)
        MicropolisNative.setFunds(handle, s[1] + e.cost)
    }
}

internal fun MainActivity.redo() {
    val e = redoStack.removeLastOrNull() ?: return
    undoStack.addLast(e); updateUndoButtons()
    sim.post {
        for (k in e.idx.indices) { val i = e.idx[k]; MicropolisNative.setTile(handle, i / 100, i % 100, e.after[k].toInt() and 0xFFFF) }
        val s = IntArray(10); MicropolisNative.getStats(handle, s)
        MicropolisNative.setFunds(handle, s[1] - e.cost)
    }
}

internal fun MainActivity.updateUndoButtons() {
    val canUndo = undoStack.isNotEmpty()
    undoBtn.isEnabled = canUndo; undoBtn.alpha = if (canUndo) 1f else 0.35f
}

/** Drop the undo/redo history (new city or loaded city). */
internal fun MainActivity.resetHistory() {
    undoStack.clear(); redoStack.clear()
    updateUndoButtons()
}
