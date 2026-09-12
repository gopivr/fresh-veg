# Production hardening review

Phase 19 reviewed the FresVeg release candidate across persistence, runtime, security and operations concerns.

## Database and transactions

Owned schemas are isolated per service and Liquibase history tables are protected from runtime users. High-traffic lookup paths have explicit indexes for customer carts, inventory availability, reservations, order status, fulfillment slots and tracking. Checkout, reservation and fulfillment mutations use service-owned transactions and optimistic versions where aggregate state can be concurrently changed. Rollback scripts refuse to remove non-empty domain tables.

## Runtime behavior

Service-to-service calls use explicit base URLs, validation, typed problem responses and bounded payloads. Commerce keeps payment and order state durable before publishing downstream work through the outbox. The local compose stack adds health-based startup ordering and exposes actuator health and Prometheus endpoints for every service.

## Security and PII

Runtime database credentials are separate from migrator credentials. Customer address data is stored as order and fulfillment snapshots only where required for business auditability. APIs use bearer tokens, audience checks and role-based route authorization. Logs and problem responses should continue to avoid full address and payment identifiers.

## Compatibility and API governance

The OpenAPI contracts under `contracts/openapi` define `/api/v1` routes, problem responses, correlation IDs and idempotent command headers. Compatibility reviews should treat removal, required-field changes and enum narrowing as breaking changes.

## Release checks

Use `./scripts/run-hardening-checks.sh` for the standard release-candidate checks:

- Maven verify, including unit, integration and Liquibase migration tests.
- Maven dependency tree capture for dependency review.
- Liquibase bootstrap SQL rendering for migration review.
- Docker Compose config rendering.
- Trivy config/container scanning when Trivy is installed locally.

## Unresolved concerns

- External identity-provider, payment-provider and fulfillment-carrier integrations remain environment-specific and require production credentials and contract certification.
- Rate limiting is implemented at the gateway boundary configuration level and should be tuned with production traffic data.
- Backup, restore and point-in-time recovery must be validated against the managed Postgres provider used in production.
- Container image vulnerability scanning depends on the scanner installed in the CI/CD environment.
