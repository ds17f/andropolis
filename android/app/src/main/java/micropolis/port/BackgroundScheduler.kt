package micropolis.port

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Background play: anchor, alarms, and what happens when an alarm fires
 * (DESIGN.md 12.12a). The anchor is a .cty + PRNG state + the real time it stands for;
 * BackgroundSim turns it into the future deterministically.
 */
object BackgroundScheduler {
    private const val TAG = "BackgroundPlay"
    private const val K_ACTIVE = "bgActive"
    private const val K_RNG = "bgAnchorRng"
    private const val K_MS = "bgAnchorMs"
    private const val K_PAUSED = "bgPaused"
    private const val K_TICKS = "bgPendTicks"
    private const val K_AT = "bgPendAtMs"
    private const val K_GROUP = "bgPendGroup"
    private const val K_MSG = "bgPendMsg"
    private const val K_TITLE = "bgPendTitle"
    private const val K_X = "bgPendX"
    private const val K_Y = "bgPendY"
    private const val K_PAUSE = "bgPendPause"
    private const val K_CITYTIME = "bgPendCityTime"

    private val lock = Any()   // one timeline operation at a time (alarm vs foreground)

    fun prefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences("micropolis", Context.MODE_PRIVATE)
    fun anchorFile(ctx: Context) = File(ctx.filesDir, "bg_anchor.cty")
    fun isActive(ctx: Context) = prefs(ctx).getBoolean(K_ACTIVE, false)

    /** Engine ticks per real millisecond at the chosen pace. */
    fun ticksPerMs(p: SharedPreferences): Double =
        BackgroundPrefs.PACE_YEARS_PER_HOUR[BackgroundPrefs.paceIndex(p)] *
            BackgroundSim.TICKS_PER_YEAR / 3_600_000.0 *
            p.getInt("bgDebugSpeedup", 1).coerceIn(1, 1000)    // hidden: testing only (set via adb)

    /**
     * The app went to the background with the sim running. `anchorPath` already holds
     * the live city and `rng` the live PRNG state. Worker thread.
     */
    fun start(ctx: Context, rng: Long, nowMs: Long) = synchronized(lock) {
        prefs(ctx).edit().putBoolean(K_ACTIVE, true).putBoolean(K_PAUSED, false)
            .putLong(K_RNG, rng).putLong(K_MS, nowMs).commit()
        probeAndArm(ctx)
    }

    /** Look ahead from the anchor and set the alarm for the stop. Caller holds the lock. */
    private fun probeAndArm(ctx: Context) {
        val p = prefs(ctx)
        val stop = BackgroundSim.probe(p, anchorFile(ctx).path, p.getLong(K_RNG, 0L))
        if (stop == null) { Log.w(TAG, "probe: cannot load anchor"); clear(ctx); return }
        val at = p.getLong(K_MS, System.currentTimeMillis()) + (stop.ticks / ticksPerMs(p)).toLong()
        p.edit().putInt(K_TICKS, stop.ticks).putLong(K_AT, at)
            .putString(K_GROUP, stop.group?.id).putInt(K_MSG, stop.msg).putString(K_TITLE, stop.title)
            .putInt(K_X, stop.x).putInt(K_Y, stop.y).putBoolean(K_PAUSE, stop.pause)
            .putInt(K_CITYTIME, stop.cityTime).commit()
        Log.i(TAG, "armed: ${stop.title.ifEmpty { "horizon" }} in ${stop.ticks} ticks at $at")
        arm(ctx, at)
    }

    /** Set (or reset) the one pending alarm. Also used after a reboot. */
    fun arm(ctx: Context, atMs: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(ctx)
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
    }

