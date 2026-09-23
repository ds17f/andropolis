# Task 031: Autosave + name your city

## Goal
Two things so a city is never lost and feels like YOUR city:
1. **Autosave** the city continuously and restore it on the next launch, so closing,
   backgrounding, or an in-game collapse never loses progress.
2. **Name the city** when a new one starts, store the name, and show it in the top bar
   where the title currently reads "Micropolis".

## Context (MainActivity.kt)
- The engine is set up on the `sim` thread in `onCreate`:
  ```kotlin
  sim.post {
      handle = MicropolisNative.create()
      MicropolisNative.init(handle)
      MicropolisNative.generateRandomCity(handle)
  }
  sim.post({ tickLoop() })
  ```
- `tickLoop()` re-posts itself every 100ms on `sim` (runs whether paused or not).
- Top-bar title is the `cityTitle` TextView (`text = "Micropolis"`).
- The overflow `PopupMenu` handles `"New city"` / `"Save city"` / `"Load city"` (these
  use `savePath = filesDir/city.cty`, the MANUAL slot — leave it as the manual slot).
- `onDestroy()` exists and posts engine teardown on `sim`.
- All engine calls run on `sim`; all View/UI changes must run on `ui` (main) thread.
- `MicropolisNative.saveCity(handle, path)` / `loadCity(handle, path)` return 1/0.

## Interface

### Fields
```kotlin
private val autosavePath by lazy { java.io.File(filesDir, "autosave.cty").absolutePath }
private val prefs by lazy { getSharedPreferences("micropolis", MODE_PRIVATE) }
private var cityName: String = "Micropolis"
@Volatile private var cityReady = false      // true once a city exists (guard autosave)
private var lastAutosaveMs = 0L
```

### Startup: restore autosave, else prompt + generate
Replace the setup `sim.post { create/init/generateRandomCity }` block with:
```kotlin
sim.post {
    handle = MicropolisNative.create()
    MicropolisNative.init(handle)
    val hasSave = java.io.File(autosavePath).exists()
    if (hasSave) {
        MicropolisNative.loadCity(handle, autosavePath)
        cityReady = true
        ui.post { cityName = prefs.getString("cityName", "Micropolis") ?: "Micropolis"
                  cityTitle.text = cityName }
    } else {
        MicropolisNative.generateRandomCity(handle)
        cityReady = true
        ui.post { promptCityName(isFirst = true) }   // name a brand-new city
    }
}
```
(Keep the `sim.post({ tickLoop() })` line after it.)

### Name prompt
```kotlin
private fun promptCityName(isFirst: Boolean) {
    val input = android.widget.EditText(this).apply {
        setText(if (isFirst) "" else cityName)
        hint = "Name your city"
        setSingleLine()
    }
    androidx.appcompat.app.AlertDialog.Builder(this)
        .setTitle(if (isFirst) "Name your city" else "Rename city")
        .setView(input)
        .setPositiveButton("OK") { _, _ ->
            val name = input.text.toString().trim().ifEmpty { "Micropolis" }
            cityName = name
            prefs.edit().putString("cityName", name).apply()
            cityTitle.text = name
        }
        .setCancelable(false)
        .show()
}
```

### Autosave — periodic (wall clock) in `tickLoop()`
At the top of `tickLoop()` (still on `sim`), add:
```kotlin
if (cityReady && handle != 0L) {
    val now = android.os.SystemClock.uptimeMillis()
    if (now - lastAutosaveMs > 30_000L) {
        lastAutosaveMs = now
        MicropolisNative.saveCity(handle, autosavePath)
    }
}
```

### Autosave — on background (critical: user swipes away)
Add:
```kotlin
override fun onPause() {
    super.onPause()
    if (cityReady && handle != 0L) sim.post { MicropolisNative.saveCity(handle, autosavePath) }
}
```

### "New city" overflow action → confirm, name, regenerate, reset autosave
Change the `"New city"` branch to (so a fresh city is named and the old autosave is not
silently kept):
```kotlin
"New city" -> {
    cityReady = false
    sim.post {
        MicropolisNative.generateRandomCity(handle)
        MicropolisNative.saveCity(handle, autosavePath)   // reset autosave to the new city
        cityReady = true
        ui.post { promptCityName(isFirst = true) }
    }
}
```
(If Undo task 029 added a snapshot history, do not worry about it here — leave any
`commitSnapshot()`/history calls exactly as they are; only change what this spec names.)

## Files in scope
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- The planner verifies on device: name prompt on a fresh install; the name shows in the
  title; killing/reopening restores the city; backgrounding saves.

## Constraints
- Change only `MainActivity.kt`. Do not change the JNI, C ABI, engine, `MapView`,
  drawables, or `build.gradle.kts`. Keep the manual Save/Load (`savePath`) working.
- No new dependencies. Commit when green; stage only your file by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
