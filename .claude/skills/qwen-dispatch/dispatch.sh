#!/usr/bin/env bash
# Dispatch a task spec to the local qwen executor via pi.
# Usage:
#   dispatch.sh [--plan] <task-spec.md> [context-files...]
# Env:
#   QWEN_MODEL   executor model id (default: qwen3-coder-next:latest)
#   QWEN_PROVIDER  pi provider     (default: ollama)
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
MODEL="${QWEN_MODEL:-qwen3-coder-next:latest}"
PROVIDER="${QWEN_PROVIDER:-ollama}"
RULES="$REPO/tasks/_rules.md"

PLAN=0
if [[ "${1:-}" == "--plan" ]]; then PLAN=1; shift; fi

SPEC="${1:-}"
if [[ -z "$SPEC" || ! -f "$SPEC" ]]; then
  echo "error: task spec not found: '${SPEC:-<none>}'" >&2
  echo "usage: dispatch.sh [--plan] <task-spec.md> [context-files...]" >&2
  exit 2
fi
shift || true
CONTEXT=("$@")   # extra files for qwen to read

# Stable session id per spec, so a follow-up round resumes context.
SID="port-$(basename "$SPEC" .md)"

# Live log, so you can watch with watch.sh (tasks/logs/<SID>.jsonl).
LOGDIR="$REPO/tasks/logs"
mkdir -p "$LOGDIR"
LOG="$LOGDIR/$SID.jsonl"

# Pre-flight: refuse to dispatch with a dirty tree. qwen has git write access; an
# uncommitted planner change can be clobbered by a stray checkout (it happened
# once). Commit or stash first, or set DISPATCH_ALLOW_DIRTY=1 to override.
if [[ "${DISPATCH_ALLOW_DIRTY:-0}" != "1" ]]; then
  DIRTY="$(git -C "$REPO" status --porcelain)"
  if [[ -n "$DIRTY" ]]; then
    echo "error: working tree is not clean. Commit your work before dispatch, or" >&2
    echo "       set DISPATCH_ALLOW_DIRTY=1 to override. Uncommitted changes:" >&2
    echo "$DIRTY" >&2
    exit 3
  fi
fi
HEAD_BEFORE="$(git -C "$REPO" rev-parse HEAD)"
BLOCKED_FILE="$REPO/tasks/$(basename "$SPEC" .md).BLOCKED.md"
rm -f "$BLOCKED_FILE"   # clear any stale block from a previous round

# Fence scope on plan-only passes: no filesystem mutation.
TOOL_ARGS=()
INSTRUCTION="Implement the attached task spec. Build and run the test in its \
Definition of Done, and iterate until it is green. Stage only your in-scope files \
by name and commit when green; never use git add -A, git commit -a, git checkout, \
git reset, git restore, git stash, or git clean. If you cannot make it green, or a \
decision is missing from the spec, do NOT guess: write tasks/$(basename "$SPEC" .md).BLOCKED.md \
(what you tried, the exact error, the question you need answered) and stop."
if [[ "$PLAN" -eq 1 ]]; then
  TOOL_ARGS=(--exclude-tools 'write,edit')
  INSTRUCTION="Read the attached task spec. Do NOT change any files. Describe \
precisely how you would implement it: files you would touch, the code you would \
write, and how you would satisfy the Definition of Done. Flag anything ambiguous."
fi

echo ">> dispatching $(basename "$SPEC")  model=$MODEL  plan=$PLAN  session=$SID" >&2
echo ">> live log: $LOG  (watch with: .claude/skills/qwen-dispatch/watch.sh $SID)" >&2

cd "$REPO"
set +e
pi -p --mode json \
  --provider "$PROVIDER" --model "$MODEL" \
  --append-system-prompt "$RULES" \
  --session-id "$SID" \
  --approve \
  "${TOOL_ARGS[@]}" \
  "@$SPEC" "${CONTEXT[@]/#/@}" \
  "$INSTRUCTION" \
  | tee "$LOG"
STATUS=${PIPESTATUS[0]}
set -e

# Post-run summary for the planner (Opus reviews before anything is trusted).
echo >&2
echo ">> dispatch exit: $STATUS" >&2
if [[ -f "$BLOCKED_FILE" ]]; then
  echo ">> ⚠ qwen is BLOCKED and needs help. Read: $BLOCKED_FILE" >&2
fi
echo ">> new commits since dispatch:" >&2
git -C "$REPO" --no-pager log --oneline "$HEAD_BEFORE"..HEAD >&2 || true
echo ">> uncommitted changes (review before trusting):" >&2
git -C "$REPO" --no-pager status --porcelain >&2 || true
exit "$STATUS"
