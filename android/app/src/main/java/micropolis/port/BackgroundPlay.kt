package micropolis.port

/**
 * MainActivity side of background play (DESIGN.md 12.12a): hand the city to the
 * background timeline when the app is closed, and take it back when it opens.
 * Both run on the sim thread, so a quick close + open cannot race.
 */
internal fun MainActivity.startBackgroundPlay() {
    if (!BackgroundPrefs.enabled(prefs) || !cityReady || handle == 0L || speed == 0) return
    simSuspended = true
    val now = System.currentTimeMillis()
    val ctx = applicationContext
    sim.post {
        val st = IntArray(10); MicropolisNative.getStats(handle, st)
        prefs.edit().putInt("bgLeftMonth", st[4] * 12 + st[5]).commit()   // for "while you were away"
        MicropolisNative.saveCity(handle, BackgroundScheduler.anchorFile(ctx).path)
        BackgroundScheduler.start(ctx, MicropolisNative.getRng(handle), now)
    }
}

internal fun MainActivity.resumeFromBackgroundPlay() {
    val ctx = applicationContext
    if (!simSuspended && !BackgroundScheduler.isActive(ctx)) return
    sim.post {
        val caught = ArrayList<Triple<Int, Int, Int>>()          // (message, x, y) seen while catching up
        val res = if (handle != 0L) BackgroundScheduler.resume(ctx, handle) { e ->
            if (e[0] == 0) caught.add(Triple(e[3], e[1], e[2]))
        } else null
        simSuspended = false
        if (res == null) return@post
        MicropolisNative.saveCity(handle, autosavePath)
        val st = IntArray(10); MicropolisNative.getStats(handle, st)
        val away = (st[4] * 12 + st[5]) - prefs.getInt("bgLeftMonth", st[4] * 12 + st[5])
        ui.post {
            resetHistory()                                       // the city moved on; old undo no longer applies
            for ((m, x, y) in caught) logMessage(GameText.messages.getOrElse(m) { "City event" }, x, y)
            if (res.paused) {
                if (speed != 0) { lastRunSpeed = speed; speed = 0 }
                updatePlayPauseText(); updateSpeedChipText()
                if (res.x >= 0) mapView.zoomToTile(res.x, res.y)
                showBanner("⏸  ${res.title}")
            } else if (away > 0) {
                val y = away / 12; val m = away % 12
                val parts = listOfNotNull(if (y > 0) "$y year${if (y == 1) "" else "s"}" else null,
                                          if (m > 0) "$m month${if (m == 1) "" else "s"}" else null)
                showBanner("While you were away: ${parts.joinToString(", ")} passed")
            }
        }
    }
}
