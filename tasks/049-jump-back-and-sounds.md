# Task 049: "Back" after an auto-jump + non-verbal build/bulldoze sounds

## Goal
1. When the map auto-jumps to an event, let the player return to where they were.
2. Replace the spoken "build" / "bulldozer" clips with non-verbal effects.

## Context
- `MapView` has private `panX`, `panY`, `scale`, plus `clampPan()` and `centerOnTile(tx, ty)`.
- `MainActivity`: event drain (in `tickLoop`, handled in `ui.post { when (type) { ... } }`)
  calls `mapView.centerOnTile(ex, ey)` when `autoGoto` is on — for type 0 (MESSAGE) and
  type 2 (AUTO_GOTO). `showBanner(text)` shows `messageBanner` (TextView, top-left of the
  map) for 6 s; tapping the banner currently calls `mapView.centerOnTile(lastEventTile)`.
- Sound files: `android/app/src/main/res/raw/snd_build.mp3` and `snd_bulldozer.mp3` are
  spoken words. Source set: `MicropolisCore/content/micropolis/sounds/`.

## Interface

### 1. MapView — save / restore the view
```kotlin
/** Current view as [panX, panY, scale]. */
fun saveView(): FloatArray = floatArrayOf(panX, panY, scale)
/** Restore a view captured by saveView(). */
fun restoreView(v: FloatArray) {
    if (v.size < 3) return
    panX = v[0]; panY = v[1]; scale = v[2]
    clampPan(); invalidate()
}
```

### 2. MainActivity — remember where we were, offer "↩ Back"
- Field: `private var viewBeforeJump: FloatArray? = null`.
- Helper used for every AUTO jump (replace the two `mapView.centerOnTile(ex, ey)` calls that
  are gated by `autoGoto`):
  ```kotlin
  private fun autoJumpTo(x: Int, y: Int) {
      if (viewBeforeJump == null) viewBeforeJump = mapView.saveView()   // keep the ORIGINAL spot across chained jumps
      mapView.centerOnTile(x, y)
      messageBanner.text = messageBanner.text.toString() + "   ↩ Back"
  }
  ```
  Call it after `showBanner(...)` so the "↩ Back" is appended to the event text.
- Banner tap: if `viewBeforeJump != null`, restore it and clear it
  (`mapView.restoreView(viewBeforeJump!!); viewBeforeJump = null; messageBanner.visibility = View.GONE`);
  otherwise keep today's behavior (go to `lastEventTile`).
- When the banner auto-hides (`bannerHide`), also clear `viewBeforeJump` — the player chose
  to stay. (Change `bannerHide` to `Runnable { messageBanner.visibility = View.GONE; viewBeforeJump = null }`.)
- Any manual navigation also means "stay": in `MapView`, add
  `var onUserNavigate: (() -> Unit)? = null`, invoke it when a two-finger pan or pinch
  starts (in `ACTION_POINTER_DOWN` when not `navLocked`), and in MainActivity set
  `mapView.onUserNavigate = { viewBeforeJump = null }`. Also clear it in
  `minimap.onTileSelected`.

### 3. Sounds (shell)
```sh
SRC=MicropolisCore/content/micropolis/sounds
cp $SRC/bop.mp3    android/app/src/main/res/raw/snd_build.mp3
cp $SRC/rumble.mp3 android/app/src/main/res/raw/snd_bulldozer.mp3
```
(No code change for sounds — same resource names.)

## Files in scope
- `android/app/src/main/java/micropolis/port/MapView.kt`: edit.
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.
- `android/app/src/main/res/raw/snd_build.mp3`, `snd_bulldozer.mp3`: replace.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Planner verifies on device: an auto-jump shows "↩ Back" in the toast; tapping it returns
  to the previous spot and zoom; letting it time out keeps you at the event.

## Constraints
- Only the files above. No JNI/C ABI/engine/build.gradle changes. No new dependencies.
- Commit when green; stage only your files by name. Do not use `git checkout`,
  `git reset`, or `git add -A`.
