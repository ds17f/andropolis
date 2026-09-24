# Task 066: City → Conditions shows the engine's real city averages

## Goal
The Conditions tab shows crime, pollution, land value, traffic and density as the
engine sees them (averaged over developed land only), with correct good/bad colours.

## Context
- Today `showCityPanel()` (`android/app/src/main/java/micropolis/port/CityPanels.kt`
  lines 22-40) calls `avgOverlay(kind)`, which averages an overlay over ALL
  120×100 tiles, water and empty land too. A small city is always "None"/"Low".
- The engine keeps its own averages over developed land (land value > 0). The
  citizens' poll uses them (`evaluate.cpp` line 250). Fields on `Micropolis`
  (`e->sim`): `crimeAverage` (0-250), `pollutionAverage` (0-255),
  `landValueAverage` (1-250). Traffic: `Micropolis::getTrafficAverage()` in
  `evaluate.cpp` lines 310-334 — it WRITES `trafficAverage`, so do not call it;
  copy its loop (see Interface).
- Pattern to copy: `micropolis_get_evaluation` — header
  `android/engine/include/micropolis_c.h` lines 101-106, impl
  `android/engine/src/micropolis_c.cpp` lines 221-227, JNI
  `android/app/src/main/cpp/micropolis_jni.cpp` lines 108-115, Kotlin
  `MicropolisNative.kt` lines 36-39.

## Interface (copy exactly)
```c
/* micropolis_c.h — add after micropolis_get_evaluation */
/* ---- City conditions (City → Conditions). All 0..255, averaged over developed
 * land (land value > 0), the same numbers the citizens' poll uses. ---- */
typedef struct MicropolisConditions {
    int crime; int pollution; int land_value; int traffic; int density;
} MicropolisConditions;   /* 5 ints */
void micropolis_get_conditions(const MicropolisEngine *e, MicropolisConditions *out);
```
```cpp
// micropolis_c.cpp — add after micropolis_get_evaluation
void micropolis_get_conditions(const MicropolisEngine *e, MicropolisConditions *out) {
    if (!e || !out) return;
    Micropolis *s = e->sim;
    out->crime = int(s->crimeAverage);
    out->pollution = int(s->pollutionAverage);
    out->land_value = int(s->landValueAverage);
    // Same loop as Micropolis::getTrafficAverage(), without writing trafficAverage.
    long traffic = 0, density = 0; int count = 0;
    for (int x = 0; x < WORLD_W; x += s->landValueMap.MAP_BLOCKSIZE) {
        for (int y = 0; y < WORLD_H; y += s->landValueMap.MAP_BLOCKSIZE) {
            if (s->landValueMap.worldGet(x, y) > 0) {
                traffic += s->trafficDensityMap.worldGet(x, y);
                density += s->populationDensityMap.worldGet(x, y);
                count++;
            }
        }
    }
    int t = count ? int(traffic / count * 24 / 10) : 0;
    int d = count ? int(density / count) : 0;
    out->traffic = t > 255 ? 255 : t;
    out->density = d > 255 ? 255 : d;
}
```
```cpp
// micropolis_jni.cpp — add after getEvaluation
JNIEXPORT void JNICALL
Java_micropolis_port_MicropolisNative_getConditions(JNIEnv *env, jobject, jlong h, jintArray dst) {
    if (env->GetArrayLength(dst) < 5) return;
    MicropolisConditions c; micropolis_get_conditions(eng(h), &c);
    jint t[5] = { c.crime, c.pollution, c.land_value, c.traffic, c.density };
    env->SetIntArrayRegion(dst, 0, 5, t);
}
```
```kotlin
// MicropolisNative.kt — add after getEvaluation
    /**
     * City conditions, 0..255, averaged over developed land. Fills dst (length >= 5):
     * [crime, pollution, landValue, traffic, density].
     */
    external fun getConditions(handle: Long, dst: IntArray)
```

## Files in scope
- `android/engine/include/micropolis_c.h`: add the struct and prototype above.
- `android/engine/src/micropolis_c.cpp`: add the function above. If `WORLD_W`/
  `WORLD_H` do not compile, use `MICROPOLIS_MAP_W`/`MICROPOLIS_MAP_H`.
- `android/app/src/main/cpp/micropolis_jni.cpp`: add the JNI function above.
- `android/app/src/main/java/micropolis/port/MicropolisNative.kt`: add the declaration above.
- `android/app/src/main/java/micropolis/port/CityPanels.kt`:
  - `showCityPanel()`: replace the two `avgOverlay` lines with
    `val c = IntArray(5); MicropolisNative.getConditions(handle, c)` and pass
    `c[0], c[1], c[2], c[3], c[4]` as crime, poll, land, traffic, density.
  - Delete the function `avgOverlay` (nothing else uses it).
  - `statBar`: add a parameter `highIsGood: Boolean = false`. When true, use the
    colours the other way round: `v < 77` red `0xFFE5533D`, `v < 160` amber
    `0xFFF5A623`, else green `0xFF4CAF50`.
  - In the Conditions tab: `statBar("Land value", land, highIsGood = true)`.

Change only these files.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- `grep -n avgOverlay android/app/src/main/java/micropolis/port/*.kt` prints nothing.
- Planner verifies on device: a small city shows non-zero crime/land value; land
  value colour is green when high.

## Constraints
- Do not change the engine `.cpp` sources under `MicropolisCore/`.
- Do not add a new dependency.
- Do not change the other City tabs.
