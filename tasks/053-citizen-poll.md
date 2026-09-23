# Task 053: Citizen poll — "What are the worst problems?"

## Goal
Bring back the classic City Evaluation poll: approval (Yes / No) and the citizens' ranked
worst problems (Crime, Pollution, Housing, Taxes, Traffic, Unemployment, Fire) with the
share of citizens naming each. Show it on City → Overview and the top two on the Annual Report.

## Context
- C-ABI header declares `int micropolis_get_problems(const MicropolisEngine *e, int *ids, int *votes, int n);`
  (see its comment in `android/engine/include/micropolis_c.h`). Implement in
  `android/engine/src/micropolis_c.cpp`:
  ```cpp
  int micropolis_get_problems(const MicropolisEngine *e, int *ids, int *votes, int n) {
      if (!e || !ids || !votes || n <= 0) return 0;
      Micropolis *s = e->sim;
      int count = s->countProblems();
      if (count > n) count = n;
      for (int i = 0; i < count; i++) { ids[i] = s->getProblemNumber(i); votes[i] = s->getProblemVotes(i); }
      return count;
  }
  ```
  (If `countProblems()` etc. aren't callable on a const engine, use `e->sim` as non-const
  like the existing `get_budget` does.)
- JNI (`micropolis_jni.cpp`): `getProblems(handle: Long, out: IntArray): Int` — `out` has
  length ≥ 8; fill `out[0..3]` = ids, `out[4..7]` = votes; return the count. Copy the
  `getEvaluation` array style. Kotlin `external fun getProblems(handle: Long, out: IntArray): Int`.
- `MainActivity.showCityPanel()` gathers data on `sim` then calls `showCityPanelUI(...)`;
  the Overview tab shows rows then `statBar(label, v0to255)` bars. `ev[6]` = approval %.
  `showReportCard(year, resume)` builds the annual card with `styledDialog(title, rows, …)`.

## Interface (MainActivity)
- Names + icons:
  ```kotlin
  private val problemNames = arrayOf("Crime", "Pollution", "Housing", "Taxes", "Traffic", "Unemployment", "Fire")
  private val problemIcons = arrayOf("🚨", "☁", "🏠", "💰", "🚗", "👷", "🔥")
  ```
- `showCityPanel()`: also call `val p = IntArray(8); val np = MicropolisNative.getProblems(handle, p)`
  and pass `p` and `np` through to `showCityPanelUI`.
- Overview tab, after the existing stat bars, add a section:
  - Title "What citizens say" (muted, 11sp, like the Budget "Projection" label).
  - "Is the mayor doing a good job?" then a split bar: green `Yes ev[6]%` / red `No (100-ev[6])%`
    (a horizontal LinearLayout with two weighted Views, rounded ends, height 10dp) and the
    two percentages as text under it.
  - "Worst problems": for each of the `np` entries, a row `icon  Name` on the left and `NN%`
    bold on the right, with a thin amber bar under it whose weight = votes (out of 100).
  - If `np == 0`: muted text "No survey yet — check back after the next evaluation."
- Annual report (`showReportCard`): fetch problems in its `sim.post` block the same way and
  add a row `"Top concerns"` to `"${icon} ${name} ${votes}%, ${icon} ${name} ${votes}%"`
  (first two; omit the row if none).

## Files in scope
- `android/engine/src/micropolis_c.cpp`, `android/app/src/main/cpp/micropolis_jni.cpp`,
  `MicropolisNative.kt`, `MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Planner verifies on device: City → Overview shows the Yes/No split and ranked concerns on a
  developed city; the Annual Report shows the top two.

## Constraints
- **Copy the code blocks above exactly.** Everything you need is in this spec; do not read
  engine sources (`MicropolisCore/`) or git history to re-derive it. Only ADD code — do not
  delete or restructure existing code.
- Do not change the C-ABI header (done), engine sources, CMake, or `build.gradle.kts`.
- Commit when green; stage only your files by name. Do not use `git checkout`, `git reset`, or `git add -A`.
