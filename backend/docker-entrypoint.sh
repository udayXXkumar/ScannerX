#!/usr/bin/env bash

set -euo pipefail

if [[ -n "${DATABASE_URL:-}" && -z "${SPRING_DATASOURCE_URL:-}" ]]; then
  eval "$(
    python3 <<'PY'
import os
import shlex
from urllib.parse import unquote, urlparse

database_url = os.environ.get("DATABASE_URL", "").strip()
if not database_url:
    raise SystemExit(0)

parsed = urlparse(database_url)
if parsed.scheme not in ("postgres", "postgresql"):
    raise SystemExit(0)

host = parsed.hostname or "localhost"
port = parsed.port or 5432
database = (parsed.path or "/scannerx").lstrip("/") or "scannerx"
jdbc_url = f"jdbc:postgresql://{host}:{port}/{database}"

print(f"export SPRING_DATASOURCE_URL={shlex.quote(jdbc_url)}")

if parsed.username and not os.environ.get("SPRING_DATASOURCE_USERNAME"):
    print(
        f"export SPRING_DATASOURCE_USERNAME={shlex.quote(unquote(parsed.username))}"
    )

if parsed.password and not os.environ.get("SPRING_DATASOURCE_PASSWORD"):
    print(
        f"export SPRING_DATASOURCE_PASSWORD={shlex.quote(unquote(parsed.password))}"
    )
PY
  )"
fi

exec java -jar /app/app.jar --server.port="${PORT:-8080}"
