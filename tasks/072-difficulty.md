# Task 072: Difficulty (Easy / Medium / Hard) on the New map screen

## Goal
The player selects Easy, Medium or Hard for a new map. The level sets the engine
game level and the starting money. Disaster frequency stays a separate setting.

## Context
- The engine has three game levels (0 Easy, 1 Medium, 2 Hard). The level changes:
  - Starting money: $20,000 / $10,000 / $5,000 (the engine's `setGameLevelFunds`).
  - Tax income: × 1.4 / 1.2 / 0.8 (`FLevels`).
  - Road and rail upkeep: × 0.7 / 0.9 / 1.2 (`RLevels`).
  - Outside demand for industry: × 1.2 / 1.1 / 0.98.
  - Taxes slow growth more: the engine adds the level to the tax rate for growth.
- The engine saves the level in the `.cty` file. Scenarios and sample cities keep the
  level from their file. This task changes only the New map tab.
- The app does not use the engine's random disasters (task 051). The level does not
  change the app's disaster roll.
- The C-ABI already has `void micropolis_set_game_level(MicropolisEngine *e, int level);`
  (`android/engine/include/micropolis_c.h` line 55). There is no JNI binding yet.
- `NewGame.kt` line 51: `startNewMap` always sets $20,000.
- `NewGameScreen.kt` lines 20–99: the New map tab. It builds one card row per entry
  in `options` (lines 25–30). `lastSelected` (line 21) keeps the selected card per row.
  Line 73 selects card 1 in each row. Lines 94–95 read the values and call `startNewMap`.

## Interface (copy exactly)

### 1. JNI: `android/app/src/main/cpp/micropolis_jni.cpp`
Add immediately after the `setAutoBudget` function (lines 220–223):
```cpp
JNIEXPORT void JNICALL
Java_micropolis_port_MicropolisNative_setGameLevel(JNIEnv *, jobject, jlong h, jint level) {
    micropolis_set_game_level(eng(h), level);
}
```

### 2. `MicropolisNative.kt`
Add immediately after the line `external fun setAutoBudget(handle: Long, on: Int)`:
```kotlin
    /** Game level: 0 Easy, 1 Medium, 2 Hard. Saved in the .cty. */
    external fun setGameLevel(handle: Long, level: Int)
```

### 3. `NewGame.kt`
- Change the `startNewMap` signature to:
  ```kotlin
  internal fun MainActivity.startNewMap(trees: Int, lakes: Int, river: Int, island: Int, level: Int, onDone: () -> Unit) {
  ```
- Replace line 51 (`MicropolisNative.setFunds(handle, 20_000) ...`) with these two lines:
  ```kotlin
        MicropolisNative.setGameLevel(handle, level)
        MicropolisNative.setFunds(handle, intArrayOf(20_000, 10_000, 5_000)[level])   // generating keeps the old city's money
  ```
- Add one line to the KDoc of `startNewMap`: ` * level: 0 Easy, 1 Medium, 2 Hard (sets the starting money too).`

### 4. `NewGameScreen.kt`
- Line 21: change `IntArray(4)` to `IntArray(5)`.
- In `options` (lines 25–30), add a fifth entry after the `Island` entry:
  ```kotlin
            Triple("Difficulty", arrayOf("Easy", "Medium", "Hard"), intArrayOf(0, 1, 2))
  ```
  (Add the comma after the `Island` entry.)
- In the `when (idx)` block (lines 60–66), add `4 -> "⭐"` before the `else` line.
- Replace line 73 with:
  ```kotlin
            select(if (idx == 4) 0 else 1)          // Difficulty starts on Easy
  ```
- Replace lines 94–95 with:
  ```kotlin
                val v = IntArray(5) { optionValues[it][lastSelected[it]] }
                startNewMap(v[0], v[1], v[2], v[3], v[4], resume)
  ```

### 5. `android/app/src/main/assets/manual/tips.html`
Add this section immediately before the line `<h2>While the app is closed</h2>`:
```html
<h2>Difficulty</h2>
<p>New game → New map lets you select Easy, Medium or Hard. The city keeps its
difficulty when you save it.</p>
<ul>
 <li><b>Easy:</b> $20,000 to start. Taxes bring in the most money. Roads cost the least.</li>
 <li><b>Medium:</b> $10,000 to start.</li>
 <li><b>Hard:</b> $5,000 to start. Taxes bring in less money, roads cost more, and
   high taxes slow growth more.</li>
</ul>
<p>Difficulty does not change how often disasters happen. Simulation → Disasters controls that.</p>
```

## Files in scope
- `android/app/src/main/cpp/micropolis_jni.cpp`: add the JNI function.
- `android/app/src/main/java/micropolis/port/MicropolisNative.kt`: add the `external fun`.
- `android/app/src/main/java/micropolis/port/NewGame.kt`: `startNewMap` only.
- `android/app/src/main/java/micropolis/port/NewGameScreen.kt`: the lines named above only.
- `android/app/src/main/assets/manual/tips.html`: add the section.

## Definition of done
- Build: `make build`. It must succeed.
- Check: `grep -n "setGameLevel" android/app/src/main/java/micropolis/port/NewGame.kt` prints one line.
- Check: `grep -rn "startNewMap(" android/app/src/main/java` shows 2 lines (the definition
  and the call), and the call has 6 arguments.
- Check: `grep -c "<h2>Difficulty</h2>" android/app/src/main/assets/manual/tips.html` prints `1`.

## Constraints
- Do not change the engine `.cpp` sources or `micropolis_c.h` / `micropolis_c.cpp`.
- Do not change the Scenarios or Cities tabs.
- Do not change the other manual files.
- Do not add a new dependency.
