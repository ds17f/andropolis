#!/usr/bin/env bash
# Hardened wrapper around dispatch.sh. The Ollama box intermittently wedges pi at
# STARTUP (0 CPU, 0 log output, forever). This detects that (no output within
# STARTUP_SECS), kills the dispatch AND its orphaned pi, frees the box's memory,
# and retries. Once pi starts producing output it is left to finish normally.
#
# Same arguments as dispatch.sh:  dispatch-retry.sh [--isolate] <spec> [ctx files...]
set -uo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
HOST="worklaptop.home.silberg.cloud"
STARTUP_SECS=90
MAX_ATTEMPTS=4

# First non-flag argument is the spec; derive the log path like dispatch.sh does.
SPEC=""
for a in "$@"; do case "$a" in --*) ;; *) SPEC="$a"; break;; esac; done
SID="port-$(basename "$SPEC" .md)"
LOG="$REPO/tasks/logs/$SID.jsonl"

free_mem() {
  for m in $(curl -sS -m 6 "http://$HOST:11434/api/ps" 2>/dev/null \
             | grep -oE '"model":"[^"]+"' | sed 's/.*:"//;s/"//'); do
    curl -sS -m 12 "http://$HOST:11434/api/generate" \
      -d "{\"model\":\"$m\",\"keep_alive\":0}" >/dev/null 2>&1
  done
}
kill_dispatch() {
  kill -9 "$1" 2>/dev/null
  pkill -9 -f 'qwen-dispatch/dispatch.sh' 2>/dev/null
  for p in $(pgrep -x pi 2>/dev/null); do
    c=$(readlink "/proc/$p/cwd" 2>/dev/null)
    case "$c" in *micropolis-port*) kill -9 "$p" 2>/dev/null;; esac
  done
}

for attempt in $(seq 1 "$MAX_ATTEMPTS"); do
  echo ">> dispatch-retry attempt $attempt/$MAX_ATTEMPTS: $SID" >&2
  mkdir -p "$REPO/tasks/logs"; : > "$LOG"
  "$REPO/.claude/skills/qwen-dispatch/dispatch.sh" "$@" >/dev/null 2>&1 &
  DPID=$!
  started=0
  for ((i=0; i<STARTUP_SECS; i+=3)); do
    sleep 3
    if [ "$(wc -l <"$LOG" 2>/dev/null || echo 0)" -gt 0 ]; then started=1; break; fi
    kill -0 "$DPID" 2>/dev/null || { started=1; break; }  # exited already
  done
  if [ "$started" -eq 1 ]; then
    wait "$DPID"; rc=$?
    echo ">> dispatch-retry: produced output; finished (exit $rc) on attempt $attempt" >&2
    exit "$rc"
  fi
  echo ">> dispatch-retry: STARTUP HANG (${STARTUP_SECS}s, 0 lines) — kill + free + retry" >&2
  kill_dispatch "$DPID"; free_mem; sleep 3
done
echo ">> dispatch-retry: all $MAX_ATTEMPTS attempts hung — the box needs attention" >&2
exit 1
