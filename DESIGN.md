# Micropolis Android Port — Design & Working Method

Status: **active** · Last updated: 2026-09-22
Working dir: `~/Developer/micropolis-port`

This is the source-of-truth document for the port. It captures the architecture
decision, the planner/executor working method, and the exact tooling so the
effort survives across sessions and machines.

---

## 1. Goal

Build a **native Android port** of Micropolis (open-source SimCity Classic),
based on the modern **MicropolisCore** project. Native = NDK-compiled C++ engine
+ Jetpack Compose UI, not a WebView wrapper.

Two-model working method:
- **Opus (Claude Code)** = planner / architect / reviewer / integrator.
- **qwen (local, via `pi`)** = executor doing the bulk coding under tight specs.

## 2. Upstream: MicropolisCore

Repo: https://github.com/SimHacker/MicropolisCore (Don Hopkins / SimHacker).

Facts that shape the port:
- The **simulation engine is ~27 self-contained C++ files** — no X11, no Tcl/Tk.
  This is the crown jewel and is portable as-is. **We do not rewrite it in Kotlin.**
- The engine is exposed to JS today through an **Emscripten + Embind** binding,
  compiled to **WebAssembly**.
- The frontend is a separate **SvelteKit + WebGL** tile renderer, plus CLI tools.
- Build stack: Node 20+, pnpm, Emscripten SDK (emcc/em++/emar).

Key leverage: the Android port reuses the C++ engine unchanged. We compile it
with the **NDK** and replace the **Embind (C++↔JS) binding with a JNI / C-ABI
(C++↔Kotlin) binding** — a well-defined, largely mechanical translation.

## 3. Architecture: three clean layers

| Layer | What | Owner |
|---|---|---|
| **Engine** | The 27 C++ files, recompiled with the Android NDK. Mostly build config, little code change. | **Opus** (toolchain) |
| **Binding** | Replace the Embind layer with a JNI / thin C-ABI boundary. Opus designs the contract (memory ownership, threading, who frees what); qwen fills in per-function translations. | **Opus** design, **qwen** fill |

### Binding notes (discovered 2026-09-22)

Investigation of the engine sources established the exact shape of the JS coupling
— this drives the whole binding design:

- The `emscripten::val` dependency is **isolated to the callback plumbing**, not
  the simulation. Concentrated in `callback.h` (~70 refs), `js_callback.h` (~39),
  `callback.cpp` (~35). Only 2 refs each in `micropolis.h`/`.cpp`. The 24 core sim
  files (`simulate`, `zone`, `scan`, `traffic`, `evaluate`, …) include no
  Emscripten headers at all.
- **`micropolis.h` already ships a native stub:** under `#if defined(__EMSCRIPTEN__)`
  it includes the real Emscripten headers; the `#else` branch defines a minimal
  `namespace emscripten { class val { … }; }`. So building **without**
  `-D__EMSCRIPTEN__` makes the core compile against that stub with a plain C++17
  toolchain — no shim from us.
- **Native build set = 25 of 27 sources.** Exclude `emscripten.cpp` (Embind) and
  `callback.cpp` (JS callback impl, includes `<emscripten.h>`). Supply our own
  concrete `Callback` subclass instead of the JS one. Proven by task 001: a
  `NullCallback` + headless `main` ticks the sim (`cityTime` advances).
- **The core binding decision:** `emscripten::val callbackVal` is an opaque
  pass-through "user data" handle. For the native port it becomes a native handle
  (a `jobject`, a `void*`, or dropped). Replacing it + providing a native
  `Callback` subclass is the substance of the binding layer.
| **UI / render** | New Jetpack Compose app + a GL/Canvas tile renderer, input handling, city save/load. Genuinely new code, boundable per-screen. | **qwen** builds, **Opus** reviews |

### Sequencing (dependency order)

