# Backlog (not yet scheduled)

Ideas captured for later. Each becomes a `tasks/NNN-*.md` spec when scheduled.

## Better input system (drag-to-build + staged build mode)

Requested 2026-09-22. "It can wait."

Touch building is currently single-tap, one tile, committed immediately. Two wants:

1. **Drag to build.** Dragging a finger lays a continuous road / rail / wire along
   the path, not one tile per tap.
2. **Staged build mode (preview → confirm / rollback).** Because touch is
   imprecise, let the user compose a build without committing it, see it (and its
   cost), then OK it or roll it back if it is wrong.

**Engine support (this is very doable):** MicropolisCore already separates
computing tool effects from applying them:
- `Micropolis::toolDown` / `toolDrag(tool, fromX, fromY, toX, toY)` — the engine's
  own drag path for road-like tools.
- `ToolEffects` — accumulates the tile changes and the cost of a tool action
  *without* touching the world; `modifyWorld` / `modifyIfEnoughFunding` then apply
  it, or it is discarded. This is exactly the preview/commit/rollback primitive.
- `queryTool` for inspecting a tile.

**Shape of the work:**
- **C ABI + JNI (Opus designs):** extend `micropolis_c.h` beyond `doTool` to expose
  a staged-effects API — begin a build, add tool actions at tiles (drag path),
  read back the pending tile deltas + total cost for overlay rendering, then
  commit or cancel. Map `ToolEffects` to a C-friendly surface (no C++ types leak).
- **UI (qwen):** a build-mode toggle; drag gesture in `MapView` that feeds tile
  coordinates along the path; an overlay that draws pending tiles (e.g. tinted)
  over the committed map; a confirm/cancel bar showing the cost.

**Why staged is nice:** it also gives us undo for free and a natural place to show
"not enough funds" before spending.
