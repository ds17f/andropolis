package micropolis.port
import android.content.Intent
import android.os.Build

/** If the intent came from a notification, centre the map on its tile (once). */
internal fun MainActivity.handleEventIntent(i: Intent?) {
    if (i == null) return
    val x = i.getIntExtra(Notifier.EXTRA_X, -1)
    val y = i.getIntExtra(Notifier.EXTRA_Y, -1)
    if (x < 0 || y < 0) return
    i.removeExtra(Notifier.EXTRA_X)
    i.removeExtra(Notifier.EXTRA_Y)
    ui.postDelayed({ mapView.centerOnTile(x, y) }, 800)
}

/** Android 13+: ask for POST_NOTIFICATIONS if we do not have it yet. */
internal fun MainActivity.requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= 33 &&
        checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 56)
    }
}
