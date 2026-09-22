#!/usr/bin/env bash
# Dispatch a task spec to the local qwen executor via pi.
#
# Usage:
#   dispatch.sh [--plan] [--isolate] <task-spec.md> [context-files...]
#
# Flags:
#   --plan      Plan-only pass: qwen describes its approach, changes no files.
#   --isolate   Run qwen in a throwaway git worktree on branch dispatch/<task-id>
#               (physical isolation + ccache if present). Opus reviews and merges
#               after. Use for large/risky tasks and for parallel dispatches.
#
# Env:
#   QWEN_MODEL             executor model id (default: qwen3-coder-next:latest)
#   QWEN_PROVIDER          pi provider      (default: ollama)
#   DISPATCH_ALLOW_DIRTY=1 skip the clean-tree guard (not recommended)
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
MODEL="${QWEN_MODEL:-qwen3-coder-next:latest}"
PROVIDER="${QWEN_PROVIDER:-ollama}"

PLAN=0; ISOLATE=0
while [[ "${1:-}" == --* ]]; do
  case "$1" in
    --plan)    PLAN=1; shift;;
    --isolate) ISOLATE=1; shift;;
    --)        shift; break;;
    *) echo "unknown flag: $1" >&2; exit 2;;
  esac
done

SPEC="${1:-}"
if [[ -z "$SPEC" || ! -f "$SPEC" ]]; then
  echo "error: task spec not found: '${SPEC:-<none>}'" >&2
  echo "usage: dispatch.sh [--plan] [--isolate] <task-spec.md> [context-files...]" >&2
  exit 2
fi
shift || true
CONTEXT=("$@")   # extra files for qwen to read

TASK="$(basename "$SPEC" .md)"
SID="port-$TASK"

# Clean-tree guard: qwen has git write access, so an uncommitted planner change
# can be clobbered by a stray checkout. Commit first (this bit us once).
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

# Live log always lives in the MAIN repo, so watch.sh finds it either mode.
LOGDIR="$REPO/tasks/logs"; mkdir -p "$LOGDIR"; LOG="$LOGDIR/$SID.jsonl"

# Working dir: the repo, or an isolated worktree on its own branch.
WORKDIR="$REPO"; BRANCH=""
if [[ "$ISOLATE" -eq 1 ]]; then
  BRANCH="dispatch/$TASK"
  WORKDIR="$REPO/.worktrees/$SID"
  echo ">> isolate: worktree $WORKDIR on branch $BRANCH" >&2
  # `git worktree remove` refuses a tree with submodules, so tear down by hand.
  rm -rf "$WORKDIR"
  git -C "$REPO" worktree prune
  git -C "$REPO" branch -D "$BRANCH" 2>/dev/null || true
  git -C "$REPO" worktree add -q -b "$BRANCH" "$WORKDIR" HEAD
  # Bring the submodule into the worktree, borrowing the main clone's objects
  # (--reference-if-able) so we don't re-clone MicropolisCore over the network.
  git -C "$WORKDIR" submodule update --init --recursive \
      --reference-if-able "$REPO/.git/modules/MicropolisCore" 2>/dev/null || \
    git -C "$WORKDIR" submodule update --init --recursive
  # Share a compiler cache so per-worktree cold builds stay cheap (optional).
  if command -v ccache >/dev/null; then
    export CMAKE_CXX_COMPILER_LAUNCHER=ccache CMAKE_C_COMPILER_LAUNCHER=ccache
    echo ">> ccache enabled" >&2
  fi
  # Copy gitignored local config the build needs (SDK path) into the worktree.
  [[ -f "$REPO/android/local.properties" ]] && \
    cp "$REPO/android/local.properties" "$WORKDIR/android/local.properties" || true
fi

RULES="$WORKDIR/tasks/_rules.md"
BLOCKED_FILE="$WORKDIR/tasks/$TASK.BLOCKED.md"
rm -f "$BLOCKED_FILE"   # clear any stale block from a previous round

# Instruction + scope fence.
TOOL_ARGS=()
INSTRUCTION="Implement the attached task spec. Build and run the test in its \
Definition of Done, and iterate until it is green. Stage only your in-scope files \
by name and commit when green; never use git add -A, git commit -a, git checkout, \
git reset, git restore, git stash, or git clean. If you cannot make it green, or a \
decision is missing from the spec, do NOT guess: write tasks/$TASK.BLOCKED.md \
(what you tried, the exact error, the question you need answered) and stop."
if [[ "$PLAN" -eq 1 ]]; then
  TOOL_ARGS=(--exclude-tools 'write,edit')
  INSTRUCTION="Read the attached task spec. Do NOT change any files. Describe \
precisely how you would implement it: files you would touch, the code you would \
write, and how you would satisfy the Definition of Done. Flag anything ambiguous."
fi

echo ">> dispatching $TASK  model=$MODEL  plan=$PLAN  isolate=$ISOLATE  session=$SID" >&2
echo ">> live log: $LOG  (watch: .claude/skills/qwen-dispatch/watch.sh $SID)" >&2

cd "$WORKDIR"
set +e
pi -p --mode json \
  --provider "$PROVIDER" --model "$MODEL" \
  --append-system-prompt "$RULES" \
  --session-id "$SID" \
  --approve \
  "${TOOL_ARGS[@]}" \
  "@$SPEC" "${CONTEXT[@]/#/@}" \
  "$INSTRUCTION" \
  | tee "$LOG" "$LOGDIR/current.jsonl"
STATUS=${PIPESTATUS[0]}
set -e

# Post-run summary for the planner (Opus reviews before anything is trusted).
echo >&2
echo ">> dispatch exit: $STATUS" >&2
[[ -f "$BLOCKED_FILE" ]] && echo ">> ⚠ qwen is BLOCKED and needs help. Read: $BLOCKED_FILE" >&2
if [[ "$ISOLATE" -eq 1 ]]; then
  echo ">> commits on $BRANCH:" >&2
  git -C "$WORKDIR" --no-pager log --oneline "$HEAD_BEFORE"..HEAD >&2 || true
  echo ">> review:  git -C \"$REPO\" diff $HEAD_BEFORE..$BRANCH" >&2
  echo ">> merge:   git -C \"$REPO\" merge --ff-only $BRANCH" >&2
  echo ">> cleanup: rm -rf \"$WORKDIR\" && git -C \"$REPO\" worktree prune && git -C \"$REPO\" branch -d $BRANCH" >&2
else
  echo ">> new commits since dispatch:" >&2
  git -C "$REPO" --no-pager log --oneline "$HEAD_BEFORE"..HEAD >&2 || true
  echo ">> uncommitted changes (review before trusting):" >&2
  git -C "$REPO" --no-pager status --porcelain >&2 || true
fi
exit "$STATUS"
