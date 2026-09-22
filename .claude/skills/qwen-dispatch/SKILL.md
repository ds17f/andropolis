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

   **To watch qwen live**, run this in a second terminal while the dispatch runs:
   ```bash
   .claude/skills/qwen-dispatch/watch.sh            # follows the newest dispatch
   .claude/skills/qwen-dispatch/watch.sh port-001-smoke-null-callback
   ```
   It renders a colored feed of qwen's text, tool calls, and results from
   `tasks/logs/<session>.jsonl`. Ctrl-C stops the view; the dispatch keeps running.
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

The default is `qwen3-coder-next:latest`. It has a 262K context and thinking. It
is best when you feed engine files and the spec together. To change the model for
one run, use `QWEN_MODEL=qwen3.8:27b-mlx`. Do an early **bake-off** on one real
spec with both models. Let the diffs decide. Read `DESIGN.md` section 6.
