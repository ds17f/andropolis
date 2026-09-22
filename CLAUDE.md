# CLAUDE.md — micropolis-port

Full design & method: `DESIGN.md`.

## How we work here — READ FIRST

This project runs a **local open-weights model (qwen, via `pi`) as the executor**
for the coding volume. Opus (Claude) is the **planner / architect / reviewer ONLY**.

**Default to delegating to qwen. Do NOT spend Opus tokens on work qwen can do.**
That means not just feature code but the legwork around it: researching the
codebase, searching, sourcing and copying assets, and boilerplate. Put the whole
job — "find X", "copy Y into assets", "implement Z" — into a task spec and dispatch
it. When in doubt, delegate.

Opus does ONLY:
- Architecture and contract design (APIs, JNI/threading models).
- Toolchain / pipeline bring-up a local model would thrash on (first-time
  Gradle/NDK integration).
- Reviewing qwen's diffs and verifying on-device.

**To dispatch** (the `qwen-dispatch` skill): write `tasks/NNN-slug.md`, commit,
then `.claude/skills/qwen-dispatch/dispatch.sh [--isolate] tasks/NNN-slug.md [context files]`.
Always review the diff before merging. Watch live: `.claude/skills/qwen-dispatch/watch.sh`.

## Key facts
- Native Android port of **MicropolisCore** (C++ engine, NDK + JNI). Architecture,
  the C-ABI boundary, and the build in `DESIGN.md`.
- Build / run: `make run`, `make run-headful`, `make screenshot`.
- The sibling repo `~/Developer/micropolis-android` (a Kotlin/micropolisj port) is
  **ditched** in favor of this repo — keep it separate, do not invest in it.
