# Task 065: Make "paused" obvious — amber top bar + notification on leaving

## Goal
1. While the game is paused, the whole top bar turns dark amber and the subtitle says
   "⏸ PAUSED · <date>" in amber. Nothing goes over the map (players pause to edit).
2. When background play is on and the player leaves the app while the game is paused,
   post one silent notification that says the city will not grow. Remove it when the
   player comes back.

## Context
- Background play starts in `MainActivity.startBackgroundPlay()`
  (`android/app/src/main/java/micropolis/port/BackgroundPlay.kt`, lines 8-19). It
  returns at once when `speed == 0`, so a paused city does not advance while away.
  The player gets no signal of this today.
- `MainActivity.onStop()` calls `startBackgroundPlay()`; `onStart()` calls
  `resumeFromBackgroundPlay()` (`MainActivity.kt`, near line 315).
- Notifications: `Notifier.kt` (70 lines). One channel per `BackgroundPrefs.GROUPS`
  entry. The paused notice is NOT an event group; it gets its own channel and must
  not appear in `GROUPS` (the settings screen lists `GROUPS`).

- Today the only in-app sign of pause is the toolbar icon tint and the subtitle
  "· Paused" (`Panels.kt`, `updatePlayPauseText()` near line 152). Every speed change
  calls `updatePlayPauseText()`, so it is the one place to change the top bar.
- The top bar is built in `MainLayout.kt` lines 30-56: `topBar` (LinearLayout,
  background `0xFF12161C`) holds `toolbar` (subtitle colour `0xFF9AA7B4`) and the
  stats strip.

## Interface (copy exactly)
```kotlin
// Notifier.kt — add inside object Notifier
private const val PAUSED_CHANNEL = "bg_paused"
private const val PAUSED_ID = 0x7a05ed

/** Silent reminder: the city is paused, so it does not grow while the app is closed. */
fun postPaused(ctx: Context, cityName: String)

/** Remove the paused reminder (the player is back). */
fun cancelPaused(ctx: Context)
```

## Files in scope
- `android/app/src/main/java/micropolis/port/Panels.kt`: edit two functions only.
  - `updatePlayPauseText()`: add, before `updateSubtitle()`:
    ```kotlin
    topBar.setBackgroundColor(if (speed == 0) 0xFF4A3510.toInt() else 0xFF12161C.toInt())
    toolbar.setSubtitleTextColor(if (speed == 0) 0xFFF5A623.toInt() else 0xFF9AA7B4.toInt())
    ```
  - `updateSubtitle()`: the paused text becomes `"⏸  PAUSED · $dateText"`.
    The running text does not change.
- `android/app/src/main/java/micropolis/port/Notifier.kt`: edit.
  - In `ensureChannels`, also create channel `PAUSED_CHANNEL`, name `"Paused city"`,
    `NotificationManager.IMPORTANCE_LOW`, description
    `"Reminder that a paused city does not grow while the app is closed."`
  - `postPaused`: same pattern as `post()` (call `ensureChannels`, return if
    `!canPost`, `PendingIntent.getActivity` that opens `MainActivity` with
    `FLAG_ACTIVITY_SINGLE_TOP or FLAG_ACTIVITY_CLEAR_TOP`, no x/y extras, request
    code `PAUSED_ID`). Title `"$cityName is paused"`, text
    `"It will not grow while you are away. Tap to open it."`. Small icon
    `R.drawable.ic_stat_city`, `setAutoCancel(true)`,
    `setCategory(NotificationCompat.CATEGORY_STATUS)`. Notify with id `PAUSED_ID`,
    catch `SecurityException` like `post()`.
  - `cancelPaused`: `NotificationManagerCompat.from(ctx).cancel(PAUSED_ID)`.
- `android/app/src/main/java/micropolis/port/BackgroundPlay.kt`: edit.
  - `startBackgroundPlay()`: replace the first line with:
    ```kotlin
    if (!BackgroundPrefs.enabled(prefs) || !cityReady || handle == 0L) return
    if (speed == 0) { Notifier.postPaused(applicationContext, cityName); return }
    ```
  - `resumeFromBackgroundPlay()`: make the first statement
    `Notifier.cancelPaused(applicationContext)` (before the early return).

Change only these files.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Planner verifies on device: pause → top bar turns dark amber, subtitle
  "⏸ PAUSED · <date>"; play → normal colours. Background play on, pause, press Home → one
  "<city> is paused" notification, no sound. Open the app → it is gone. Unpaused +
  Home → no paused notification.

## Constraints
- Do not change the engine `.cpp` sources.
- Do not add a new dependency.
- Do not add the paused notice to `BackgroundPrefs.GROUPS`.
- Do not change any other behavior of background play.
- Do not add any view over the map.
