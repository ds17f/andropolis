#!/usr/bin/env bash
# Watch a qwen dispatch live, as a readable colored feed.
#
# Usage:
#   watch.sh                 # follow the most recent dispatch log
#   watch.sh <session-id>    # e.g. port-001-smoke-null-callback
#   watch.sh <path.jsonl>    # any pi --mode json stream file
#
# Run this in a separate terminal while a dispatch runs. Ctrl-C to stop; the
# dispatch keeps running. It renders qwen's text, tool calls, and results.
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
LOGDIR="$REPO/tasks/logs"
arg="${1:-}"

if [[ -z "$arg" ]]; then
  LOG="$(ls -t "$LOGDIR"/*.jsonl 2>/dev/null | head -1 || true)"
elif [[ -f "$arg" ]]; then
  LOG="$arg"
else
  LOG="$LOGDIR/${arg%.jsonl}.jsonl"
fi
[[ -n "${LOG:-}" && -f "$LOG" ]] || { echo "no log found (looked in $LOGDIR)"; exit 1; }

echo "▶ watching $(basename "$LOG")   (Ctrl-C to stop; dispatch keeps running)"
echo

# Each line is one JSON event. Parse-or-skip so stray non-JSON lines are ignored.
tail -n +1 -f "$LOG" | jq -jR --unbuffered '
  def trim($n): if (.|type=="string") and (.|length) > $n then .[:$n] + "…" else . end;
  def arg: (.args.path // .args.command // .args.filePath // (.args|tostring)) | trim(90);
  (fromjson? // empty) |
  if .type=="message_update" then
    (.assistantMessageEvent // {}) as $e
    | if   $e.type=="text_delta"     then ($e.delta // "")
      elif $e.type=="text_start"     then "\n\n\u001b[36mqwen ▸\u001b[0m "
      elif $e.type=="toolcall_start" then "\n\u001b[33m  🔧 " + ($e.toolName // "tool") + "\u001b[0m"
      else empty end
  elif .type=="tool_execution_start" then
    "\n\u001b[33m  ⏳ " + (.toolName // "tool") + "  " + arg + "\u001b[0m"
  elif .type=="tool_execution_end" then
    "\n\u001b[32m  ✅ " + (.toolName // "tool") + " done\u001b[0m\n"
  elif .type=="turn_start"  then "\n\u001b[90m── turn ──\u001b[0m\n"
  elif .type=="agent_start" then "\u001b[90m▶ qwen session start\u001b[0m\n"
  elif .type=="agent_end"   then "\n\u001b[90m■ session end\u001b[0m\n"
  else empty end
'
