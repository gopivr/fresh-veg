#!/usr/bin/env bash
set -euo pipefail

mvn -pl commerce-service,supply-service,fulfillment-service -am verify
