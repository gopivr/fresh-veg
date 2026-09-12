#!/usr/bin/env bash
set -euo pipefail

if [[ "${CONFIRM_RESET_LOCAL_DB:-}" != "YES" ]]; then
  cat >&2 <<'MSG'
Refusing to reset the FresVeg local database.
This command is destructive: it removes the docker-compose Postgres and Redis volumes.
Run with CONFIRM_RESET_LOCAL_DB=YES when you intentionally want a clean local environment.
MSG
  exit 2
fi

docker compose down -v
