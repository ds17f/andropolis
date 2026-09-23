---
name: qwen-dispatch
description: Dispatch a task spec to the local qwen executor with pi, then review the diff and iterate. Use when you delegate a coding unit of the Micropolis Android port to qwen instead of writing the code yourself — when the plan says "qwen owns" a task, or the user asks to give a task to qwen, to the local model, or to pi.
---

# qwen-dispatch

Give one task spec to the local **qwen** executor. Use `pi` and the Ollama box at
`worklaptop.home.silberg.cloud`. Then **review the diff before it goes into the
code**. Opus stays the planner and the reviewer. qwen writes the bulk of the code.
For the full method, read `DESIGN.md`.

## When to use this skill

- Use it for a task that the plan marks **"qwen owns"**. Examples: Embind-to-JNI
  translations, Compose screens, glue code, and tests. These are small tasks with
  a clear spec.
- Do not use it for NDK or CMake bring-up, or for the design of the C-ABI
  boundary. Opus does those tasks directly.

## Steps

1. **Write the spec first.** Copy `tasks/_TEMPLATE.md` to `tasks/NNN-slug.md`.
   Fill every section. The *Interface* section needs the exact signatures. The
   *Definition of Done* section needs the exact build command and test command. A
   vague spec makes vague code. The planning value is in the spec.

2. **Optional plan-only pass.** This is a cheap check that qwen understood the
   task:
   ```bash
   .claude/skills/qwen-dispatch/dispatch.sh --plan tasks/NNN-slug.md <context-files...>
   ```
   The `--plan` flag turns off the write and edit tools. qwen only describes its
   approach.

3. **Dispatch the task.**
   ```bash
   .claude/skills/qwen-dispatch/dispatch.sh tasks/NNN-slug.md <context-files...>
   ```
   Add any upstream or engine files that qwen must read as more arguments. The
   script adds `tasks/_rules.md`, runs `pi -p --mode json`, and makes a session id
   from the spec name. A later round then resumes the same context.

   **Keep the context files small.** The script pastes each context file into the
   prompt in full. A large file fills the context window before qwen starts. When
   the window is full, Ollama drops the oldest text first: that is the rules and
   the spec. qwen then works without the spec. It invents code, edits files that
   are out of scope, or rewrites the spec (tasks 050–053 did all three).
   - Do not attach a file that is larger than 24 KB. `dispatch.sh` refuses it
     (set `DISPATCH_ALLOW_BIG=1` to override).
   - In the spec, give the file, the function names, and the line ranges. qwen
     then reads only those parts.
   - Keep source files small (about 300 lines or less). `MainActivity` is split
     into extension files for this reason.

   **Isolated runs:** add `--isolate` for large/risky tasks, or whenever you run
   several dispatches in parallel:
   ```bash
   .claude/skills/qwen-dispatch/dispatch.sh --isolate tasks/NNN-slug.md <context-files...>
   ```
   qwen then works in a throwaway worktree (`.worktrees/`) on branch
   `dispatch/NNN-slug`, so it cannot touch the main tree at all. It uses `ccache`
   if installed, so per-worktree builds stay cheap. On exit the script prints the
   `diff`, `merge --ff-only`, and cleanup commands. Because the worktree contains
   the submodule, tear it down with `rm -rf <worktree> && git worktree prune`
   (plain `git worktree remove` refuses a tree with submodules). Default (no flag)
   runs in the main tree — fine for small single tasks, which the clean-tree guard
   already protects.

   **To watch qwen live**, run this in a second terminal while the dispatch runs:
   ```bash
   .claude/skills/qwen-dispatch/watch.sh            # continuous: follows every new dispatch
   .claude/skills/qwen-dispatch/watch.sh port-001-smoke-null-callback  # one specific run
   ```
   With no argument it follows `tasks/logs/current.jsonl` with `tail -F`, so a
   single watcher left running keeps flowing as new tasks start. A session id
   follows just that run's `tasks/logs/<session>.jsonl`. Ctrl-C stops the view;
   the dispatch keeps running.
   For a transcript after the run, `pi --session <id> --export <file>.html`.

4. **Always review the diff.** Do not let qwen's work go into the code without a
   review:
   ```bash
   git -C /home/damian/Developer/micropolis-port diff
   ```
   Make sure qwen obeyed the scope. Only the *Files in scope* must change. Make
   sure the code matches the interface. Run the Definition-of-Done test yourself.

5. **Iterate or accept.**
   - If the work needs more, refine the spec or add a correction note. Run the
     same command again. The session id resumes the context.
   - If the work is good, stage it and commit it. In the message, record that qwen
     did the work and the planner wrote the spec.
   - If the work is wrong, revert it with `git checkout -- <files>`. Make the spec
     stronger. Dispatch it again.
   - If qwen wrote `tasks/<task-id>.BLOCKED.md`, it is stuck and asking for help
     (`dispatch.sh` flags this on exit). Read it, answer the question by tightening
     the spec or adding a note, delete the BLOCKED file, and re-dispatch the same
     session so qwen resumes with context.

**Before every dispatch the tree must be clean.** `dispatch.sh` refuses a dirty
tree (exit 3) so an uncommitted planner edit cannot be clobbered by qwen's git.
Commit your own work first. qwen is constrained to stage only its in-scope files.

## Model selection

The default is `qwen3-coder-next-64k:latest`: `qwen3-coder-next` with its context
set to 64K (see `dispatch.sh`). A full context window is the most frequent cause
of bad runs, so keep the prompt small (see step 3). To change the model for one
run, use `QWEN_MODEL=qwen3.8-27b-64k:latest` (also 64K).

**Bake-off, 2026-09-23 (task 055, same spec, isolated branches, run one after the other):**

| | coder-next-64k | qwen3.8-27b-64k |
|---|---|---|
| Result | correct, green, 1 attempt | correct, green, 1 attempt |
| Time | 2 min 56 s | 7 min 7 s |
| Turns / max input tokens | 20 / 21.8K | 16 / 14.2K |
| Spec fidelity | code blocks copied exactly; re-indented a whole function it had to touch | changed whitespace in a copied block; smallest possible edit |
| Speed (generation) | ~75 tok/s | ~51 tok/s |

Both needed no review fixes. coder-next is 2.4× faster, so it stays the default.
qwen3.8 is a good second choice when a task needs careful minimal edits. Repeat the
bake-off on a harder task when there is one. Read `DESIGN.md` section 6.
