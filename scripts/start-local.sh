#!/usr/bin/env bash
set -euo pipefail

compose_profiles=()
if [[ "${ENABLE_KAFKA:-false}" == "true" ]]; then
  compose_profiles+=(--profile kafka)
fi

docker compose ${compose_profiles[@]+"${compose_profiles[@]}"} up --build