0. **Engine builds headless under the NDK** — trivial C `main` that ticks the
   sim N steps. Proves the toolchain before any UI or binding work.
   *(Opus does this — toolchain debugging burns a local model's time.)*
1. Design the C-ABI boundary contract; qwen fills in the Embind→C translations.
2. Minimal Compose app: load a city, render one frame.
3. Input + interaction.
4. Polish.

## 4. Working method: planner / executor

The contract between planner and executor is a **spec file per task**, living in
the repo under `tasks/`. Opus authors them; qwen executes them via `pi`.

A task spec (`tasks/NNN-slug.md`, template at `tasks/_TEMPLATE.md`) contains:
- **Goal** — one sentence.
- **Exact interface** — the C-ABI/JNI signatures, Kotlin declarations, or file
  skeletons qwen must match. This is where Opus does the thinking a local model
  should not: memory ownership, threading, who frees what.
- **Files in scope** — a whitelist, so qwen does not wander.
- **Definition of done** — the build command + the test that must pass.
  Non-negotiable. qwen's agentic loop iterates against this, which is what keeps
  a local model's output from rotting.
- **Constraints** — e.g. "don't touch engine `.cpp`", "no new deps".

Standing rules for the executor live in `tasks/_rules.md` (injected into every
dispatch) and `AGENTS.md` (auto-loaded by `pi`).

### Division of labor

- **Opus owns:** architecture, the C-ABI/JNI boundary contract, NDK/CMake build
  setup, and review of **every** diff before it lands.
- **qwen owns:** bulk translation of well-specified units, Compose screens, glue,
  and tests — with the ability to build and run to verify itself. **Also the
  legwork:** researching the codebase, searching, and sourcing/copying assets. Put
  it all in the task spec. Do NOT burn Opus tokens on work qwen can do — the point
  of this setup is the open-weights model, not premium tokens (see `CLAUDE.md`).

## 5. Tooling: `pi` + Ollama

`pi` is the local scriptable coding agent (`~/.local/share/mise/installs/node/lts/bin/pi`).
It reaches the Ollama box (`worklaptop.home.silberg.cloud`) — models already
visible to it. Relevant flags:

- `-p` / `--print` — non-interactive, run to completion and exit.
- `--mode json` — structured output for programmatic review.
- `--provider ollama --model <id>` — pick the executor model.
- `--append-system-prompt <file>` — inject `tasks/_rules.md`.
- `--session-id <id>` — resume the same task across rounds.
- `--tools` / `--exclude-tools` — fence scope (e.g. plan-only = no write/edit).
- `@files...` — pass context files.
- Auto-loads `AGENTS.md` / `CLAUDE.md` unless `--no-context-files`.

**Consequence:** the full loop runs from inside a Claude Code session. Opus
shells out to `pi` via Bash, hands it a spec, gets JSON back, reviews the diff,
and iterates — the user only approves. The dispatch wrapper is
`.claude/skills/qwen-dispatch/dispatch.sh`; the workflow is the `qwen-dispatch` skill.

### qwen → planner communication

qwen runs to completion on each `-p` call, so the exchange is **asynchronous** —
Opus is not live during a run. Three channels carry information back:

1. **The result stream.** Every dispatch returns qwen's full text + tool trace as
   JSON (rendered live by `watch.sh`). A failed Definition of Done surfaces here
   with qwen's own diagnosis.
2. **The BLOCKED protocol.** When qwen cannot pass the DoD, or hits a decision the
   spec does not cover, the contract requires it to STOP — not guess, not touch
   out-of-scope files — and write `tasks/<task-id>.BLOCKED.md` (what it tried, the
   exact error, the question it needs answered). `dispatch.sh` detects that file on
   exit and flags it to the planner.
3. **Session continuity = a conversation.** Because dispatch uses a stable
   `--session-id`, Opus answers by re-dispatching the same session with the answer
   or a tightened spec; qwen resumes with full context. Ask → answer → continue.

**Guardrails learned the hard way (2026-09-22):** qwen has git write access, so a
stuck qwen must fail *safe*, not improvise. In task 001 it ran `git checkout
DESIGN.md` and destroyed an uncommitted planner edit. Fixes: (a) `dispatch.sh`
refuses a dirty tree (exit 3), so planner work is always committed before a run;
(b) the contract forbids qwen from `git checkout/reset/restore/stash/clean` and
`git add -A`/`commit -a` — it stages only its in-scope files by name.

## 6. Model decision

Models visible to `pi` on the Ollama box:

| Model | Context | Thinking | Notes |
|---|---|---|---|
| `qwen3-coder-next:latest` | 262K | yes | Coder-tuned, biggest context |
| `qwen3.8:27b-mlx` | 131K | no | General, not coder-tuned |
| `batiai/qwen3.6-35b:q6` | 131K | no | Larger but q6-quantized |
| `qwen3:0.6b` | 131K | no | Too small |

**Default executor: `qwen3-coder-next:latest`.** Reasoning:
- Porting means feeding the model real engine files **plus** the spec at once.
  262K context vs 131K is decisive; the smaller window gets tight fast.
- It is coder-tuned and supports **thinking**.
- The user observes qwen3.8 performs well for them and suspects it is because
  they write rich one-shot prompts where "additional reasoning has value." That
  insight cuts *toward* coder-next: our task specs are exactly those rich prompts,
  and coder-next can actually reason (thinking=yes) on top of them, where
  qwen3.8 cannot (thinking=no). Rich spec + reasoning model should meet or beat.

**But verify, don't assume.** The model is one env var (`QWEN_MODEL` in
`dispatch.sh`). Run an early **bake-off**: dispatch the same real spec to
`qwen3-coder-next` and `qwen3.8:27b` and compare the diffs. Let evidence, not
this note, settle it. Keep `qwen3.8:27b` as the ready fallback.

