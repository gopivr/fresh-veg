# Local development

Use Docker Compose for local infrastructure and services.

```bash
./scripts/start-local.sh
./scripts/stop-local.sh
CONFIRM_RESET_LOCAL_DB=YES ./scripts/reset-local-db.sh
```

Set `ENABLE_KAFKA=true` to include the optional Kafka broker. Ordinary local runs use the no-op outbox publisher. Compose waits for PostgreSQL health, runs the root Liquibase bootstrap, then starts services with health checks and dependency ordering.

Run targeted E2E coverage with:

```bash
./scripts/run-e2e.sh
```
