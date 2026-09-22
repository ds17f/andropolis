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

# Fence scope on plan-only passes: no filesystem mutation.
TOOL_ARGS=()
INSTRUCTION="Implement the attached task spec. Build and run the test in its \
Definition of Done, and iterate until it is green. If you cannot make it green, \
stop and report the exact command and error output."
if [[ "$PLAN" -eq 1 ]]; then
  TOOL_ARGS=(--exclude-tools 'write,edit')
  INSTRUCTION="Read the attached task spec. Do NOT change any files. Describe \
precisely how you would implement it: files you would touch, the code you would \
write, and how you would satisfy the Definition of Done. Flag anything ambiguous."
fi

echo ">> dispatching $(basename "$SPEC")  model=$MODEL  plan=$PLAN  session=$SID" >&2
echo ">> live log: $LOG  (watch with: .claude/skills/qwen-dispatch/watch.sh $SID)" >&2

# pipefail (set above) makes pi's exit status propagate through the tee.
cd "$REPO"
pi -p --mode json \
  --provider "$PROVIDER" --model "$MODEL" \
  --append-system-prompt "$RULES" \
  --session-id "$SID" \
  --approve \
  "${TOOL_ARGS[@]}" \
  "@$SPEC" "${CONTEXT[@]/#/@}" \
  "$INSTRUCTION" \
  | tee "$LOG"
