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

// The sim-thread tick loop (30 fps), autosaves, engine events and the message log.
// Extension functions on MainActivity; state lives in MainActivity.kt.

/** Sim thread: write a timestamped autosave of the current city into Documents/Micropolis. */
internal fun MainActivity.publicAutosave() {
    lastPublicAutosaveMs = android.os.SystemClock.uptimeMillis()
    MicropolisNative.getStats(handle, statsBuf)
    val tmp = CitySaves.tempFile(this)
    MicropolisNative.saveCity(handle, tmp.absolutePath)
    try { CitySaves.writeAutosave(this, tmp, sanitize(cityName), statsBuf[4], statsBuf[5]) }
    catch (e: Exception) { android.util.Log.w("Micropolis", "autosave failed", e) }
}

internal fun MainActivity.logMessage(text: String, x: Int, y: Int) {
    messageLog.addFirst(LogEntry(dateText, text, x, y))   // newest first
    while (messageLog.size > 50) messageLog.removeLast()
}

internal fun MainActivity.msgText(i: Int) = messageText.getOrElse(i) { "City update" }

/** Icon for an engine message index (1..57) — shown in the toast and the feed. */
internal fun MainActivity.msgIcon(i: Int): String = when (i) {
    1 -> "🏠"; 2 -> "🏢"; 3 -> "🏭"; 4 -> "🛣"; 5, 26 -> "🚆"; 6, 15, 40 -> "⚡"
    7 -> "🏟"; 8, 25 -> "⚓"; 9, 24 -> "✈"; 10 -> "☁"; 11 -> "🚨"; 12, 41 -> "🚗"
    13 -> "🚒"; 14 -> "🚓"; 16, 17, 18, 19 -> "💰"; 20 -> "🔥"; 21 -> "👾"; 22 -> "🌪"
    23 -> "⛰"; 27 -> "🚁"; 28 -> "📉"; 29, 33 -> "💸"; 30 -> "💣"; 31 -> "🌳"; 32 -> "💥"
    34 -> "🚜"; in 35..39 -> "🎉"; 42 -> "🌊"; 43 -> "☢"; 44 -> "✊"; 45, 46 -> "🏙"
    47 -> "🏆"; 48 -> "💀"; else -> "ℹ"
}

internal fun MainActivity.tickLoop() {
    if (simSuspended) { sim.postDelayed({ tickLoop() }, 250); return }   // background play owns the city
    if (cityReady && handle != 0L) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastAutosaveMs > 30_000L) {
            lastAutosaveMs = now
            MicropolisNative.saveCity(handle, autosavePath)
        }
        if (lastPublicAutosaveMs == 0L) lastPublicAutosaveMs = now
        if (now - lastPublicAutosaveMs > 5 * 60_000L) publicAutosave()
    }
    // Engine speed 3 every frame: loads/undo/generate reset it, and this runs on the sim
    // thread after them. The UI speed only sets how many ticks we run (Pause = 0).
    if (handle != 0L) MicropolisNative.setSpeed(handle, 3)
    if (handle != 0L) MicropolisNative.setEnableDisasters(handle, 0)   // only our monthly roll (see below)
    tickDebt = (tickDebt + ticksPerSecond[speed] / 30.0).coerceAtMost(16.0)
    val n = tickDebt.toInt(); tickDebt -= n
    repeat(n) { MicropolisNative.simTick(handle) }
    MicropolisNative.copyTiles(handle, buf)
    val tilesCopy = buf.copyOf()
    val spriteBuf = IntArray(4 * 32)
    val nSprites = MicropolisNative.copySprites(handle, spriteBuf)
    ui.post { mapView.update(tilesCopy); minimap.update(tilesCopy); mapView.updateSprites(spriteBuf, nSprites) }

    // Refresh overlay when one is active
    if (currentOverlay != 0 && cityReady && handle != 0L) {
        MicropolisNative.copyOverlay(handle, currentOverlay, overlayBuf)
        val snap = overlayBuf.copyOf()
        ui.post { mapView.setOverlay(currentOverlay, snap) }
    }
    MicropolisNative.getStats(handle, statsBuf)
    val funds = statsBuf[1]; val pop = statsBuf[2]; val score = statsBuf[3]
    val year = statsBuf[4]; val month = statsBuf[5]
    val months = arrayOf("Jan","Feb","Mar","Apr","May","Jun",
                        "Jul","Aug","Sep","Oct","Nov","Dec")
    val monthName = months.getOrElse(month) { "?" }
    
    // Disaster frequency logic: once per new game month
    val monthKey = year * 12 + month
    if (monthKey != lastDisasterMonth) {
        if (lastDisasterMonth != -1 && disasterFreq > 0 && speed != 0) {
            if (random.nextInt(disasterYearsPer[disasterFreq] * 12) == 0) {
                // weight like the original: fires most common, meltdown rarest
                val kind = intArrayOf(0, 0, 1, 2, 3, 4, 0, 5)[random.nextInt(8)]
                MicropolisNative.makeDisaster(handle, kind)
            }
        }
        lastDisasterMonth = monthKey
    }
    
    ui.post {
        dateText = "$monthName $year"; updateSubtitle()
        fundsValue.text = "\$" + "%,d".format(funds)
        popValue.text = "%,d".format(pop)
        scoreValue.text = "$score"
    }
    // Only a real new year (+1). A different year means another city was loaded: no report.
    val yearRolled = lastReportYear != -1 && year == lastReportYear + 1 && annualReportEnabled
    lastReportYear = year
    if (yearRolled) {
        ui.post {
            val didPause = speed != 0
            if (didPause) { lastRunSpeed = speed; speed = 0; updatePlayPauseText(); updateSpeedChipText() }
            showReportCard(year, resume = didPause)
        }
    }
    while (MicropolisNative.pollEvent(handle, eventBuf)) {
        val type = eventBuf[0]; val ex = eventBuf[1]; val ey = eventBuf[2]
        val a = eventBuf[3]; val b = eventBuf[4]; val c = eventBuf[5]
        val d = eventBuf[6]; val e2 = eventBuf[7]; val f = eventBuf[8]
        ui.post {
            when (type) {
                0 -> { // MESSAGE
                    val line = "${msgIcon(a)}  ${msgText(a)}"
                    showBanner(line)
                    logMessage(line, ex, ey)
                    if (ex >= 0 && ey >= 0) {
                        lastEventTile = Pair(ex, ey)
                        if (autoGoto) autoJumpTo(ex, ey)  // Settings → Auto go to events
                    }
                }
                1 -> showZoneStatusDialog(ex, ey, a, b, c, d, e2, f) // Query result
                2 -> { lastEventTile = Pair(ex, ey); if (autoGoto) autoJumpTo(ex, ey) } // AUTO_GOTO
                3 -> showBanner("⛰  Earthquake! (strength $a)")
                4 -> showBanner("💀  Your city has fallen.")
                5 -> showBanner("🏆  You won!")
                6 -> sfx.engineSound(a) // SOUND
            }
        }
    }
    sim.postDelayed({ tickLoop() }, 33)   // 30 fps; ticksPerSecond sets the pace
}
