package micropolis.port

import android.content.SharedPreferences

/**
 * The background-play timeline (DESIGN.md 12.12a). Everything here is deterministic:
 * the same anchor (.cty + PRNG state) and settings give the same ticks, events and
 * city every time, so a probe (look ahead) and a later replay agree.
 *
 * Recipe: engine at speed 3, engine disasters off, seeded load, then per tick:
 * simTick, the app's monthly disaster roll (DisasterRoll, seeded by the anchor PRNG
 * state), drain events. No Android UI; safe to call from any worker thread.
 */
object BackgroundSim {
    const val TICKS_PER_YEAR = 768                  // 16 ticks per cityTime, 48 cityTime per year
    const val HORIZON_TICKS = 10 * TICKS_PER_YEAR   // look ahead at most 10 game years
    private const val COOLDOWN_CITY_TIME = 48       // a notify-only message repeats at most yearly

    /** Where the probe stops. group == null: nothing to notify inside the horizon. */
    class Stop(val ticks: Int, val group: BackgroundPrefs.Group?, val msg: Int,
               val title: String, val x: Int, val y: Int, val pause: Boolean, val cityTime: Int)

    /** One run of the timeline on an engine handle. */
    private class Timeline(val h: Long, val seed: Long, val disasterFreq: Int) {
        val stats = IntArray(10)
        val ev = IntArray(9)
        var lastMonth = -1

        init {
            MicropolisNative.setSpeed(h, 3)
            MicropolisNative.setEnableDisasters(h, 0)
            while (MicropolisNative.pollEvent(h, ev)) { }     // drop load-time events
            MicropolisNative.getStats(h, stats)
            lastMonth = stats[4] * 12 + stats[5]
        }

        /** Advance one tick; onEvent sees each engine event (ev = [type, x, y, a, …]). */
        fun tick(onEvent: (IntArray) -> Unit) {
            MicropolisNative.simTick(h)
            MicropolisNative.getStats(h, stats)
            val monthKey = stats[4] * 12 + stats[5]
            if (monthKey != lastMonth) {
                val kind = DisasterRoll.roll(seed, monthKey, disasterFreq)
                if (kind >= 0) MicropolisNative.makeDisaster(h, kind)
                lastMonth = monthKey
            }
            while (MicropolisNative.pollEvent(h, ev)) onEvent(ev)
        }

        val year get() = stats[4]
        val cityTime get() = stats[0]
    }

    private fun disasterFreq(p: SharedPreferences) = p.getInt("disasterFreq", 2)

    /** A fresh engine holding the anchor; the caller must destroy it. 0 on failure. */
    private fun openFresh(path: String, rng: Long): Long {
        val h = MicropolisNative.create()
        MicropolisNative.init(h)
        if (MicropolisNative.loadCitySeeded(h, path, rng) != 1) { MicropolisNative.destroy(h); return 0L }
        return h
    }

    /**
     * Look ahead from the anchor for the first stop the player wants to hear about.
     * Returns null only if the anchor cannot be loaded.
     */
    fun probe(p: SharedPreferences, path: String, rng: Long): Stop? {
        val h = openFresh(path, rng)
        if (h == 0L) return null
        try {
            val t = Timeline(h, rng, disasterFreq(p))
            val newYear = BackgroundPrefs.GROUPS.first { it.id == "newYear" }
            var year = t.year
            var stop: Stop? = null
            var n = 0
            while (n < HORIZON_TICKS && stop == null) {
                t.tick { e ->
                    if (stop != null || e[0] != 0) return@tick           // messages only
                    val msg = e[3]
                    val g = Notifier.groupForMessage(msg) ?: return@tick
                    if (!BackgroundPrefs.notify(p, g)) return@tick
                    val pause = BackgroundPrefs.pause(p, g)
                    if (!pause && t.cityTime - p.getInt(cooldownKey(msg), -100000) < COOLDOWN_CITY_TIME) return@tick
                    stop = Stop(n + 1, g, msg, GameText.messages.getOrElse(msg) { "City event" },
                                e[1], e[2], pause, t.cityTime)
                }
                n++
                if (stop == null && t.year != year) {
                    year = t.year
                    if (BackgroundPrefs.notify(p, newYear))
                        stop = Stop(n, newYear, 0, "Annual report: $year", -1, -1,
                                    BackgroundPrefs.pause(p, newYear), t.cityTime)
                }
            }
            return stop ?: Stop(HORIZON_TICKS, null, 0, "", -1, -1, false, t.cityTime)
        } finally {
            MicropolisNative.destroy(h)
        }
    }

    fun cooldownKey(msg: Int) = "bgCool_$msg"

    /**
     * Replay `ticks` ticks from the anchor into engine `h` (a fresh one or the live one).
     * onEvent sees each engine event. Returns the PRNG state afterwards (the next anchor's).
     */
    fun replayInto(h: Long, p: SharedPreferences, path: String, rng: Long, ticks: Int,
                   onEvent: (IntArray) -> Unit = {}): Long? {
        if (MicropolisNative.loadCitySeeded(h, path, rng) != 1) return null
        val t = Timeline(h, rng, disasterFreq(p))
        repeat(ticks) { t.tick(onEvent) }
        return MicropolisNative.getRng(h)
    }

    /** Replay `ticks` ticks in a fresh engine and save the result to `out`. Returns the new PRNG state. */
    fun replayToFile(p: SharedPreferences, path: String, rng: Long, ticks: Int, out: String): Long? {
        val h = MicropolisNative.create()
        MicropolisNative.init(h)
        try {
            val r = replayInto(h, p, path, rng, ticks) ?: return null
            return if (MicropolisNative.saveCity(h, out) == 1) r else null
        } finally {
            MicropolisNative.destroy(h)
        }
    }
}
