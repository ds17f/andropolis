# Roadmap — ordered working backlog

Chew top to bottom. Each `[ ]` is roughly one qwen dispatch (Opus specs + reviews +
verifies on device). Mark `[x]` when merged & verified, `[~]` when in flight.
Deeper design notes for the big items live in `BACKLOG.md`.

## 1. Make it playable  (unblocks growing a city → unblocks playtest)
- [x] 010 — complete the tool bar (power plants) + Move/Build toggle  ✅ merged
- [ ] Verify precise placement on device; tweak tap targeting / zoom if needed

## 2. Save / load
- [x] 011 — C-ABI **save** function + JNI + Kotlin for save & load  ✅ merged
- [x] 012 — Save / Load / New buttons (city.cty in filesDir)  ✅ merged
      (code-verified + builds/runs; on-device round-trip to be confirmed by touch)

## 3. Playtest milestone
- [ ] Build coal plant + residential zones + roads, run the sim, confirm
      **population grows** (on device). Proves the game loop end-to-end.

## 4. UI completion & game options
- [ ] Speed of play control — pause / slow / med / fast (`setSpeed`)
- [ ] Tax rate control (`setCityTax`)
- [ ] Selected-tool highlight + menu polish (icons / grouping)
- [ ] Budget window
- [ ] City evaluation window (rating, problems, stats)
- [ ] Map overlays (power, crime, pollution, land value, pop density, traffic)
- [ ] Graphs / history window
- [ ] Messages / notifications feed

## 5. Better input  (see BACKLOG.md)
- [ ] Drag-to-build roads / rail / wire (`toolDrag`)
- [ ] Staged build mode: preview + confirm/cancel with cost (`ToolEffects`).
      Opus designs the C-ABI staged-effects extension.

## 6. Engine → host events  (needs the callback plumbing; Opus designs C-ABI/JNI)
- [ ] Wire the key callbacks (funds / date / message / sound)
- [ ] Sounds
- [ ] Disasters menu / triggers

---
Method reminder: qwen (coder-next) does the coding; Opus specs + reviews. Android
tasks run in the main tree (not `--isolate`). Dispatch via `run_in_background`,
never a trailing `&`. See `CLAUDE.md`.
