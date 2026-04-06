#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"
RUN_DIR="$ROOT_DIR/.run"

BACKEND_PORT="${SCANNERX_BACKEND_PORT:-8080}"
FRONTEND_PORT="${SCANNERX_FRONTEND_PORT:-5173}"
BACKEND_URL="http://127.0.0.1:${BACKEND_PORT}/actuator/health"
FRONTEND_URL="http://localhost:${FRONTEND_PORT}"
BACKEND_PUBLIC_URL="http://localhost:${BACKEND_PORT}"
LOCAL_ALLOWED_ORIGINS="http://localhost:3000,http://localhost:5173,http://localhost:8080,http://127.0.0.1:3000,http://127.0.0.1:5173,http://127.0.0.1:8080,http://localhost:${FRONTEND_PORT},http://127.0.0.1:${FRONTEND_PORT}"

mkdir -p "$RUN_DIR"

load_local_env_file() {
  local env_file="$1"

  if [[ -f "$env_file" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$env_file"
    set +a
  fi
}

port_in_use() {
  local port="$1"
  ss -ltn "( sport = :$port )" | awk 'NR > 1 { print }' | grep -q LISTEN
}

http_up() {
  local url="$1"
  curl -fsS --max-time 2 "$url" >/dev/null 2>&1
}

wait_for_port() {
  local port="$1"
  local retries="${2:-30}"

  for _ in $(seq 1 "$retries"); do
    if port_in_use "$port"; then
      return 0
    fi
    sleep 1
  done

  return 1
}

show_port_owner() {
  local port="$1"
  ss -ltnp "( sport = :$port )" || true
}

start_backend() {
  if http_up "$BACKEND_URL"; then
    echo "Backend already running at $BACKEND_URL"
    return
  fi

  if port_in_use "$BACKEND_PORT"; then
    echo "Port $BACKEND_PORT is already in use by another process."
    show_port_owner "$BACKEND_PORT"
    exit 1
  fi

  echo "Starting backend..."
  (
    cd "$BACKEND_DIR"
    mkdir -p data
    nohup env \
      SPRING_PROFILES_ACTIVE=local \
      PORT="$BACKEND_PORT" \
      APP_QUEUE_MODE=local \
      APP_CORS_ALLOWED_ORIGINS="${APP_CORS_ALLOWED_ORIGINS:-$LOCAL_ALLOWED_ORIGINS}" \
      ./mvnw spring-boot:run >"$RUN_DIR/backend.log" 2>&1 &
    echo $! >"$RUN_DIR/backend.pid"
  )

  for _ in {1..120}; do
    if http_up "$BACKEND_URL"; then
      echo "Backend is up at $BACKEND_URL"
      return
    fi
    sleep 1
  done

  echo "Backend did not become ready."
  tail -n 40 "$RUN_DIR/backend.log" || true
  exit 1
}

start_frontend() {
  if http_up "$FRONTEND_URL"; then
    echo "Frontend already running at $FRONTEND_URL"
    return
  fi

  if port_in_use "$FRONTEND_PORT"; then
    echo "Port $FRONTEND_PORT is already in use by another process."
    show_port_owner "$FRONTEND_PORT"
    exit 1
  fi

  echo "Starting frontend..."
  (
    cd "$FRONTEND_DIR"
    nohup env \
      VITE_API_BASE_URL="${VITE_API_BASE_URL:-$BACKEND_PUBLIC_URL/api}" \
      VITE_WS_BASE_URL="${VITE_WS_BASE_URL:-$BACKEND_PUBLIC_URL}" \
      npm run dev -- --host 0.0.0.0 --port "$FRONTEND_PORT" --strictPort >"$RUN_DIR/frontend.log" 2>&1 &
    echo $! >"$RUN_DIR/frontend.pid"
  )

  for _ in {1..60}; do
    if http_up "$FRONTEND_URL"; then
      echo "Frontend is up at $FRONTEND_URL"
      return
    fi
    sleep 1
  done

  echo "Frontend did not become ready."
  tail -n 40 "$RUN_DIR/frontend.log" || true
  exit 1
}

load_local_env_file "$ROOT_DIR/.env.local"
load_local_env_file "$BACKEND_DIR/.env.local"

start_backend
start_frontend

echo
echo "ScannerX is running:"
echo "  Frontend: http://localhost:$FRONTEND_PORT"
echo "  Backend : http://127.0.0.1:$BACKEND_PORT"
echo "  H2 DB   : $BACKEND_DIR/data/scannerx.mv.db"
echo "  H2 Console: http://127.0.0.1:$BACKEND_PORT/h2-console"
echo
echo "Logs:"
echo "  tail -f $RUN_DIR/backend.log"
echo "  tail -f $RUN_DIR/frontend.log"
echo
echo "To stop everything:"
echo "  $ROOT_DIR/stop.sh"
