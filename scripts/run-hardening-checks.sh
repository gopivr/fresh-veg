#!/usr/bin/env bash
set -euo pipefail

mvn verify
mvn -DskipTests dependency:tree -DoutputFile=target/dependency-tree.txt
mvn -N -Pdatabase-bootstrap liquibase:updateSQL > target/bootstrap-liquibase.sql

docker compose config >/tmp/fresveg-compose-rendered.yaml

if command -v trivy >/dev/null 2>&1; then
  trivy config .
else
  echo "trivy not found; install Trivy to run container/config vulnerability scanning." >&2
fi
