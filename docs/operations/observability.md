# FresVeg observability and operations

Phase 15 standardizes production diagnostics across the gateway and all backend services.

## Endpoints

Each deployable component exposes:

- `GET /actuator/health` for aggregate health without component details.
- `GET /actuator/health/liveness` for process liveness.
- `GET /actuator/health/readiness` for traffic readiness. Database-backed services include the database readiness contributor.
- `GET /actuator/prometheus` for Prometheus scraping.

Actuator discovery remains disabled and sensitive actuator endpoints such as `/actuator/env` remain denied by service security.

## Metrics

Micrometer publishes the standard Spring Boot meters, including HTTP server latency/error series and Hikari database pool metrics for database-backed services. All meters carry an `application` tag from `spring.application.name`.

Phase 15 adds FresVeg business meters:

| Prometheus name | Service | Type | Meaning |
| --- | --- | --- | --- |
| `fresveg_orders_created_total` | Commerce | Counter | Durable order records created after inventory reservations are persisted. |
| `fresveg_checkout_failures_total` | Commerce | Counter | Checkout preview failures, including order creation preview failures. |
| `fresveg_payment_failures_total` | Commerce | Counter | Failed payment authorizations, refund failures and payment gateway exceptions. |
| `fresveg_outbox_backlog` | Commerce | Gauge | Pending Commerce outbox events waiting for publication. |
| `fresveg_inventory_reservation_conflicts_total` | Supply | Counter | Inventory reservation conflicts caused by insufficient stock or conflicting reservation intent. |

Counters may be absent from a Prometheus scrape until they have a nonzero value, while the meters are still registered in the application registry.

## Tracing

All components include Micrometer tracing with the OpenTelemetry bridge and OTLP exporter. Configure tracing with environment variables:

- `OTEL_TRACES_SAMPLER_PROBABILITY`, default `1.0`.
- `OTEL_EXPORTER_OTLP_ENDPOINT`, default `http://localhost:4318/v1/traces`.

Existing `X-Correlation-ID` handling remains the application-level request correlation contract. The gateway forwards it to downstream services, servlet services put it into MDC, and responses echo the safe resolved value.

## Logs

Services retain structured ECS JSON console logs through `LOG_FORMAT`, defaulting to `ecs`. Logs include service name and correlation ID when a request is in scope. Request detail logging remains masked by Spring and service code must not log tokens, credentials, payment method data, address snapshots or full order snapshots.

## Readiness and rollout notes

Use liveness for restart decisions and readiness for traffic routing. Prometheus scraping can use `/actuator/prometheus` on each component. Keep `/actuator/env`, `/actuator/configprops`, heap dumps and mappings unavailable unless a future explicit operations phase introduces an authenticated management plane.
