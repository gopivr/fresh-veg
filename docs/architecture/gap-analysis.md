# Gap analysis

All target implementation components are absent. Existing requirement documents are retained; there is no implementation to refactor or duplicate.

| Gap | Impact / planned resolution | Phase |
| --- | --- | --- |
| No Maven/Java/Spring project | Pin compatible dependencies, seven modules, profiles, health and build conventions | 1 |
| No local infrastructure configuration | PostgreSQL/Redis Compose baseline; optional Kafka | 1 |
| No schema ownership/migration foundation | Service schemas, roles, packaged changelogs, repeat-run validation | 2 |
| No business models/APIs | Implement domains in master order with owner-local migrations and tests | 3–11 |
| No auth implementation | External OIDC resource server and ownership checks with first business endpoints; full gateway integration later | 3–13 |
| No durable event integration | Transactional order outbox in Phase 9; publisher/AsyncAPI in Phase 12 | 9, 12 |
| No cache or performance evidence | Add measured indexes/caching after correctness | 14 |
| No operational telemetry | Baseline health/logging earlier; metrics/tracing and runbooks | 1–15 |
| No service images or deployment assets | Full local stack and health-based startup | 16 |
| No consolidated contracts/E2E/hardening/handover | Evolve contracts with APIs; complete final validation and documentation | 17–20 |

## Sequencing decisions and open issues

- Phase 9 explicitly requires an outbox write with the order. Create its migration then; Phase 12 reuses it and adds publication, avoiding duplicate migration history.
- Security rules apply from each endpoint's introduction. Phase 13 is not permission to expose earlier business endpoints without authorization.
- Phase 8 may use a delivery client abstraction until Phase 11. Choose explicit local/test behavior and production configuration requirements in Phase 8.
- The fulfillment contract uses singular and plural public prefixes; Phase 13 routes both. Phase 12 uses `InventoryReleased` as the canonical inventory-release event name in AsyncAPI while existing reservation API names stay unchanged.
- Commerce needs remote reservation compensation after a local failure, including a crash or unknown remote outcome. Design durable reconciliation and idempotency during Phase 9; do not claim distributed atomicity.
- Root bootstrap versus per-service Liquibase tracking needs one consistent identity/locking strategy in Phase 2, verified by running both paths without duplicate changes.
- The available JDK is 25, while the proposed baseline is 21. Phase 1 must verify compilation/runtime and the selected dependency compatibility; install/use an appropriate JDK if needed. Versions remain unselected.

## Known limitations and prerequisites

There is no Git metadata, build, runtime test evidence, database, configured OIDC provider, deployment target or established API consumer in this workspace. Docker CLI availability does not establish a working container daemon. Phase 1 must check container and dependency access, select the compatible dependency set, and establish a reproducible build. Identity provider, production secrets, deployment topology, money/quantity precision, currency/business policy and idempotency retention remain decisions for their relevant phases.

These are planned work and operational unknowns, not implemented technical debt or blockers to completing Phase 0. See the [backlog](implementation-backlog.md).
