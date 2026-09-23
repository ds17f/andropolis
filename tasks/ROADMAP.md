# Roadmap — ordered working backlog

Chew top to bottom. Each `[ ]` is roughly one qwen dispatch (Opus specs + reviews +
verifies on device). Mark `[x]` when merged & verified, `[~]` when in flight.
Deeper design notes for the big items live in `BACKLOG.md`.

## 1. Make it playable  (unblocks growing a city → unblocks playtest)
- [x] 010 — complete the tool bar (power plants) + Move/Build toggle  ✅ merged
- [x] Verify precise placement on device (place-on-lift ghost 030, straight roads, minimap nav 041)

## 2. Save / load
- [x] 011 — C-ABI **save** function + JNI + Kotlin for save & load  ✅ merged
- [x] 012 — Save / Load / New buttons (city.cty in filesDir)  ✅ merged
      (code-verified + builds/runs; on-device round-trip to be confirmed by touch)

## 3. Playtest milestone
- [x] Build coal plant + zones + roads, run the sim (user's *damesville* reached pop 25k)
      **population grows** (on device). Proves the game loop end-to-end.

## 4. UI completion & game options
- [x] 020 — Modern top app bar + actions menu (redesign stage 1)  ✅ merged
- [x] 013 — Speed of play: pause / slow / med / fast (tick-loop based)  ✅ merged
- [x] 014 — Tax rate control (`setCityTax` binding + UI)  ✅ merged
- [x] 015 — Selected-tool highlight  ✅ merged
- [x] 020-023 — Full modern UI redesign: top bar + ⋮ menu, categorized icon palette (bottom sheet), contextual build controls  ✅ merged
- [x] Budget window (016+017)  ✅ merged
- [x] City evaluation window (016+017)  ✅ merged
- [x] Map overlays (034 heatmap tinting; City→Stats bars 042)
- [x] Graphs / history window (044)
- [x] Messages feed (045)

## 5. Better input  (see BACKLOG.md)
- [x] 018 — Drag-to-build (paint along finger) + navigate/build split  ✅ merged
- [x] 019 — Staged build: preview + confirm/cancel (UI-only)  ✅ merged
      Opus designs the C-ABI staged-effects extension.

## 6. Engine → host events  (needs the callback plumbing; Opus designs C-ABI/JNI)
- [x] Wire the key callbacks — event bridge 037 (QueueCallback + poll_event), consumption 038
- [x] Sounds (046, 049)
- [x] Disasters menu / triggers (Simulation → Disasters cards, 032/039)

---
Method reminder: qwen (coder-next) does the coding; Opus specs + reviews. Android
tasks run in the main tree (not `--isolate`). Dispatch via `run_in_background`,
never a trailing `&`. See `CLAUDE.md`.

- [x] 024 — Modern styling pass (rounded corners, cards, spacing, depth)  ✅ merged
- [x] 025 — Trial mode (build → Keep/Revert), replaces tile-paint preview  ✅ merged

## 7. Ambient background play + notifications  (DESIGN.md §12)
User decisions (2026-09-23): a separate **background pace** setting; every event
group (Disasters, New year, City problems, Milestones) is its own setting (notify /
pause); "run in background" is a setting too.
- [x] 054 — Deterministic replay: RNG capture, seeded load, `test/determinism.c` (Opus)
- [ ] 055 — Settings: "Background & notifications" section (run in background, pace,
      per-group notify / pause). Prefs only, no behaviour yet. (qwen)
- [ ] 056 — Notifier: channels per group, POST_NOTIFICATIONS request, post with a
      deep link (tile x/y); tap opens the app and centres the map. (qwen, Opus reviews)
- [ ] 057 — Background engine: sidecar (anchor, rng, pace), probe on background,
      exact alarm, alarm receiver replays + notifies, catch-up on foreground,
      boot re-arm, WorkManager safety net. (Opus designs the threading/contract;
      qwen parts)
- [ ] 058 — Deterministic app-side disaster roll (seeded from snapshot + month) so
      background and foreground agree. (qwen)
