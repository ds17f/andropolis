# Task 028: JNI data layer — overlays, history, disasters, funding

## Goal
Implement the four new C-ABI functions already declared in
`android/engine/include/micropolis_c.h` (overlays, history graphs, disasters,
budget funding), wrap them in JNI, and expose them in `MicropolisNative.kt`. This
is the data the new UI panels need. The header is the CONTRACT — do NOT change it;
implement exactly the signatures and semantics it documents.

## Context
The engine wrapper is `struct MicropolisEngine { Micropolis *sim; ... }`; every
existing function reaches the engine through `e->sim->...`. Copy the style of the
EXISTING functions:
- struct-fill template: `micropolis_get_budget` in `micropolis_c.cpp`.
- buffer-copy template: `micropolis_copy_tiles` in `micropolis_c.cpp` (column-major
  `dst[x * H + y]`, bounds check `dst_len`).
- JNI array templates: `getBudget` / `copyTiles` in `micropolis_jni.cpp`
  (`GetArrayLength`, `Get*ArrayElements` / `SetIntArrayRegion`, `eng(h)` helper).

The engine (`MicropolisCore/packages/micropolis-engine/src/micropolis.h`) already
provides everything — use these members/methods on `e->sim`:
- Overlay maps (call `.worldGet(x, y)` with WORLD tile coords 0..W-1/0..H-1; it
  handles the lower resolution internally, returns the cell value):
  `populationDensityMap`, `trafficDensityMap`, `pollutionDensityMap`,
  `landValueMap`, `crimeRateMap` (all `Byte`, 0..255),
  `rateOfGrowthMap` (`short`, SIGNED, roughly -200..200),
  `powerGridMap` (`Byte`, 0 or non-zero = powered).
- History: `short getHistory(int historyType, int historyScale, int historyIndex)`.
  Types 0..5 match the C enum (RES,COM,IND,MONEY,CRIME,POLLUTION). Scales 0=short,
  1=long. `getHistory(type, scale, 0)` is the MOST RECENT sample; valid indices go
  back at least 120.
- Disasters (void, no args): `makeFire()`, `makeFlood()`, `makeTornado()`,
  `makeEarthquake()`, `makeMonster()`, `makeMeltdown()`.
- Funding: public floats `roadPercent`, `firePercent`, `policePercent` (0.0..1.0);
  `void setAutoBudget(bool)`.

## Interface — implement EXACTLY these (already in the header)

### 1. `micropolis_copy_overlay(const MicropolisEngine *e, int overlay, unsigned char *dst, int dst_len)`
- Guard: return 0 if `!e || !dst || dst_len < W*H` or `overlay == 0` (NONE).
- Column-major loop like `copy_tiles`. For each tile (x,y) write a byte 0..255:
  - POPULATION/TRAFFIC/POLLUTION/LANDVALUE/CRIME(1..5): the map's
    `worldGet(x,y)` value (already 0..255) — cast to `unsigned char`.
  - GROWTH(6): `rateOfGrowthMap.worldGet(x,y)` is signed; map to 0..255 with 128 ==
    no growth: `clamp(128 + value, 0, 255)`.
  - POWER(7): `powerGridMap.worldGet(x,y) ? 255 : 0`.
- Return `W*H`.

### 2. `micropolis_get_history(const MicropolisEngine *e, int history, int scale, int *dst, int dst_len)`
- Guard: return 0 if `!e || !dst || dst_len < MICROPOLIS_HISTORY_LEN` or
  history/scale out of range.
- Fill OLDEST-FIRST so `dst[LEN-1]` is newest:
  `dst[i] = getHistory(history, scale, (MICROPOLIS_HISTORY_LEN - 1) - i)`.
- Return `MICROPOLIS_HISTORY_LEN`.

### 3. `micropolis_make_disaster(MicropolisEngine *e, int disaster)`
- Guard `!e`. `switch (disaster)` → the matching `makeX()`; ignore unknown.

### 4. `micropolis_set_funding(...)` and `micropolis_set_auto_budget(...)`
- `set_funding`: guard `!e`; clamp each pct to 0..100;
  `e->sim->roadPercent = road_pct / 100.0f;` (same for fire/police);
  then `e->sim->setAutoBudget(false)`.
- `set_auto_budget`: guard `!e`; `e->sim->setAutoBudget(on != 0)`.

### JNI wrappers (`android/app/src/main/cpp/micropolis_jni.cpp`) — append:
- `copyOverlay(handle, overlay: Int, dst: ByteArray): Int` → `jbyteArray`; use
  `GetByteArrayElements` / `ReleaseByteArrayElements` (jbyte bit pattern == unsigned
  char, `reinterpret_cast<unsigned char*>`), like `copyTiles` does for shorts.
- `getHistory(handle, history: Int, scale: Int, dst: IntArray): Int` → `jintArray`;
  `GetIntArrayElements` / `ReleaseIntArrayElements`.
- `makeDisaster(handle, disaster: Int)` → void.
- `setFunding(handle, roadPct: Int, firePct: Int, policePct: Int)` → void.
- `setAutoBudget(handle, on: Int)` → void.

### `MicropolisNative.kt` — add matching `external fun`s with KDoc:
```kotlin
/** Fill dst (ByteArray, len >= 12000) column-major dst[x*H+y] with 0..255
 *  intensity for the overlay (see MicropolisOverlay). Returns tiles written. */
external fun copyOverlay(handle: Long, overlay: Int, dst: ByteArray): Int
/** Fill dst (len >= 120) oldest-first (dst[119] newest). Returns samples. */
external fun getHistory(handle: Long, history: Int, scale: Int, dst: IntArray): Int
external fun makeDisaster(handle: Long, disaster: Int)
external fun setFunding(handle: Long, roadPct: Int, firePct: Int, policePct: Int)
external fun setAutoBudget(handle: Long, on: Int)
```

## Files in scope
- `android/engine/src/micropolis_c.cpp`: append the 4 C functions (5 symbols).
- `android/app/src/main/cpp/micropolis_jni.cpp`: append the 5 JNI wrappers.
- `android/app/src/main/java/micropolis/port/MicropolisNative.kt`: add 5 `external fun`.
- Do NOT edit `micropolis_c.h` (the contract is fixed) or any engine source.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`
  (this compiles the NDK C++ for arm64-v8a + x86_64 and links the JNI lib).
- No behavior change to existing calls; only additions.

## Constraints
- Change only the three files above. Do not touch the C-ABI header, CMake,
  `build.gradle.kts`, `MapView`, `MainActivity`, or the vendored engine.
- No new dependencies. Commit when green; stage only your files by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
