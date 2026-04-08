#!/usr/bin/env bash
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_FILE="/tmp/hcare-dev.pids"
BACKEND_LOG="/tmp/hcare-backend.log"
FRONTEND_LOG="/tmp/hcare-frontend.log"
BACKEND_URL="http://localhost:8080/api/v1/auth/login"
FRONTEND_URL="http://localhost:5173"

# ── Cleanup any stale processes ───────────────────────────────────────────────
if [[ -f "$PID_FILE" ]]; then
  echo "⚠️  Found existing PID file — running dev-stop.sh first..."
  "$REPO_DIR/dev-stop.sh" 2>/dev/null || true
fi

# ── Start backend ─────────────────────────────────────────────────────────────
echo "▶  Starting backend (dev profile)..."
SPRING_PROFILES_ACTIVE=dev mvn -f "$REPO_DIR/backend/pom.xml" spring-boot:run \
  > "$BACKEND_LOG" 2>&1 &
BACKEND_PID=$!
echo "   PID $BACKEND_PID → logs at $BACKEND_LOG"

# ── Start frontend ────────────────────────────────────────────────────────────
echo "▶  Starting frontend..."
npm --prefix "$REPO_DIR/frontend" run dev \
  > "$FRONTEND_LOG" 2>&1 &
FRONTEND_PID=$!
echo "   PID $FRONTEND_PID → logs at $FRONTEND_LOG"

# ── Save PIDs ─────────────────────────────────────────────────────────────────
echo "$BACKEND_PID $FRONTEND_PID" > "$PID_FILE"

# ── Wait for backend ──────────────────────────────────────────────────────────
echo -n "⏳ Waiting for backend"
for i in $(seq 1 60); do
  if curl -s -o /dev/null -w "%{http_code}" -X POST "$BACKEND_URL" \
       -H "Content-Type: application/json" \
       -d '{"email":"","password":""}' 2>/dev/null | grep -qE "^(401|200)$"; then
    echo " ready."
    break
  fi
  echo -n "."
  sleep 2
  if [[ $i -eq 60 ]]; then
    echo ""
    echo "✗  Backend did not start within 120s. Check $BACKEND_LOG"
    exit 1
  fi
done

# ── Wait for frontend ─────────────────────────────────────────────────────────
echo -n "⏳ Waiting for frontend"
for i in $(seq 1 20); do
  if curl -s -o /dev/null "$FRONTEND_URL" 2>/dev/null; then
    echo " ready."
    break
  fi
  echo -n "."
  sleep 1
  if [[ $i -eq 20 ]]; then
    echo ""
    echo "✗  Frontend did not start within 20s. Check $FRONTEND_LOG"
    exit 1
  fi
done

# ── Open Chrome ───────────────────────────────────────────────────────────────
echo "🌐 Opening Chrome at $FRONTEND_URL"
open -a "Google Chrome" "$FRONTEND_URL"

echo ""
echo "✅ hcare dev environment is running."
echo "   Backend:  http://localhost:8080"
echo "   Frontend: $FRONTEND_URL"
echo "   Login:    admin@sunrise.dev / Admin1234!"
echo "   Stop:     ./dev-stop.sh"
