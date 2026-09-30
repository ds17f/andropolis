# Task 071: Fewer random disasters, one rate table, and a help section

## Goal
Make random disasters less frequent, keep the rates in one place, and tell the
player the rates in the in-app help.

## Context
- Tester report: disasters happen too often on "Normal".
- Now: the rates are in two places, and they must always agree:
  - `MainActivity.kt` line 68: `disasterYearsPer = intArrayOf(0, 10, 5, 1)`, which
    `SimLoop.kt` lines 132–143 use for the foreground roll.
  - `DisasterRoll.kt` line 9: `yearsPer = intArrayOf(0, 10, 5, 1)`, which
    `BackgroundSim.kt` uses for background play.
- After this task, `DisasterRoll` is the only place that has the rates. The foreground
  loop calls `DisasterRoll.roll` with a new random seed each month, so the foreground
  stays random.
- Help: `android/app/src/main/assets/manual/tips.html` is the app's own help page.
  The other files in `assets/manual/` are the original manual. Do not change them.
  The original manual says that disasters happen more often at higher game levels.
  That is not true in this app, so the tips page must say this.

## Interface (copy exactly)

### 1. `DisasterRoll.kt`
Change line 9 to:
```kotlin
    /** Average game years between random disasters, per frequency (Off, Rare, Normal, Frequent).
     *  The help page (assets/manual/tips.html, "Disasters") shows these numbers. Change both together. */
    val yearsPer = intArrayOf(0, 20, 10, 3)
```
(It is now public, not `private`.)

### 2. `SimLoop.kt`: replace lines 136–140
Replace this block:
```kotlin
            if (random.nextInt(disasterYearsPer[disasterFreq] * 12) == 0) {
                // weight like the original: fires most common, meltdown rarest
                val kind = intArrayOf(0, 0, 1, 2, 3, 4, 0, 5)[random.nextInt(8)]
                MicropolisNative.makeDisaster(handle, kind)
            }
```
with:
```kotlin
            val kind = DisasterRoll.roll(random.nextLong(), monthKey, disasterFreq)
            if (kind >= 0) MicropolisNative.makeDisaster(handle, kind)
```

### 3. `MainActivity.kt`
Delete line 68 (`internal val disasterYearsPer = ...`). Nothing else uses it.

### 4. `tips.html`: add this section
Put it immediately before the line `<h2>While the app is closed</h2>`:
```html
<h2>Disasters</h2>
<p>Simulation → Disasters sets how often random disasters happen. Each game month has a
small chance of a disaster. The numbers below are averages in game years, not a schedule:
two disasters can happen close together. At fast speed, a game year passes in a short time.</p>
<ul>
 <li><b>Off:</b> no random disasters.</li>
 <li><b>Rare:</b> about one in 20 game years.</li>
 <li><b>Normal:</b> about one in 10 game years.</li>
 <li><b>Frequent:</b> about one in 3 game years.</li>
</ul>
<p>Fire is the most usual disaster. A meltdown is the least usual. You can start any
disaster yourself from the same tab. This setting also applies while the app is closed.</p>
<p class="muted">The original manual says that disasters happen more often at higher game
levels. In this app, only this setting controls it.</p>
```

## Files in scope
- `android/app/src/main/java/micropolis/port/DisasterRoll.kt`: edit line 9.
- `android/app/src/main/java/micropolis/port/SimLoop.kt`: replace lines 136–140.
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: delete line 68.
- `android/app/src/main/assets/manual/tips.html`: add the section.

## Definition of done
- Build: `make build`. It must succeed.
- Check: `grep -rn "disasterYearsPer" android/app/src/main/java` prints nothing.
- Check: `grep -n "intArrayOf(0, 20, 10, 3)" android/app/src/main/java/micropolis/port/DisasterRoll.kt`
  prints one line.
- Check: `grep -c "<h2>Disasters</h2>" android/app/src/main/assets/manual/tips.html` prints `1`.

## Constraints
- Do not change the engine `.cpp` sources.
- Do not change `BackgroundSim.kt`. It already uses `DisasterRoll`.
- Do not change the manual files other than `tips.html`.
- Do not add a new dependency.
