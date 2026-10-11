#!/usr/bin/env bash
# Stops the Finance App (embedded Tomcat) started by bin/start.sh.
# Usage: stop.sh [port]   (default: APP_PORT from app.env)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$(dirname "$SCRIPT_DIR")/app.env"

PORT="${1:-$APP_PORT}"

PID="$(ss -ltnp "sport = :$PORT" 2>/dev/null | grep -o 'pid=[0-9]*' | head -1 | cut -d= -f2 || true)"
if [[ -z "$PID" ]]; then
  echo "Nothing listening on port $PORT — app is not running."
  exit 0
fi

# Also stop the parent `mvn spring-boot:run` launcher so it doesn't linger or respawn the app
PARENT="$(ps -o ppid= -p "$PID" | tr -d ' ')"
TARGETS=("$PID")
if [[ -n "$PARENT" ]] && ps -o args= -p "$PARENT" | grep -q 'spring-boot:run'; then
  TARGETS+=("$PARENT")
fi

echo "Stopping Finance App on port $PORT (pid ${TARGETS[*]})..."
kill "${TARGETS[@]}" 2>/dev/null || true

for _ in $(seq 1 20); do
  if ! ss -ltn "sport = :$PORT" | grep -q LISTEN; then
    echo "Stopped."
    exit 0
  fi
  sleep 0.5
done

echo "Still running after 10s — sending SIGKILL."
kill -9 "${TARGETS[@]}" 2>/dev/null || true
sleep 1
if ss -ltn "sport = :$PORT" | grep -q LISTEN; then
  echo "[ERROR] Port $PORT is still in use."
  exit 1
fi
echo "Stopped."
