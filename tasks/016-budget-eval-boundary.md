# Task 016: Budget + evaluation boundary (C ABI → JNI → Kotlin)

## Goal
Expose the city budget and the city evaluation to Kotlin, following the exact
pattern of `getStats` (fill an `IntArray`).

## Context
`getStats` already shows the pattern: a C-ABI function fills a struct, the JNI
unpacks it into a `jint[]`, Kotlin calls it with an `IntArray`. Do the same for
budget and evaluation. The Micropolis engine fields are public (as `getStats`
reads them directly). Apply the design below exactly.

## Interface (apply exactly)

### 1. `android/engine/include/micropolis_c.h`
After the `MicropolisStats` block, add two structs + two declarations:
```c
typedef struct MicropolisBudget {
    int total_funds; int tax_rate; int tax_income;
    int road_fund; int road_spend; int road_percent;
    int police_fund; int police_spend; int police_percent;
    int fire_fund; int fire_spend; int fire_percent;
} MicropolisBudget;   /* 12 ints */
void micropolis_get_budget(const MicropolisEngine *e, MicropolisBudget *out);

typedef struct MicropolisEvaluation {
    int city_score; int score_delta; int city_class;
    int city_pop; int pop_delta; int assessed_value; int approval;
} MicropolisEvaluation;   /* 7 ints */
void micropolis_get_evaluation(const MicropolisEngine *e, MicropolisEvaluation *out);
```

### 2. `android/engine/src/micropolis_c.cpp`
Add (map each field from the engine; percents are floats 0..1, scale to 0..100):
```cpp
void micropolis_get_budget(const MicropolisEngine *e, MicropolisBudget *out) {
    if (!e || !out) return;
    Micropolis *s = e->sim;
    out->total_funds = int(s->totalFunds); out->tax_rate = int(s->cityTax); out->tax_income = int(s->taxFund);
    out->road_fund = int(s->roadFund);   out->road_spend = int(s->roadSpend);   out->road_percent = int(s->roadPercent * 100);
    out->police_fund = int(s->policeFund); out->police_spend = int(s->policeSpend); out->police_percent = int(s->policePercent * 100);
    out->fire_fund = int(s->fireFund);   out->fire_spend = int(s->fireSpend);   out->fire_percent = int(s->firePercent * 100);
}

void micropolis_get_evaluation(const MicropolisEngine *e, MicropolisEvaluation *out) {
    if (!e || !out) return;
    Micropolis *s = e->sim;
    out->city_score = int(s->cityScore); out->score_delta = int(s->cityScoreDelta); out->city_class = int(s->cityClass);
    out->city_pop = int(s->cityPop); out->pop_delta = int(s->cityPopDelta);
    out->assessed_value = int(s->cityAssessedValue); out->approval = int(s->cityYes);
}
```
(`e->sim` is a `Micropolis *`; the const on `e` does not matter here since the
existing code already `const_cast`s in similar readers — use `e->sim` directly.)

### 3. `android/app/src/main/cpp/micropolis_jni.cpp`
Mirror the `getStats` JNI. Add:
```cpp
JNIEXPORT void JNICALL
Java_micropolis_port_MicropolisNative_getBudget(JNIEnv *env, jobject, jlong h, jintArray dst) {
    if (env->GetArrayLength(dst) < 12) return;
    MicropolisBudget b; micropolis_get_budget(eng(h), &b);
    jint t[12] = { b.total_funds, b.tax_rate, b.tax_income,
                   b.road_fund, b.road_spend, b.road_percent,
                   b.police_fund, b.police_spend, b.police_percent,
                   b.fire_fund, b.fire_spend, b.fire_percent };
    env->SetIntArrayRegion(dst, 0, 12, t);
}

JNIEXPORT void JNICALL
Java_micropolis_port_MicropolisNative_getEvaluation(JNIEnv *env, jobject, jlong h, jintArray dst) {
    if (env->GetArrayLength(dst) < 7) return;
    MicropolisEvaluation ev; micropolis_get_evaluation(eng(h), &ev);
    jint t[7] = { ev.city_score, ev.score_delta, ev.city_class,
                  ev.city_pop, ev.pop_delta, ev.assessed_value, ev.approval };
    env->SetIntArrayRegion(dst, 0, 7, t);
}
```

### 4. `android/app/src/main/java/micropolis/port/MicropolisNative.kt`
Add:
```kotlin
/** Fills dst (>=12): [totalFunds, taxRate, taxIncome, roadFund, roadSpend, roadPct,
 *  policeFund, policeSpend, policePct, fireFund, fireSpend, firePct]. */
external fun getBudget(handle: Long, dst: IntArray)
/** Fills dst (>=7): [score, scoreDelta, cityClass, pop, popDelta, assessedValue, approval]. */
external fun getEvaluation(handle: Long, dst: IntArray)
```

## Files in scope
- `android/engine/include/micropolis_c.h`
- `android/engine/src/micropolis_c.cpp`
- `android/app/src/main/cpp/micropolis_jni.cpp`
- `android/app/src/main/java/micropolis/port/MicropolisNative.kt`

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.

## Constraints
- Change only the four files above. Do not change the CMake files, the engine,
  the tests, `MapView`, or `MainActivity`.
- No new dependencies. Commit when green; stage only your files by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
