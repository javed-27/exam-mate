#!/usr/bin/env bash
# Keep `adb reverse tcp:11434 tcp:11434` applied so the app can reach Ollama
# on the host even when the USB cable briefly disconnects/reconnects.
#
# Usage:
#   ./scripts/keep_reverse_tunnel.sh          # run in foreground
#   ./scripts/keep_reverse_tunnel.sh &        # run in background
#   ./scripts/keep_reverse_tunnel.sh --once   # apply once and exit
set -euo pipefail

ADB="${ADB:-adb}"
HOST_PORT="${HOST_PORT:-11434}"
POLL_SECS="${POLL_SECS:-3}"

if ! command -v "$ADB" >/dev/null 2>&1; then
  echo "adb not found. Add Android SDK platform-tools to PATH or set ADB=/path/to/adb" >&2
  exit 1
fi

apply_once() {
  "$ADB" reverse tcp:"$HOST_PORT" tcp:"$HOST_PORT" >/dev/null 2>&1 || true
  if "$ADB" reverse --list 2>/dev/null | grep -q "tcp:$HOST_PORT"; then
    echo "[$(date '+%H:%M:%S')] reverse tcp:$HOST_PORT applied"
  else
    echo "[$(date '+%H:%M:%S')] no device connected; reverse not applied" >&2
    return 1
  fi
}

if [[ "${1:-}" == "--once" ]]; then
  apply_once
  exit $?
fi

echo "Watching device; re-applying adb reverse tcp:$HOST_PORT every ${POLL_SECS}s (Ctrl-C to stop)"
while true; do
  apply_once
  sleep "$POLL_SECS"
done