    fun cancelAlarm(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java).cancel(alarmIntent(ctx))
    }

    private fun alarmIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(ctx, 57, Intent(ctx, BackgroundAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /**
     * The pending stop has been reached (alarm, or found overdue on foreground).
     * Replay to it, make it the new anchor, notify, and either pause or look ahead again.
     */
    fun reachStop(ctx: Context, notify: Boolean = true) = synchronized(lock) {
        val p = prefs(ctx)
        if (!p.getBoolean(K_ACTIVE, false) || p.getBoolean(K_PAUSED, false)) return@synchronized
        val anchor = anchorFile(ctx)
        val tmp = File(ctx.filesDir, "bg_next.cty")
        val ticks = p.getInt(K_TICKS, 0)
        val rng = BackgroundSim.replayToFile(p, anchor.path, p.getLong(K_RNG, 0L), ticks, tmp.path)
        if (rng == null || !tmp.renameTo(anchor)) { Log.w(TAG, "replay failed"); clear(ctx); return@synchronized }
        val at = p.getLong(K_AT, System.currentTimeMillis())
        p.edit().putLong(K_RNG, rng).putLong(K_MS, at).putInt(K_TICKS, 0).commit()

        val g = BackgroundPrefs.GROUPS.firstOrNull { it.id == p.getString(K_GROUP, null) }
        if (g != null) {
            val msg = p.getInt(K_MSG, 0)
            if (msg > 0) p.edit().putInt(BackgroundSim.cooldownKey(msg), p.getInt(K_CITYTIME, 0)).commit()
            if (notify) Notifier.post(ctx, g, p.getString(K_TITLE, "") ?: "",
                cityLine(p), p.getInt(K_X, -1), p.getInt(K_Y, -1))
        }
        if (g != null && p.getBoolean(K_PAUSE, false)) {
            p.edit().putBoolean(K_PAUSED, true).commit()          // wait for the player
            Log.i(TAG, "paused at ${p.getString(K_TITLE, "")}")
        } else {
            probeAndArm(ctx)
        }
    }

    private fun cityLine(p: SharedPreferences): String {
        val name = p.getString("cityName", "Your city") ?: "Your city"
        return "$name — tap to see it"
    }

    /**
     * The player is back. Bring the live engine `h` to "now" on the background timeline.
     * Runs on the sim thread. Returns the event to show (x, y, paused) or null if background
     * play was not active.
     */
    class Resume(val paused: Boolean, val x: Int, val y: Int, val title: String, val caughtUpTicks: Int)

    fun resume(ctx: Context, h: Long, onEvent: (IntArray) -> Unit): Resume? {
        if (!isActive(ctx)) return null
        cancelAlarm(ctx)
        val now = System.currentTimeMillis()
        // Stops that are overdue (alarm late or killed): reach them first, without a notification.
        var guard = 0
        while (guard++ < 1000) {
            val p = prefs(ctx)
            if (p.getBoolean(K_PAUSED, false) || now < p.getLong(K_AT, Long.MAX_VALUE)) break
            reachStop(ctx, notify = false)
            if (!isActive(ctx)) return null
        }
        synchronized(lock) {
            val p = prefs(ctx)
            val paused = p.getBoolean(K_PAUSED, false)
            val elapsed = (now - p.getLong(K_MS, now)).coerceAtLeast(0)
            val ticks = if (paused) 0
                        else minOf((elapsed * ticksPerMs(p)).toLong(), (p.getInt(K_TICKS, 0) - 1).toLong())
                             .coerceAtLeast(0).toInt()
            val r = BackgroundSim.replayInto(h, p, anchorFile(ctx).path, p.getLong(K_RNG, 0L), ticks, onEvent)
            val res = Resume(paused, p.getInt(K_X, -1), p.getInt(K_Y, -1), p.getString(K_TITLE, "") ?: "", ticks)
            clear(ctx)
            return if (r == null) null else res
        }
    }

    fun clear(ctx: Context) {
        cancelAlarm(ctx)
        prefs(ctx).edit().putBoolean(K_ACTIVE, false).putBoolean(K_PAUSED, false).commit()
    }

    /** After a reboot: set the stored alarm again. */
    fun rearm(ctx: Context) {
        val p = prefs(ctx)
        if (p.getBoolean(K_ACTIVE, false) && !p.getBoolean(K_PAUSED, false))
            arm(ctx, p.getLong(K_AT, System.currentTimeMillis()))
    }
}

/** The pending background stop is due. */
class BackgroundAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val done = goAsync()
        Thread {
            try { BackgroundScheduler.reachStop(ctx.applicationContext) }
            catch (e: Exception) { Log.e("BackgroundPlay", "alarm failed", e) }
            finally { done.finish() }
        }.start()
    }
}

/** Re-arm after a reboot (alarms do not survive one). */
class BackgroundBootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) BackgroundScheduler.rearm(ctx.applicationContext)
    }
}