## 7. Repo layout

```
micropolis-port/
  DESIGN.md                # this file — source of truth
  AGENTS.md                # house rules; pi auto-loads
  MicropolisCore/          # upstream (submodule) — untouched
  android/                 # the new native app (NDK + Compose)
  tasks/
    _rules.md              # standing contract for qwen (injected every dispatch)
    _TEMPLATE.md           # task-spec template
    NNN-slug.md            # one spec per unit (Opus authors, qwen executes)
  .claude/skills/qwen-dispatch/   # the dispatch skill + script
```

## 8. Commit workflow: microcommits

**Commit often.** Every moment the work is good is a moment to commit — in
particular, when qwen returns a diff that does what we want and its
Definition-of-Done test is green.

**Microcommits** are small, in-flight commits. They are not meant to be clean
history; they are **branch points to roll back to when things go sideways.** The
cost of one is near zero and the safety it buys is large, so err toward more of
them.

- **qwen writes its own commits.** When a dispatched task passes its Definition
  of Done, the executor commits the change with a short message describing what it
  did. `pi` has git via its bash tool.
- **Opus still reviews before each commit lands** — microcommits are frequent, not
  unreviewed.
- After a unit of work succeeds, **squash the microcommits down into conventional
  commits** (`feat:`, `fix:`, etc.) that tell the real story. In-flight noise
  disappears; the good history remains.

Practical rule of thumb: green test → commit; risky next step → commit first so
there's a point to return to; unit done → squash to a conventional commit.

## 9. Build & status

**Step 0 is COMPLETE and verified (2026-09-22).** The 25-source engine compiles
and runs headless on the host, and cross-compiles to a real Android AArch64
binary. Local toolchain: g++ 16, CMake 4.3, NDK 30.0.16248370, SDK at `~/Android/Sdk`.

Host (smoke test):
```
cmake -S android -B android/build-host -DCMAKE_BUILD_TYPE=Debug
cmake --build android/build-host --target micropolis_smoke -j
./android/build-host/smoke/micropolis_smoke   # -> cityTime=32 totalFunds=20000 cityPop=0
```

Android (arm64-v8a):
```
NDK=~/Android/Sdk/ndk/30.0.16248370
cmake -S android -B android/build-android \
  -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-24 -DCMAKE_BUILD_TYPE=Release
cmake --build android/build-android --target micropolis_smoke -j
# -> ELF 64-bit AArch64 pie executable, for Android 24 (NDK r30)
```
Known upstream warnings: two `-Wnonnull` in `fileio.cpp` (benign).

**Task 002 COMPLETE and verified (2026-09-22).** The C-ABI boundary
(`android/engine/include/micropolis_c.h` + `android/engine/src/micropolis_c.cpp`)
is implemented and merged to `main`. A plain-C test, `micropolis_abi_smoke`,
drives the engine through the ABI and passes on host; the boundary also
cross-compiles into the arm64 engine lib. qwen wrote the implementation from the
spec (in an isolated worktree); C↔C++ enum drift is a compile error via
`static_assert`.

