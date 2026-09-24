package micropolis.port

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Posts background-play notifications (DESIGN.md §12.9). One channel per event group. */
object Notifier {
    const val EXTRA_X = "eventX"
    const val EXTRA_Y = "eventY"

    private fun channelId(g: BackgroundPrefs.Group) = "bg_${g.id}"

    private const val PAUSED_CHANNEL = "bg_paused"
    private const val PAUSED_ID = 0x7a05ed

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel("bg_problems")   // replaced by pollution / traffic / power / money (task 061)
        for (g in BackgroundPrefs.GROUPS) {
            val importance = if (g.id == "disasters" || g.id == "accidents") NotificationManager.IMPORTANCE_HIGH
                             else NotificationManager.IMPORTANCE_DEFAULT
            nm.createNotificationChannel(NotificationChannel(channelId(g), g.title, importance).apply {
                description = g.desc
            })
        }
        nm.createNotificationChannel(NotificationChannel(PAUSED_CHANNEL, "Paused city", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Reminder that a paused city does not grow while the app is closed."
        })
    }

    fun canPost(ctx: Context) = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    /** Show a notification; tapping it opens MainActivity centred on tile (x, y) (x < 0: no tile). */
    fun post(ctx: Context, g: BackgroundPrefs.Group, title: String, text: String, x: Int, y: Int) {
        ensureChannels(ctx)
        if (!canPost(ctx)) return
        val id = g.id.hashCode()
        val open = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_X, x); putExtra(EXTRA_Y, y)
        }
        val pi = PendingIntent.getActivity(ctx, id, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, channelId(g))
            .setSmallIcon(R.drawable.ic_stat_city)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (e: SecurityException) { }
    }

    /** Silent reminder: the city is paused, so it does not grow while the app is closed. */
    fun postPaused(ctx: Context, cityName: String) {
        ensureChannels(ctx)
        if (!canPost(ctx)) return
        val open = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(ctx, PAUSED_ID, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, PAUSED_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_city)
            .setContentTitle("$cityName is paused")
            .setContentText("It will not grow while you are away. Tap to open it.")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(PAUSED_ID, n) } catch (e: SecurityException) { }
    }

    /** Remove the paused reminder (the player is back). */
    fun cancelPaused(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(PAUSED_ID)
    }

    /** Which group an engine message (index into messageText, 1..57) belongs to, or null. */
    fun groupForMessage(msg: Int): BackgroundPrefs.Group? {
        val id = when (msg) {
            20, 21, 22, 23, 42 -> "disasters"
            24, 25, 26, 27, 30, 32, 43, 44 -> "accidents"
            10, 11 -> "pollution"
            12, 41 -> "traffic"
            15, 40 -> "power"
            16, 28, 29 -> "money"
            in 35..39 -> "milestones"
            else -> null
        }
        return BackgroundPrefs.GROUPS.firstOrNull { it.id == id }
    }
}
