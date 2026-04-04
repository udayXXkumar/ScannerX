#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"
RUN_DIR="$ROOT_DIR/.run"

BACKEND_URL="http://127.0.0.1:8080/actuator/health"
FRONTEND_URL="http://localhost:5173"
POSTGRES_PORT="5433"

mkdir -p "$RUN_DIR"

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

ensure_postgres() {
  if port_in_use "$POSTGRES_PORT"; then
    echo "Postgres already running on 127.0.0.1:$POSTGRES_PORT"
    return
  fi

  if ! command -v docker >/dev/null 2>&1; then
    echo "Postgres is required but Docker is unavailable."
    echo "Start Postgres on 127.0.0.1:$POSTGRES_PORT, then run this script again."
    exit 1
  fi

  echo "Postgres not detected. Trying docker compose..."
  docker compose -f "$ROOT_DIR/docker-compose.dev.yml" up -d db >/dev/null

  if ! wait_for_port "$POSTGRES_PORT" 60; then
    echo "Postgres could not be started on 127.0.0.1:$POSTGRES_PORT"
    exit 1
  fi

  echo "Postgres is up on 127.0.0.1:$POSTGRES_PORT"
}

start_backend() {
  if http_up "$BACKEND_URL"; then
    echo "Backend already running at $BACKEND_URL"
    return
  fi

  if port_in_use 8080; then
    echo "Port 8080 is already in use by another process."
    show_port_owner 8080
    exit 1
  fi

  echo "Starting backend..."
  (
    cd "$BACKEND_DIR"
    nohup env \
      SPRING_PROFILES_ACTIVE=postgres \
      APP_QUEUE_MODE=local \
      DB_HOST=127.0.0.1 \
      DB_PORT="$POSTGRES_PORT" \
      DB_NAME=scannerx \
      DB_USERNAME=scannerx \
      DB_PASSWORD=scannerx \
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

  if port_in_use 5173; then
    echo "Port 5173 is already in use by another process."
    show_port_owner 5173
    exit 1
  fi

  echo "Starting frontend..."
  (
    cd "$FRONTEND_DIR"
    nohup npm run dev -- --host 0.0.0.0 --port 5173 --strictPort >"$RUN_DIR/frontend.log" 2>&1 &
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

ensure_postgres
start_backend
start_frontend

echo
echo "ScannerX is running:"
echo "  Frontend: http://localhost:5173"
echo "  Backend : http://127.0.0.1:8080"
echo "  Postgres: psql postgresql://scannerx:scannerx@127.0.0.1:$POSTGRES_PORT/scannerx"
echo
echo "Logs:"
echo "  tail -f $RUN_DIR/backend.log"
echo "  tail -f $RUN_DIR/frontend.log"
echo
echo "To stop everything:"
echo "  $ROOT_DIR/stop.sh"
