# Task 046: Sound effects

## Goal
Play the classic Micropolis sounds: the engine's own sounds (sirens, explosions, the
monster, traffic honks, foghorn, "uh-uh"/"sorry") plus build/bulldoze feedback when a
tool succeeds. Add a **Sound effects** on/off switch in Settings (persisted).

## Context
- The engine event bridge now emits **event type 6 = SOUND** with `a` = sound id
  (see `MicropolisSound` in `android/engine/include/micropolis_c.h`):
  0 Siren, 1 ExplosionLow, 2 ExplosionHigh, 3 Monster, 4 HonkHonkLow, 5 HonkHonkMed,
  6 HonkHonkHigh, 7 HeavyTraffic, 8 FogHornLow, 9 UhUh, 10 Sorry.
- `MainActivity.tickLoop()` drains events with `pollEvent(handle, eventBuf)` and handles
  each `type` inside a `ui.post { when (type) { ... } }`.
- `mapView.onTileTap = { tileX, tileY -> ... sim.post { MicropolisNative.doTool(handle, tool, tileX, tileY) } }`
  — `doTool` returns 1 on success (currently ignored). Tool 7 = Bulldozer.
- Settings panel: `showSettingsPanel()` builds one "Minimap navigation" row (label +
  description + `android.widget.Switch`). `prefs` = SharedPreferences.
- Source audio (GPL, same as the engine): `MicropolisCore/content/micropolis/sounds/`.

## Interface

### 1. Copy the sound assets (shell)
```sh
SRC=MicropolisCore/content/micropolis/sounds
DST=android/app/src/main/res/raw
mkdir -p $DST
cp $SRC/Siren.mp3         $DST/snd_siren.mp3
cp $SRC/ExplosionLow.mp3  $DST/snd_explosion_low.mp3
cp $SRC/ExplosionHigh.mp3 $DST/snd_explosion_high.mp3
cp $SRC/Monster.mp3       $DST/snd_monster.mp3
cp $SRC/HonkHonkLow.mp3   $DST/snd_honk_low.mp3
cp $SRC/HonkHonkMed.mp3   $DST/snd_honk_med.mp3
cp $SRC/HonkHonkHigh.mp3  $DST/snd_honk_high.mp3
cp $SRC/HeavyTraffic.mp3  $DST/snd_heavy_traffic.mp3
cp $SRC/FogHornLow.mp3    $DST/snd_foghorn.mp3
cp $SRC/UhUh.mp3          $DST/snd_uhuh.mp3
cp $SRC/Sorry.mp3         $DST/snd_sorry.mp3
cp $SRC/build.mp3         $DST/snd_build.mp3
cp $SRC/bulldozer.mp3     $DST/snd_bulldozer.mp3
```

### 2. New file `SoundFx.kt`
```kotlin
package micropolis.port

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock

/** Small SoundPool wrapper. Throttles repeats so fast sims don't spam the speaker. */
class SoundFx(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val engineRes = intArrayOf(
        R.raw.snd_siren, R.raw.snd_explosion_low, R.raw.snd_explosion_high, R.raw.snd_monster,
        R.raw.snd_honk_low, R.raw.snd_honk_med, R.raw.snd_honk_high, R.raw.snd_heavy_traffic,
        R.raw.snd_foghorn, R.raw.snd_uhuh, R.raw.snd_sorry)
    private val engineIds = IntArray(engineRes.size) { pool.load(context, engineRes[it], 1) }
    private val buildId = pool.load(context, R.raw.snd_build, 1)
    private val dozeId = pool.load(context, R.raw.snd_bulldozer, 1)
    private val lastPlayed = LongArray(engineRes.size + 2)
    var enabled = true

    private fun play(slot: Int, id: Int, minGapMs: Long) {
        if (!enabled) return
        val now = SystemClock.uptimeMillis()
        if (now - lastPlayed[slot] < minGapMs) return
        lastPlayed[slot] = now
        pool.play(id, 0.8f, 0.8f, 1, 0, 1f)
    }
    fun engineSound(sound: Int) { if (sound in engineIds.indices) play(sound, engineIds[sound], 400) }
    fun build() = play(engineRes.size, buildId, 120)
    fun bulldoze() = play(engineRes.size + 1, dozeId, 120)
    fun release() = pool.release()
}
```

### 3. MainActivity wiring
- Field: `private lateinit var sfx: SoundFx`.
- In `onCreate` (early, before the tick loop starts): `sfx = SoundFx(this); sfx.enabled = prefs.getBoolean("sound", true)`.
- In `onDestroy`: `sfx.release()`.
- Event drain: add a branch to the `when (type)`: `6 -> sfx.engineSound(a)`.
- Tool feedback — in `mapView.onTileTap`, use the `doTool` result:
  ```kotlin
  sim.post {
      val r = MicropolisNative.doTool(handle, tool, tileX, tileY)
      if (r == 1) ui.post { if (tool == 7) sfx.bulldoze() else sfx.build() }
  }
  ```
- Settings: in `showSettingsPanel()`, add a second row below "Minimap navigation", built the
  same way: title **"Sound effects"**, description "City sounds and build feedback.", a
  `Switch` with `isChecked = sfx.enabled` whose listener does
  `sfx.enabled = checked; prefs.edit().putBoolean("sound", checked).apply()`.

## Files in scope
- `android/app/src/main/res/raw/snd_*.mp3`: new (the 13 copies above).
- `android/app/src/main/java/micropolis/port/SoundFx.kt`: new.
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- The planner verifies on device: building plays a click, bulldozing a dozer sound, a
  Fire disaster sounds a siren; the Settings switch mutes everything and persists.

## Constraints
- Do not change the JNI, C ABI, engine, `MapView`, `MinimapView`, drawables, or
  `build.gradle.kts`. No new dependencies (SoundPool is part of Android).
- Commit when green; stage only your files by name (`git add android/app/src/main/res/raw/snd_*.mp3`
  is fine). Do not use `git checkout`, `git reset`, or `git add -A`.
