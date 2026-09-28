package micropolis.port

/**
 * MainActivity side of background play (DESIGN.md 12.12a): hand the city to the
 * background timeline when the app is closed, and take it back when it opens.
 * Both run on the sim thread, so a quick close + open cannot race.
 */
internal fun MainActivity.startBackgroundPlay() {
    if (!BackgroundPrefs.enabled(prefs) || !cityReady || handle == 0L) return
    if (speed == 0) { Notifier.postPaused(applicationContext, cityName); return }
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

/** A message event seen while catching up, with the date it actually happened on. */
private class CaughtMsg(val msg: Int, val x: Int, val y: Int, val date: String)

internal fun MainActivity.resumeFromBackgroundPlay() {
    val ctx = applicationContext
    Notifier.cancelPaused(ctx)
    if (!simSuspended && !BackgroundScheduler.isActive(ctx)) return
    sim.post {
        val caught = ArrayList<CaughtMsg>()          // seen while catching up the live engine
        val res = if (handle != 0L) BackgroundScheduler.resume(ctx, handle) { e, year, month ->
            if (e[0] == 0) caught.add(CaughtMsg(e[3], e[1], e[2], dateTextFor(year, month)))
        } else null
        // Pause on the sim thread, before simSuspended is released, so tickLoop() (also sim
        // thread) cannot see "not suspended, still at the old speed" and tick past the event
        // before the UI thread gets around to setting speed = 0.
        if (res?.paused == true && speed != 0) { lastRunSpeed = speed; speed = 0 }
        simSuspended = false
        if (res == null) return@post
        MicropolisNative.saveCity(handle, autosavePath)
        val st = IntArray(10); MicropolisNative.getStats(handle, st)
        val away = (st[4] * 12 + st[5]) - prefs.getInt("bgLeftMonth", st[4] * 12 + st[5])
        ui.post {
            resetHistory()                                       // the city moved on; old undo no longer applies
            // Pending first (oldest first — logMessage addFirst's each one), then live-caught,
            // so the newest of all of them ends up on top.
            for (m in res.pending) logMessage("${msgIcon(m.msg)}  ${msgText(m.msg)}", m.x, m.y, dateTextFor(m.year, m.month))
            for (m in caught) logMessage("${msgIcon(m.msg)}  ${msgText(m.msg)}", m.x, m.y, m.date)
            saveMessageLog()
            if (res.paused) {
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
