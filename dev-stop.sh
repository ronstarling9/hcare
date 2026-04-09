#!/usr/bin/env bash
set -euo pipefail

PID_FILE="/tmp/hcare-dev.pids"

kill_pid() {
  local pid=$1
  local name=$2
  if kill -0 "$pid" 2>/dev/null; then
    kill "$pid" 2>/dev/null && echo "   Stopped $name (PID $pid)"
  fi
}

# ── Kill from PID file ────────────────────────────────────────────────────────
if [[ -f "$PID_FILE" ]]; then
  read -r BACKEND_PID BFF_PID FRONTEND_PID < "$PID_FILE"
  echo "▶  Stopping hcare dev servers..."

  kill_pid "$BACKEND_PID" "backend"
  kill_pid "$BFF_PID" "bff"
  kill_pid "$FRONTEND_PID" "frontend"

  rm -f "$PID_FILE"
else
  echo "⚠️  No PID file found — falling back to port/process scan..."
fi

# ── Fallback: kill anything still on these ports ──────────────────────────────
for port in 8080 8081 5173; do
  pid=$(lsof -ti :"$port" 2>/dev/null || true)
  if [[ -n "$pid" ]]; then
    kill "$pid" 2>/dev/null && echo "   Killed process on port $port (PID $pid)"
  fi
done

echo "✅ hcare dev environment stopped."
