# FresVeg Platform

FresVeg is a modular Spring Boot platform for fresh grocery commerce. It contains an API gateway, five domain services and shared platform primitives. Services own their schemas and expose versioned HTTP APIs documented under `contracts/openapi`.

## Services

| Module | Responsibility | Port |
| --- | --- | --- |
| `api-gateway` | Public routing, JWT validation, CORS, correlation, security headers and coarse rate limiting | 8080 |
| `account-service` | Users, customer profiles, addresses, vendors, memberships and preferences | 8081 |
| `catalog-service` | Categories, products, variants, units, images and metadata | 8082 |
| `supply-service` | Vendor locations, listings, prices, inventory, reservations and ledger | 8083 |
| `commerce-service` | Carts, checkout preview, durable orders, idempotency, payments and outbox | 8084 |
| `fulfillment-service` | Delivery slots, fulfillment creation, shipments, tracking and assignments | 8085 |
| `platform-common` | Shared HTTP, correlation, error and test/database support | library |
| `authorization-server` | Local Keycloak login, registration and OAuth2/OIDC token issuance | 8180 |

## Build and test

Use JDK 21 or 25, Maven 3.9.x and Docker for Testcontainers.

```bash
mvn clean verify
```

The full build runs unit tests, application integration tests and Liquibase migration validation against isolated PostgreSQL containers. OpenAPI contract conventions are checked during the platform-common test suite.

## Run locally

Start the full local stack with Docker Compose:

```bash
./scripts/start-local.sh
```

Stop it with:

```bash
./scripts/stop-local.sh
```

Resetting local data is destructive and requires an explicit confirmation variable:

```bash
CONFIRM_RESET_LOCAL_DB=YES ./scripts/reset-local-db.sh
```

Set `ENABLE_KAFKA=true` when you want the optional Kafka broker profile. The default compose stack runs the outbox publisher in no-op mode.

Follow the [local authentication instructions](docs/operations/local-authentication.md)
to create a user and obtain an API token with a username and password through
Postman or curl. Local token requests do not require a browser.

## Contracts and docs

- API contracts: `contracts/openapi/*.yaml`
- Async events: `contracts/asyncapi/fresveg-events.yaml`
- Architecture: `docs/architecture/solution-architecture.md`
- Database: `docs/database/schema-ownership.md` and `docs/database/entity-reference.md`
- API guidelines: `docs/api/api-guidelines.md`
- Operations: `docs/operations/local-development.md`, `docs/operations/deployment.md`, `docs/operations/troubleshooting.md`
- Phase history: `docs/PREVIOUS_PHASE_SUMMARY.md`
