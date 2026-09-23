package micropolis.port

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

// PLACEHOLDER (task 062 replaces this file): the Material bottom bar and the tool FAB.

/** The bottom bar: Simulation, City, Overlay, Undo. Must set simState, overlayState and undoBtn. */
internal fun MainActivity.buildBottomBar(): View {
    val bar = LinearLayout(this)
    simState = TextView(this).apply { setOnClickListener { showSimulationPanel() } }
    overlayState = TextView(this).apply { setOnClickListener { showOverlayPanel() } }
    undoBtn = TextView(this).apply { text = "Undo"; setOnClickListener { undo() } }
    bar.addView(simState); bar.addView(overlayState); bar.addView(undoBtn)
    return bar
}

/** The tool FAB (tap = palette) with a small ✕ above it. Must set toolFab and dropToolFab. */
internal fun MainActivity.buildToolFab(): View {
    val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    dropToolFab = TextView(this).apply { text = "✕"; setOnClickListener { selectTool(MOVE_TOOL) } }
    toolFab = com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton(this).apply {
        setOnClickListener { openPalette() }
    }
    col.addView(dropToolFab); col.addView(toolFab)
    return col
}
