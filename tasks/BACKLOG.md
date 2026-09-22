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

## Navigate vs. interact (disambiguate pan/zoom from building)

Added 2026-09-22. Right now a tap builds and a drag pans, which is ambiguous and
imprecise — easy to build when you meant to move the map. Need a clear way to
separate **moving the map** from **interacting with the land**. Options to weigh:
- An explicit **mode toggle** (Move ↔ Build) in the UI.
- A gesture split (e.g. one finger pans, long-press-then-drag builds; or a build
  cursor/crosshair you position, then confirm).
This pairs naturally with the staged build-mode item above.

## UI completion and game options

Added 2026-09-22. "The UI stuff needs love." Not urgent, but needed to *complete
the game*:
- **Finish the bottom menu.** The tool bar is incomplete/cut off. Show all tools,
  indicate the currently selected tool, consider icons over text, group sensibly.
- **Speed of play** control — pause / slow / medium / fast (engine `setSpeed`).
- **Tax rate** control (engine `setCityTax`).
- **Overlay / popup windows** that complete the classic game:
  - Budget window (tax + spending sliders).
  - City evaluation window (mayor rating, problems, stats).
  - Graphs / history (population, funds, crime, pollution over time).
  - Map overlays (power grid, crime, pollution, land value, pop density, traffic).
  - Messages / notifications feed; disasters menu.
  - New city / regenerate; save / load.
- **General polish pass** — layout, spacing, theming, funds/date formatting,
  selected-tool highlight.

These break into several `tasks/NNN-*.md` dispatches (Opus specs each, qwen builds).
