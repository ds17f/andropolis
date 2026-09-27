# Task 067: Cycle ANIMBIT tile frames every sim tick

## Goal
Tiles with the engine's ANIMBIT set (fire, rubble/explosion, traffic, wires, smoke
stacks, radar dish, nuclear swirl, church, …) advance through their animation frames
instead of staying stuck on the first frame forever.

## Context
- `Micropolis::animateTiles()` (`MicropolisCore/packages/micropolis-engine/src/animate.cpp`
  lines 211-227) walks every tile and, for any tile with `ANIMBIT` set, replaces it with
  `animatedTiles[tile & LOMASK] | (tile & ALLBITS)` — i.e. it steps each animated tile to
  its next frame. It is a pure, cheap, self-contained pass over the map.
- Nobody calls it. Upstream (`MicropolisCore/apps/micropolis/src/lib/MicropolisSimulator.ts`
  line 179) calls `this.micropolis.simTick(); this.micropolis.animateTiles();` as a pair,
  once per tick — that's the TypeScript frontend's own render loop, which this Android
  port does not share code with. This port's own tick wrapper,
  `micropolis_sim_tick` (`android/engine/src/micropolis_c.cpp` lines 123-127), calls
  only `micropolisSimTick(e->sim)` and never touches `animateTiles()`. Grepping this
  repo (`android/`) for `animateTile` confirms zero references.
- Concretely this is why bulldozing a zone leaves the little `TINYEXP` explosion
  tiles it places (`putRubble`, `MicropolisCore/packages/micropolis-engine/src/tool.cpp`
  lines 923-938) stuck on their first frame forever instead of cycling to a settled
  rubble tile: nothing ever advances them.
- `animateTiles()` is a public method on `Micropolis` (declared
  `MicropolisCore/packages/micropolis-engine/src/micropolis.h` line 1429, under the
  `public:` label just above it at line 1425). No new engine changes are needed —
  `android/engine/src/micropolis_c.cpp` is this port's own bridge file, not
  `MicropolisCore/`, so editing it is in scope.
- Match the original pairing: call it once per `simTick()` call, inside the same
  native function, so animation cadence scales with game speed exactly like ticks do
  (a tile only animates as fast as the sim is actually running, same as upstream).

## Interface (copy exactly)
```cpp
// android/engine/src/micropolis_c.cpp — micropolis_sim_tick, add the animateTiles() call
void micropolis_sim_tick(MicropolisEngine *e) {
    if (e) {
        micropolisSimTick(e->sim);   // simTick() that keeps the monster alive (micropolis_seeded.cpp)
        e->sim->animateTiles();      // advance ANIMBIT tile frames (fire, rubble, traffic, wires, …)
    }
}
```

## Files in scope
- `android/engine/src/micropolis_c.cpp`: add the one `animateTiles()` call shown above.
  Change nothing else in this file.

## Definition of done
- Build: `make build`
- Test (manual, no automated test exists for this): `make run`, then in the running
  app: build a small zone (e.g. a road + a residential zone), bulldoze it, and watch
  the tile. It must visibly animate through a short rubble/explosion sequence and
  settle to a static rubble tile within a couple of seconds — not stay on the same
  explosion frame indefinitely. Take a screenshot immediately after bulldozing and
  another ~2 seconds later (`make screenshot`) and confirm the tile differs between
  the two.

## Constraints
- Do not change any file under `MicropolisCore/`.
- Do not add a new dependency.
- Do not touch the JNI layer (`android/app/src/main/cpp/micropolis_jni.cpp`) or Kotlin
  — `micropolis_sim_tick`'s existing signature is unchanged, so nothing above it needs
  to change.
