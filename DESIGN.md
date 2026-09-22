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
  and tests — with the ability to build and run to verify itself.

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

**Next after first light:** move the sim to a background thread; real tile
rendering; touch input → `doTool`; then the engine→host callbacks task.

## 11. Sibling repo: `~/Developer/micropolis-android`

A **parallel, independent** port with a different architecture: a pure-**Kotlin**
port of the **micropolisj** (Java) engine (package `micropolisj.engine`), on branch
`kotlin-qwen`, remote `ds17f/micropolis-android`. It was already "building &
running" when found. It is **not** MicropolisCore and shares no engine code with
this repo. We reuse its build harness (Gradle/AGP/Kotlin versions, Makefile
shape) but keep the projects separate. These are two bets on the same goal; the
user may reconcile them later.
