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

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        for (g in BackgroundPrefs.GROUPS) {
            val importance = if (g.id == "disasters") NotificationManager.IMPORTANCE_HIGH
                             else NotificationManager.IMPORTANCE_DEFAULT
            nm.createNotificationChannel(NotificationChannel(channelId(g), g.title, importance).apply {
                description = g.desc
            })
        }
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

    /** Which group an engine message (index into messageText, 1..57) belongs to, or null. */
    fun groupForMessage(msg: Int): BackgroundPrefs.Group? {
        val id = when (msg) {
            in 20..27, 30, 32, 42, 43, 44 -> "disasters"
            10, 11, 12, 15, 16, 28, 29, 40, 41 -> "problems"
            in 35..39 -> "milestones"
            else -> null
        }
        return BackgroundPrefs.GROUPS.firstOrNull { it.id == id }
    }
}