**Binding gotcha (remember this):** `~Micropolis()` calls `setCallback(NULL, …)`,
and `setCallback` `delete`s the previous callback. So the `Callback` passed to the
engine MUST be heap-allocated — the engine owns and frees it. Never stack-allocate
it (that was a bug in the task-001 spec; qwen correctly overrode it).

## 10. Android app (first light)

Scaffolded 2026-09-22 under `android/` as a Gradle project. Harness reused from
the sibling repo `~/Developer/micropolis-android` (see §11): **Gradle 8.10.2, AGP
8.7.3, Kotlin 2.0.21, compileSdk 35, minSdk 24, Java 17**.

- **Native:** Gradle `externalNativeBuild` (CMake 3.22.1) builds `libmicropolis.so`
  from `android/app/src/main/cpp/CMakeLists.txt`, which `add_subdirectory`s
  `android/engine` (the engine + C-ABI lib) and links a JNI bridge. ABIs:
  `x86_64` (emulator) + `arm64-v8a` (devices). `ANDROID_STL=c++_shared`.
- **JNI bridge:** `android/app/src/main/cpp/micropolis_jni.cpp` — one thin
  function per native method, over `micropolis_c.h`. Kotlin surface:
  `MicropolisNative` (object, `external fun`s; handle = `MicropolisEngine*` as Long).
- **First-light UI is deliberately NOT Compose yet.** A plain custom `View`
  (`MapView`) draws the 120×100 tiles as colored cells; `MainActivity` creates the
  engine, generates a city, and ticks + redraws on a UI-thread timer. This is the
  lowest-risk path to pixels on a device. Compose + a real tile atlas + a sim
  thread come next. The UI is Opus-authored bring-up (getting the whole
  APK+JNI pipeline to light up); feature UI work goes to qwen from here.
- **Build & run:** the root `Makefile` — `make run` builds the APK, boots the
  `Pixel_API_36` emulator headless, installs, and launches; `make screenshot`
  grabs a PNG.

**Progress:**
- Task 003 (done, verified on device): the sim runs on a dedicated `HandlerThread`
  (engine is single-threaded, so it owns the handle and every native call); touch
  is marshaled onto it and taps call `doTool` (park). Verified: live updates with
  no ANR, `doTool` runs on-device without crashing.

- Task 004 (done, verified on device): real tile rendering from the atlas
  (`android/app/src/main/assets/tiles.png`, 256×960, 16×16, row-major,
  `idx = value & 0x3FF`). Authentic terrain now shows (dirt/forest/water). Fully
  qwen — it sourced/copied the atlas and wrote the renderer.

- Task 005 (done, verified): zoom + pan viewport (pinch/drag, square fit-to-width
  tiles, clamped pan, tap under transform). qwen impl; Opus fixed one API-compat
  detail (`GestureDetector.onScroll` first param is `MotionEvent?` on API 34+).
- Task 006 (done, verified): tool picker — a scrollable button bar; taps use the
  selected tool. Fully qwen.
- Task 007 (done, verified): stats HUD (funds/date/pop/score) polled from
  `getStats` each tick. Fully qwen. (True engine→host callbacks for discrete
  events — messages/sounds — deferred until needed; polling covers the HUD.)
- Task 008 (done, verified on device by user): respect the top/bottom system
  bars — `root.fitsSystemWindows = true` on the root `LinearLayout` in
  `MainActivity`. The tool-picker row now clears the navigation bar and the HUD
  clears the status bar on API 35+ edge-to-edge layout.

**Known polish item:** at startup the map sits at the top with a dark gap below;
it only centers vertically after a gesture (`clampPan` isn't called on first
layout). Fix later (call it in `onSizeChanged`).

**Next candidates:** initial map centering; real engine→host callbacks for
event notifications/sounds; save/load a city; playtesting the sim. All qwen
dispatches; Opus specs + reviews.

## 11. Sibling repo: `~/Developer/micropolis-android`

