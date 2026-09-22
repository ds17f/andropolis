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
