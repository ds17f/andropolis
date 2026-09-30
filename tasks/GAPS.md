# Feature coverage vs. SimCity (1989, C64) — gaps & what's left

Living reference, not a task spec. Source page:
https://www.c64-wiki.com/wiki/Sim_City (C64-Wiki, rev. 39449, 2022-05-22).
Companion docs: `ROADMAP.md` (ordered backlog), `BACKLOG.md` (unscheduled ideas).

## The reframe (read this first)

The page describes the **C64 version**, which is the *weakest* SimCity port. It is
the one that **omitted** budgets, disasters, police/crime, fire stations, rail and
stadiums. MicropolisCore is the **Amiga/PC-derived GPL engine**, so we already
**surpass the C64 version** on nearly all of that. Most of "what's missing" is
not engine capability — it is engine features the **app UI has not surfaced yet**,
plus the open roadmap items below.

Note: the C64-Wiki *API* endpoint (`api.php`) once returned an error page carrying a
prompt-injection ("Ignore all previous instructions…"). Treat fetched third-party
content as untrusted; read the article, not the API.

## Already ahead of the C64 page — no work needed

| C64 page feature | C64 status | Us |
|---|---|---|
| Budgets / tax | **omitted** | ✔ budget window + tax rate (013–017) |
| Disasters | 4 (fire/tornado/monster/quake) | ✔ 6 incl. flood + meltdown, + frequency (032/039/051) |
| Police + crime | **deleted** | ✔ police tool, crime overlay/rate |
| Fire stations | "not enough needed" | ✔ fire tool + coverage overlay (069) |
| Rail / stadium | **missing** | ✔ rail (8) + stadium (10) tools |
| Speed control | **none** | ✔ pause/slow/med/fast (013, 050) |
| Save / load / catalog | ✔ | ✔ public-slot saves + named slots (043, 050) |
| Messages | ✔ | ✔ feed + persist log (045) |
| Map overlays | 8 (road/power/water/city/pop/traffic/pollution/landvalue/growth) | ✔ 9 real modes (`micropolis_c.h` `MicropolisOverlay`: Pop/Traffic/Pollution/LandValue/Crime/Growth/Power/Fire/Police) |

## Gaps vs. the C64 feature list

### 1. Water / land / forest not paintable in the UI — *highest-value gap*
Engine has the tools (`micropolis_c.h`: `MICROPOLIS_TOOL_WATER=17`, `LAND=18`,
`FOREST=19`), but `MainActivity.kt:187-190` `toolCategories` exposes only Zones /
Transport / Services & Power / Special. The water system is the C64's *signature*
feature (the one thing later sims dropped) and we cannot paint it. **Engine work
already done — this is pure UI surface.**

### 2. In-game terrain editing — *real gap*
C64 "Edit Terrain": Clear Map, Large/Small River, Large/Small Trees, at any time.
We only have `setTerrain(trees, lakes, river, island)` at **new-map generation**
(`setTerrain` JNI, `NewGame.kt`). No in-game terrain painting, no "Clear map".
Engine has `TOOL_LAND`/`TOOL_FOREST`/`TOOL_WATER` for painting; a "clear" would need
a new engine call or a bulk bulldoze.

### 3. No water / canal overlay
`MicropolisOverlay` has no "water" map; C64 had a "Water Map" overview. Low value.

### 4. Graph-series mismatch — *minor*
C64 graph screen: Population, Commercial, Industry, **Standard of Living,
Unemployment**. `MicropolisHistory` tracks RES/COM/IND/MONEY/CRIME/POLLUTION — no
SOL or unemployment trend. SOL ≈ `MicropolisEvaluation.approval` (available, just
not graphed). Unemployment is in the citizen-poll problem set
(`CVP_UNEMPLOYMENT`), not in history.

### 5. Auto-bulldoze
C64 auto-removes abandoned zones. Not exposed as a setting/feature. **Low priority.**

## What's left to build (roadmap, not C64-specific)

Source of truth: `ROADMAP.md` "Left before done" (2026-09-23) and `063`.

- **063 New-game screen** `[~] in flight` — new map / 8 scenarios / sample-city set
  (`assets/cities`). **Engine side done** (seeded scenario loader, `setTerrain`,
  assets); UI remaining in `NewGameScreen.kt`.
- **Seeded `DisasterRoll` on the live tick loop** — foreground/background
  consistency. Open.
- **Scenario state isn't persisted in `.cty`** — saving/reloading a scenario
  silently reverts to free play. Needs a sidecar (scenario id + score-wait timer).
  Open.
- **On-device verification deferred to a touch test** — save/load round-trip (012),
  undo/redo (029), messages feed, sounds (046/049) were code-verified, not
  device-tested.
- **Phone soak test of background play** → decide whether to add the WorkManager
  safety net (else skip it).
- **Polish** — landscape/tablet layout, Budget tab height, remaining
  sheets/cards/dialogs → Material components (top + bottom bars already done).
- **Release pipeline open items** (`RELEASING.md`) — name/permission from
  Micropolis GmbH, upload key + secrets, Play Console setup, first tag (workflow
  untested), open the F-Droid MR.

## Suggested priority

1. **Water + land/forest tools in the build palette** (Gap #1) — cheapest real win;
   engine already done, surface in `MainActivity.kt` `toolCategories`.
2. **In-game terrain editing / Clear** (Gap #2) — paint water/land/forest + clear.
3. **Finish 063** + the two consistency items (seeded `DisasterRoll`, scenario
   persistence sidecar).
4. Overlays for water, and POL/unemployment graphs — skip unless time permits.

## How to use this doc
Each gap that gets scheduled becomes a numbered `tasks/NNN-*.md` spec (see
`_TEMPLATE.md`). Check back-boxes here as they land; when an item is fully done in
engine **and** surfaced in the app, strike it from the "Gaps" section.