A **parallel, independent** port with a different architecture: a pure-**Kotlin**
port of the **micropolisj** (Java) engine (package `micropolisj.engine`), on branch
`kotlin-qwen`, remote `ds17f/micropolis-android`. It was already "building &
running" when found. It is **not** MicropolisCore and shares no engine code with
this repo. We reuse its build harness (Gradle/AGP/Kotlin versions, Makefile
shape) but keep the projects separate. **Decision (2026-09-22): the user is ditching
`micropolis-android` in favor of this repo.** This C++/NDK port is the active
path; the Kotlin repo stays separate and is not invested in further.

---

## 12. Ambient background simulation & notifications (epic)

Design doc for the "idle/ambient" mobile feature. Status: **design** (2026-09-22).
This section is the plan; nothing here is built yet.

### 12.1 Vision

Turn Micropolis into an ambient game. You set a **pace** — e.g. *up to 10 game-years
per real hour* — then leave the app. The city keeps advancing in the background. When
something worth your attention happens (a fire, a monster, a flood, a meltdown, a bad
traffic jam) or a chosen period elapses (each year, or the whole span), the phone
**notifies** you and the sim **pauses** until you come back. Tapping the notification
opens the app **at the exact moment the event happened**, zoomed to where it happened.
Then you fix things and let it roll again.

The feel we want: notifications that pop when the city actually needs you — "not so
random" — not a fixed 15-minute drip.

### 12.2 The core idea: game-time is a deterministic function of real-time

Define a **rate** `R` (game-ticks per real-second; the "10 years/hour" is just a
friendly way to set `R`). At any real time `t`, the city's correct game-time is
`gameTime(t) = gameTime(t0) + R * (t - t0)`. If the simulation is **deterministic and
replayable** from a snapshot, then we never need to run continuously in the background:
we can reconstruct the exact state at any `t` on demand by advancing a snapshot by the
right number of ticks. This single property is what makes the whole feature cheap.

### 12.3 Why not "just run it continuously" (the battery question)

Two shapes were on the table:

- **(A) Continuous foreground service** — a real Android foreground service ticks the
  sim in real time in the background, with a permanent "Micropolis is running"
  notification. Events fire live. Simple mental model. **Cost:** the process stays
  resident and must wake many times to tick across an hour; it holds off Doze; the
  persistent notification is always there. Even though each tick is cheap, the
  *frequency of wakeups* over a long session is what drains battery. Rough order: a
  few percent of battery per hour of backgrounded play, plus the ever-present
  notification. Android may also throttle it.

- **(B) Predict-and-schedule (recommended)** — this is exactly the model the user
  proposed: on backgrounding, **fast-forward a snapshot** to find the next
  notify-worthy event or period boundary, compute the **real time** it maps to, set a
  single **exact alarm** for that moment, then throw the fast-forward away. Between now
  and then we run **zero** background CPU. When the alarm fires we advance the (kept)
  snapshot to that game-time, fire the notification, save, and pause. **Cost:** one
  alarm per event — battery impact comparable to a calendar reminder, i.e. negligible.

**Recommendation: B.** It gives the "realtime feel" (the notification lands at the real
moment the event occurs), the "exact moment on foreground" (we reconstruct state at
`now`), *and* near-zero battery — precisely because determinism lets us avoid running
in real time. We do **not** need a continuously-running service. See 12.11 for the one
safety-net exception.

### 12.4 Architecture: predict-and-schedule

State we persist for the background contract (a small sidecar next to `autosave.cty`):
- `anchorRealMs` — real timestamp of the last known-good state.
- `anchorGameTicks` — the city's tick count at that anchor.
- `rate` — ticks per real-second.
- `paused` — whether the sim is currently paused awaiting the player.

On **background** (`onPause`/`onStop`), if not paused and background mode is on:
1. Snapshot the live engine (`saveCity` + **RNG state**, see 12.5).
2. Fast-forward a *copy* of that snapshot, tick by tick, watching for the first of:
   (a) a notify-worthy event (from the event queue, 12.6), (b) the next enabled period
   boundary (year-end, or span-end at the max 10-year horizon).
3. Let `Δticks` be ticks until that stop. Schedule an **exact alarm** for
   `anchorRealMs + Δticks / rate` carrying the event descriptor (type + location).
4. Discard the fast-forward; the anchor snapshot (at "now") is what we keep.

On **alarm fire** (background):
1. Advance the kept snapshot by the scheduled `Δticks` (deterministic replay →
   reproduces the same event at the same tick, 12.5). Save it as the new autosave.
