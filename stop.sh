#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR="$ROOT_DIR/.run"

stop_pid_file() {
  local label="$1"
  local pid_file="$2"

  if [[ ! -f "$pid_file" ]]; then
    return
  fi

  local pid
  pid="$(cat "$pid_file")"

  if [[ -n "$pid" ]] && kill -0 "$pid" >/dev/null 2>&1; then
    echo "Stopping $label (pid $pid)..."
    kill "$pid" >/dev/null 2>&1 || true
  fi

  rm -f "$pid_file"
}

stop_pid_file "frontend" "$RUN_DIR/frontend.pid"
stop_pid_file "backend" "$RUN_DIR/backend.pid"

if command -v docker >/dev/null 2>&1; then
  docker compose -f "$ROOT_DIR/docker-compose.dev.yml" stop db >/dev/null 2>&1 || true
fi

echo "Requested stop for stored ScannerX processes."
