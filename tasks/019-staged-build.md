# Task 019: Staged build (preview → confirm / cancel)

## Goal
A build mode that does not commit immediately: in Preview mode, taps/drags mark
tiles as pending (shown as a highlight overlay) without changing the city. A
Confirm button applies them all; Cancel discards them.

## Context
`MainActivity` handles `mapView.onTileTap` (currently posts `doTool` to the sim
thread). `MapView` draws the map through its `scale`/`panX`/`panY` transform in
`onDraw`. This is UI-only — no engine/JNI change. `currentTool` holds the selected
tool.

## Interface

### MapView.kt
- Add:
  ```kotlin
  private var pendingTiles: List<Pair<Int, Int>> = emptyList()
  private val overlayPaint = Paint().apply { color = 0x88FFEB3B.toInt() } // translucent yellow
  fun setPendingTiles(tiles: List<Pair<Int, Int>>) { pendingTiles = tiles; invalidate() }
  ```
- In `onDraw`, AFTER the tile-drawing loop but BEFORE `canvas.restore()`, draw the
  pending overlay (same transform, so it lines up with tiles):
  ```kotlin
  for ((px, py) in pendingTiles) {
      canvas.drawRect(px * tileSize, py * tileSize,
                      (px + 1) * tileSize, (py + 1) * tileSize, overlayPaint)
  }
  ```

### MainActivity.kt
- Add fields:
  ```kotlin
  private var previewMode = false
  private val pending = mutableListOf<Triple<Int, Int, Int>>()  // x, y, tool
  ```
- Change the `onTileTap` handler to branch on preview mode:
  ```kotlin
  mapView.onTileTap = { tileX, tileY ->
      if (previewMode) {
          pending.add(Triple(tileX, tileY, currentTool))
          mapView.setPendingTiles(pending.map { it.first to it.second })
      } else {
          val tool = currentTool
          sim.post { MicropolisNative.doTool(handle, tool, tileX, tileY) }
      }
  }
  ```
- Add three buttons to `barLayout` (after the Eval button):
  ```kotlin
  val previewBtn = Button(this).apply {
      text = "Preview: Off"
      setOnClickListener {
          previewMode = !previewMode
          text = if (previewMode) "Preview: On" else "Preview: Off"
      }
  }
  val confirmBtn = Button(this).apply {
      text = "Confirm"
      setOnClickListener {
          val snapshot = pending.toList()
          pending.clear(); mapView.setPendingTiles(emptyList())
          sim.post { for (t in snapshot) MicropolisNative.doTool(handle, t.third, t.first, t.second) }
      }
  }
  val cancelBtn = Button(this).apply {
      text = "Cancel"
      setOnClickListener { pending.clear(); mapView.setPendingTiles(emptyList()) }
  }
  barLayout.addView(previewBtn); barLayout.addView(confirmBtn); barLayout.addView(cancelBtn)
  ```

## Files in scope
- `android/app/src/main/java/micropolis/port/MapView.kt`: edit.
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Do not run the emulator; the planner verifies preview/confirm/cancel on device.

## Constraints
- Change only the two files above. Do not change the JNI, C ABI, CMake, engine.
- No new dependencies. Commit when green; stage only your files by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
