# Task 056: Notifier — notification channels, posting, tap opens the map at the event

## Goal
Add the notification part of background play (DESIGN.md §12.9):
- One notification channel per event group (Disasters, New year, City problems,
  Milestones), so the user can tune each one in Android settings.
- A `Notifier.post(...)` that shows a notification. Tapping it opens the app and
  centres the map on the event tile.
- Ask for the notification permission (Android 13+) when the user turns on
  **Run in background**.
- A **Send a test notification** button at the bottom of Settings → Background.

Nothing posts real notifications yet. Task 057 calls `Notifier.post` from the
background engine.

## Context (read only these parts)
- `BackgroundPrefs.kt` (small): `BackgroundPrefs.GROUPS`, `Group(id, title, desc, …)`,
  `KEY_ENABLED`.
- `BackgroundSettings.kt` (small): `backgroundSettingsTab()`. Its first view is the
  "Run in background" `settingsToggle(...) { c -> prefs.edit()...KEY_ENABLED... }`.
- `MainActivity.kt`: line 177 `override fun onCreate`, line 270
  `sim.post({ tickLoop() })` (end of onCreate), line 288 `override fun onPause()`.
  `MainActivity` has `ui` (Handler on the main thread) and `mapView`.
- `MapView.kt` line 73: `fun centerOnTile(tx: Int, ty: Int)`.
- `android/app/src/main/AndroidManifest.xml` (small).
- Do NOT read all of `MainActivity.kt`; read only the lines named above.

## Interface

### 1. New file `res/drawable/ic_stat_city.xml` (copy exactly)
```xml
<!-- Material icon "location_city" (Apache 2.0): notification small icon. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFFFF" android:pathData="M15,11V5l-3,-3 -3,3v2H3v14h18V11h-6zM7,19H5v-2h2v2zM7,15H5v-2h2v2zM7,11H5V9h2v2zM13,19h-2v-2h2v2zM13,15h-2v-2h2v2zM13,11h-2V9h2v2zM13,7h-2V5h2v2zM19,19h-2v-2h2v2zM19,15h-2v-2h2v2z"/>
</vector>
```

### 2. New file `Notifier.kt` (copy exactly)
```kotlin
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
```

### 3. New file `EventIntent.kt`
```kotlin
package micropolis.port
import android.content.Intent

/** If the intent came from a notification, centre the map on its tile (once). */
internal fun MainActivity.handleEventIntent(i: Intent?)
```
Behaviour: read `Notifier.EXTRA_X` / `EXTRA_Y` with default `-1`. If either is < 0,
return. Otherwise remove both extras from the intent and do
`ui.postDelayed({ mapView.centerOnTile(x, y) }, 800)` (the delay lets the city load).

Also in `EventIntent.kt`:
```kotlin
/** Android 13+: ask for POST_NOTIFICATIONS if we do not have it yet. */
internal fun MainActivity.requestNotificationPermission()
```
Behaviour: if `Build.VERSION.SDK_INT >= 33` and
`checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED`,
call `requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 56)`.

### 4. `MainActivity.kt` — two small additions only
- After line 270 (`sim.post({ tickLoop() })`, the end of `onCreate`), add:
  `Notifier.ensureChannels(this)` and `handleEventIntent(intent)`.
- Add a new override next to `onPause()`:
  ```kotlin
  override fun onNewIntent(intent: android.content.Intent) {
      super.onNewIntent(intent)
      setIntent(intent)
      handleEventIntent(intent)
  }
  ```

### 5. `BackgroundSettings.kt` — two small additions only
- In the "Run in background" toggle callback, after saving the pref, add:
  `if (c) requestNotificationPermission()`.
- At the end of the column (after the event rows), add a full-width
  `android.widget.Button` with text **"Send a test notification"** (top margin 12dp).
  On click: `requestNotificationPermission()`, then
  `Notifier.post(this, BackgroundPrefs.GROUPS[0], "Fire reported!", "Test notification — tap to go to the map centre", 60, 50)`.

### 6. `AndroidManifest.xml`
- Add `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />`
  directly inside `<manifest>` (before `<application>`).
- Add `android:launchMode="singleTop"` to the `.MainActivity` `<activity>`.

## Files in scope
- `android/app/src/main/res/drawable/ic_stat_city.xml`: new.
- `android/app/src/main/java/micropolis/port/Notifier.kt`: new.
- `android/app/src/main/java/micropolis/port/EventIntent.kt`: new.
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: the two additions above.
- `android/app/src/main/java/micropolis/port/BackgroundSettings.kt`: the two additions above.
- `android/app/src/main/AndroidManifest.xml`: the two changes above.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Planner verifies on device: the test button shows a notification (after granting
  the permission); tapping it opens the app and centres the map; Android's app
  notification settings list the four channels.

## Constraints
- **Copy the code blocks above exactly.** Everything you need is in this spec. Do not
  read engine sources (`MicropolisCore/`) or git history, and read only the line
  ranges named above.
- Only ADD code. Do not delete, re-indent or restructure existing code. Do not edit this spec.
- No new dependencies (`androidx.core` is already there). Do not change C/C++ code,
  CMake, or `build.gradle.kts`.
- Commit when green; stage only your files by name. Do not use `git checkout`, `git reset`, or `git add -A`.
