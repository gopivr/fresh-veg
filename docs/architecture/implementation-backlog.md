# Implementation backlog

Phases 0–13 are complete. Phases 14–20 below are pending and require explicit authorization of that phase. Execute sequentially and stop after each phase. This plan does not create later-phase scaffolding.

| Phase | Scope | Deliverables | Acceptance evidence |
| --- | --- | --- | --- |
| 1 (complete) | Foundation and multi-module platform | Parent POM, seven modules, Java/dependency pins, profiles, serialization, Actuator, PostgreSQL/Redis Compose, optional Kafka | All services compile; mvn clean verify passes; health/profile and Compose validation; no business schemas |
| 2 (complete) | Liquibase foundation | Five schemas/roles, per-service and root masters, packaging and privilege model | Clean PostgreSQL migration and unchanged second run; bootstrap/service identity consistency; schema isolation |
| 3 (complete) | Account | Identity mapping, profiles, addresses, vendors/memberships, roles/preferences; JWT ownership | Migrations and integration tests; cross-customer/vendor access denied |
| 4 (complete) | Catalog | Products/variants, hierarchical categories, units/images, metadata, filters and admin APIs | Pagination/filter/authorization tests and OpenAPI; justified indexes |
| 5 (complete) | Supply offers and pricing | Locations, listings, effective prices/tiers and vendor APIs | Ownership, price validity and offer lookup tests |
| 6 (complete) | Inventory and batches | Stock constraints, movement ledger, batches, reservation/commit/release/expiration | Simultaneous last-stock attempts cannot oversell; adjustment and expiration tests |
| 7 (complete) | Cart | Customer carts/items and resilient Supply client | Ownership, listing validation and remote-failure tests; server-resolved prices |
| 8 (complete) | Checkout | Authoritative totals, Account/Supply clients, delivery abstraction and pricing policies | Breakdown, ownership and unavailable-dependency tests; no order creation |
| 9 (complete) | Orders | Snapshots/history, idempotency, vendor operations, reservation compensation; create order outbox now | Duplicate keys do not duplicate orders; snapshots persist; order/outbox atomicity and compensation tests |
| 10 (complete) | Payments | PaymentGateway abstraction, local adapter, attempts/refunds and lifecycle | Authorization/failure/refund and inventory compensation tests; no raw card storage |
| 11 (complete) | Fulfillment | Slots/capacity, fulfillments/items, shipments/events/assignments and tracking | Capacity, transition and API authorization tests |
| 12 (complete) | Outbox and events | Reuses Phase 9 outbox with publisher abstraction, no-op/local/Kafka modes, event envelope and AsyncAPI | Retry/publication-failure tests; committed orders remain durable |
| 13 (complete) | Gateway and security | Routes including both fulfillment prefixes, CORS, JWT, headers, correlation and rate-limit abstraction | Internal routes inaccessible publicly; role and token propagation tests |
| 14 | Redis and performance | Selective TTL caching/invalidation and query-driven indexes | Cache correctness and EXPLAIN ANALYZE evidence; inventory remains authoritative in PostgreSQL |
| 15 | Observability and operations | Metrics, OpenTelemetry, structured logs, readiness/liveness and operational docs | Correlated requests and domain/failure/backlog metrics validated without sensitive logs |
| 16 | Containers and local environment | Service Dockerfiles, full Compose, health-based startup and lifecycle scripts | Build images/start stack; health checks; clearly destructive reset behavior |
| 17 | OpenAPI consolidation | Five service contracts and shared auth/error/pagination/idempotency/correlation guidance | Contract validation and implementation alignment |
| 18 | End-to-end testing | Happy path, final-stock race, payment failure, duplicate order, changed price and address scenarios | All six master scenarios pass with durable state assertions |
| 19 | Production hardening | Security/PII, performance/transactions/resilience, migration/backups/resources and scans | Dependency/container scans, coverage and migration verification; unresolved risks documented |
| 20 | Architecture and handover | README, architecture/database/API/operations docs, ADR review, diagrams and checklist | Documentation reflects implemented system and validation evidence |

## Gates for every implementation phase

Inspect the workspace, latest summary, ADRs, relevant configuration and migrations first. Preserve existing implementations and deployed migration history. Compile every service and run meaningful unit/integration tests; use PostgreSQL Testcontainers when practical. If the database changes, apply to a clean database and rerun without unexpected changes. Update OpenAPI with API changes, check for secrets, report all changed files, objects, endpoints, assumptions and limitations, update PREVIOUS_PHASE_SUMMARY.md, and stop.

## Phase 1 entry checklist (satisfied)

- Recheck that the workspace remains documentation-only and inspect any newly added instructions.
- Select and verify compatible Java, Spring Boot, Cloud Gateway, SpringDoc and test dependency versions; pin them centrally.
- Check Maven repository access and container daemon availability for the applicable checks.
- Create the root Maven platform with the seven specified modules and sensible package conventions.
- Keep Phase 1 free of business schemas; defer schema ownership/migrations to Phase 2.

See [gaps and sequencing decisions](gap-analysis.md). Phase 1 is implemented; see [the current summary](../PREVIOUS_PHASE_SUMMARY.md) for validation. Phase 13 is complete; Phase 14 has not started.
