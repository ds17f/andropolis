# Task 064: Start zoomed to fill the screen; zoom in on events

## Goal
1. At startup the map should fill the map area (today it is fully zoomed out, with dark bands
   above and below).
2. When the app jumps to an event (auto-goto, a notification tap, returning to a paused
   background event), zoom in so the event (e.g. the monster sprite) is clearly visible.

## Context (read only these parts)
- `MapView.kt`: fields `scale` (1..8, 1 = whole map fits the width), `panX`, `panY`,
  `tileSize` (= `width / cols`), `navLocked`; `onSizeChanged` (line ~114),
  `centerOnTile(tx, ty)` (line ~73), `clampPan()` (line ~298).
- `Panels.kt` `autoJumpTo(x, y)` (line ~106) calls `mapView.centerOnTile(x, y)`.
- `EventIntent.kt` `handleEventIntent` calls `mapView.centerOnTile(x, y)`.
- `BackgroundPlay.kt` `resumeFromBackgroundPlay` calls `mapView.centerOnTile(res.x, res.y)`.
- `MainActivity.kt` `onCreate`: `buildLayout()` then later `setContentView(...)` happens
  inside `buildLayout`.

## Interface

### 1. `MapView.kt` — additions only (copy exactly)
```kotlin
    private var fillOnLayout = false

    /** Zoom so the map fills the whole view (no bands), centred. Safe to call before layout. */
    fun zoomToFill() {
        if (width == 0 || height == 0) { fillOnLayout = true; return }
        tileSize = width.toFloat() / cols
        if (!navLocked) scale = maxOf(1f, height / (tileSize * rows)).coerceIn(1f, 8f)
        panX = (width - tileSize * cols * scale) / 2f
        panY = (height - tileSize * rows * scale) / 2f
        clampPan(); invalidate()
    }

    /** Centre on a tile, zooming in to at least `minScale` (unless navigation is locked). */
    fun zoomToTile(tx: Int, ty: Int, minScale: Float = 3f) {
        if (!navLocked && scale < minScale) scale = minScale.coerceIn(1f, 8f)
        centerOnTile(tx, ty)
    }
```
In `onSizeChanged`, after the existing lines, add:
```kotlin
        if (fillOnLayout) { fillOnLayout = false; zoomToFill() }
```

### 2. Call sites — replace one call each
- `Panels.kt` `autoJumpTo`: `mapView.centerOnTile(x, y)` → `mapView.zoomToTile(x, y)`.
- `EventIntent.kt` `handleEventIntent`: `mapView.centerOnTile(x, y)` → `mapView.zoomToTile(x, y)`.
- `BackgroundPlay.kt`: `mapView.centerOnTile(res.x, res.y)` → `mapView.zoomToTile(res.x, res.y)`.

### 3. `MainActivity.kt` `onCreate`
Right after the line `buildLayout()`, add `mapView.zoomToFill()`.

## Files in scope
`MapView.kt`, `Panels.kt`, `EventIntent.kt`, `BackgroundPlay.kt`, `MainActivity.kt` — only the
changes above.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Planner verifies on device: the map fills its area at startup; Disasters → Monster zooms
  in on the monster.

## Constraints
- **Copy the code blocks above exactly.** Change nothing else; do not re-indent. Do not edit this spec.
- Do not read engine sources or git history; read only the lines named above.
- Commit when green; stage only your files by name. Do not use `git checkout`, `git reset`, or `git add -A`.