2. Post the notification (type, and a deep-link intent carrying the tile location).
3. Mark `paused = true`. Do **not** schedule the next alarm — we wait for the player.
   (Period nudges that are configured *not* to pause instead re-run steps 1–4 of 12.4
   to arm the next one.)

On **foreground**:
1. Compute `wantTicks = anchorGameTicks + rate * (now - anchorRealMs)` (clamped to any
   pending event tick so we never overshoot a paused event).
2. Advance the engine deterministically to `wantTicks`; cancel the pending alarm.
3. If arriving because of a notification tap, `centerOnTile` the event location.
4. Reset the anchor to `now`. The foreground loop then ticks live at `rate`.

### 12.5 Determinism is the linchpin (RNG capture)

Predict-and-schedule only works if replaying a snapshot reproduces the same events at
the same ticks. Micropolis is deterministic **given its PRNG state**, but the `.cty`
save format does **not** store the engine's random seed, so a plain load→run diverges.
**Required engine work:** expose the PRNG state across the C-ABI
(`micropolis_get_rng` / `micropolis_set_rng`, capturing `Micropolis::nextRandom` and any
related seed) and snapshot it in the sidecar alongside `.cty`. With RNG capture,
`load(state)+setRng(seed)+advance(n)` is bit-identical every time.

**Feasibility — confirmed (2026-09-23 investigation):**
- All engine randomness goes through one LCG: `UQuad Micropolis::nextRandom`, advanced
  only in `simRandom()` (`nextRandom * 1103515245 + 12345`); `getRandom*` all call it.
  No `rand()`/`srand()`/`time()` anywhere in the engine. `nextRandom` is **public**, so the
  C-ABI can read/write the full 64-bit state (`seedRandom(int)` truncates — don't use it).
- Other state is *not* in `.cty`: `simCycle`, `phaseCycle`, `speedCycle`, `simPass`,
  `doInitialEval`, and live sprites (monster, tornado, trains, planes); loading also runs
  `initWillStuff()`. So `load(.cty)` ≠ the live engine.
- **Why that's fine:** predict-and-schedule only needs the *probe* (fast-forward to find
  the next event) and the *replay* (advance to "now" or to the event) to agree with
  **each other**, not with the live engine. Define the background timeline as a fixed
  recipe — *fresh engine → `init` → `load(S0)` → set `nextRandom = rng0` → advance N
  ticks* — and both runs are bit-identical. Cost: a one-time hiccup at the moment of
  backgrounding (in-flight sprites vanish, cycle counters reset), invisible in practice.
  Optionally capture/restore the cycle counters too to shrink even that.
- Required C-ABI: `int64_t micropolis_get_rng(e)` / `void micropolis_set_rng(e, int64_t)`.

**Fallback if RNG capture proves infeasible:** drop to a coarser promise — the
background advances the city and notifies, but the *exact* event tick is best-effort and
foregrounding lands "close enough" rather than frame-exact. This degrades B toward a
periodic **WorkManager** catch-up (advance-on-wake). We treat frame-exactness as the
goal and this as the graceful degradation.

### 12.6 Event system (prerequisite for everything here)

Today the engine runs with `NullCallback`, so the app has no events and no locations.
This epic (and in-game messages + zoom-to-event generally) needs a real bridge:

- Replace `NullCallback` with a callback that **enqueues** engine events into a
  C-side ring buffer instead of calling into the JVM (avoids cross-thread JNI upcalls;
  the sim runs on our own thread and we drain after each tick).
- New C-ABI: `micropolis_poll_event(out*) -> bool` returning `{type, x, y, extra}`.
  Map MicropolisCore's messages/sounds (fire reported, tornado/monster sighted, flood,
  meltdown, traffic, brownout, year-end, etc.) to a stable `MicropolisEventType` enum.
- Kotlin drains the queue each tick → in-game message banner, sound hooks, and the
  location used for zoom-to-event and notification deep-links.

This is independently useful (in-game messages, zoom-to-event) even without background
mode, so it ships **first**.

### 12.7 Components

