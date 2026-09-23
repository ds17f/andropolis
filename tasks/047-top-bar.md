# Task 047: Top bar redesign

## Goal
The top bar looks off: the Funds / Population / Score values sit in rounded boxes that
look like buttons (they aren't), and the square amber play/pause and the boxed ⋮ look
out of place. Make it a clean, flat header:

```
Silbergopolis                         ( ⏸ )   ⋮
May 2077 · Med
💰 $15,548     👥 25,860     ⭐ 592
```
- Row 1: city name (bold, 20sp) on the left; on the right a **circular** play/pause
  button (40dp, amber fill, dark glyph) and a plain ⋮ icon (no box, 40dp touch target).
- Row 2: date + current speed in muted text ("May 2077 · Med").
- Row 3: the three stats as **plain inline text** — icon + value, no backgrounds, no
  borders, evenly spaced. Funds value amber; the others light.

## Context (MainActivity.kt)
- The top bar is built in `onCreate`: `topBar` (vertical) → `row1` with `cityBlock`
  (`cityTitle`, `subtitle`), `playPauseBtn`, `overflowBtn`; then `row2` with three chips
  (`fundsChip`/`fundsValue`, `popChip`/`popValue`, `scoreChip`/`scoreValue`) that use
  `roundedBg(...)` backgrounds and label TextViews ("Funds", "Population", "Score").
- `tickLoop` updates `subtitle.text = "$monthName $year"`, `fundsValue.text`,
  `popValue.text`, `scoreValue.text`. `updatePlayPauseText()` sets `playPauseBtn.text`.
  `speedNames[speed]` gives the speed name.
- Keep every field name and every click handler; this is a styling/layout change.

## Interface
- Chips → remove their `roundedBg` backgrounds and the "Funds"/"Population"/"Score"
  label TextViews. Each becomes a horizontal LinearLayout: an emoji TextView
  ("💰", "👥", "⭐", 14sp) + the value TextView (16sp bold). Put the three in `row2`
  with equal weight, gravity start/center/end respectively. Format numbers with
  grouping: `"%,d".format(n)`; funds as `"$" + "%,d".format(funds)`.
- `playPauseBtn`: 40dp × 40dp, `background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFF5A623.toInt()) }`,
  no padding, text size 16sp, centered glyph.
- `overflowBtn`: `background = null`, text color `#EEF2F6`, 40dp × 40dp.
- `subtitle`: include the speed — in `tickLoop` set `"$monthName $year · ${speedNames[speed]}"`
  (and refresh it in `updateSpeedChipText()` too so it updates immediately).
- Reduce the top bar's vertical padding so it takes less height (top ~24dp, bottom ~8dp).

## Files in scope
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Planner verifies on device (incl. 1.3× system font): nothing looks like a button except
  play/pause and ⋮; no wrapping.

## Constraints
- Change only `MainActivity.kt`. No behavior changes. No new dependencies.
- Commit when green; stage only your file by name. Do not use `git checkout`,
  `git reset`, or `git add -A`.