- **EventBridge** (C-ABI + Kotlin drain) — 12.6.
- **TimeMapper** — holds `rate`, converts real↔game time, owns the anchor.
- **Snapshotter** — `.cty` + RNG sidecar; deterministic advance-to-tick helper.
- **BackgroundScheduler** — `AlarmManager` exact alarms (primary) + a periodic
  `WorkManager` safety net (12.11); arms/cancels on lifecycle.
- **Notifier** — notification channels per category, deep-link intents (tile location),
  tap → open + zoom.
- **Settings** (off the ⋮ menu) — background on/off, rate, per-event {notify, pause}.

### 12.8 Foreground behavior

When foregrounded, the existing `tickLoop` keeps running but ticks at the configured
`rate` (the current speed control becomes "off / normal / pace"). Events drained from
the queue show an in-game message and, if enabled, still pause. No alarms while
foregrounded.

### 12.9 Notifications, permissions, manifest

- `POST_NOTIFICATIONS` (Android 13+) — request at first enable of the feature.
- `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` — exact alarms on Android 12+. Prefer
  `USE_EXACT_ALARM` (granted by manifest for alarm-clock-style apps) if policy allows,
  else request `SCHEDULE_EXACT_ALARM`.
- `RECEIVE_BOOT_COMPLETED` — re-arm the pending alarm after a reboot.
- One notification channel per category (disaster / period / info) so the user can tune
  importance in system settings.

### 12.10 Battery analysis (direct answer)

- **B (predict-and-schedule): negligible.** Background CPU between events is zero; the
  work is one fast-forward burst at backgrounding (well under a second to simulate 10
  game-years) and one advance+notify at each alarm. Comparable to a reminder app.
- **A (continuous service): small but real** — a few percent per hour from frequent
  wakeups + Doze suppression, plus a permanent notification. Only worth it if
  determinism (12.5) can't be achieved, or if we later want a literally-live background.
- **Verdict:** B is both the nicer UX and the lighter battery footprint. We build B.

### 12.11 Edge cases

- **Missed/delayed exact alarm (Doze, battery saver):** a low-frequency periodic
  `WorkManager` (e.g. hourly) acts purely as a **safety net** — it checks whether the
  pending alarm's time has passed and, if so, catches up and re-arms. This is *not* the
  continuous service; it does nothing in the common case.
- **Process death:** all state lives in the autosave + sidecar; on next wake/foreground
  we recompute from the anchor. The `WorkManager` net and boot receiver re-arm.
- **Player backgrounds/foregrounds rapidly:** each background re-snapshots and re-arms;
  each foreground reconstructs to `now`. Idempotent.
- **Player edits while foregrounded:** resets the anchor; the next background re-derives
  everything from the new state.
- **Rate change:** re-anchor at `now` and re-arm.

### 12.12 Settings (off the ⋮ menu)

- Background simulation: on/off.
- Pace: a small set (e.g. 1 / 5 / 10 game-years per hour) or a slider.
- Per event type: notify on/off, and pause on/off (default: disasters notify+pause;
  year-end notify only; span-end notify+pause).

### 12.13 Phased plan (each phase independently shippable)

1. **Event system** (12.6) — engine callback → queue → `poll_event`; in-game message
   banner; `MapView.centerOnTile` zoom-to-event. *No background yet.* (Opus: C-ABI +
   RNG capture; qwen: Kotlin drain + banner + zoom.)
2. **Foreground pacing** — `rate`-based ticking; the pace control in the Simulation
   panel; event-driven pause.
3. **Notifications** — channels, permission, post-on-event, tap→open+zoom (works while
   foregrounded/just-backgrounded first).
4. **Predict-and-schedule background** (12.4–12.5) — snapshotter, TimeMapper,
   BackgroundScheduler (exact alarm + WorkManager net), boot receiver.
5. **Settings** (12.12) and polish.

### 12.14 Open decisions

- ~~**RNG capture feasibility** (12.5)~~ — **resolved: feasible** (single public 64-bit
  LCG; probe and replay share one load-and-seed recipe). See 12.5.
- **Do period nudges pause?** Proposed: year-end = notify only, span-end = pause.
  Confirm with playtesting.
- **Pace granularity** — fixed presets vs. free slider.
- **Foreground-while-backgrounded overlap** — confirm we always cancel the alarm on
  foreground so an event can't double-fire.
