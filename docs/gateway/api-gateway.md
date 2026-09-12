# API Gateway

Phase 13 makes `api-gateway` the secure public entry point for implemented FresVeg public APIs. It uses Spring Cloud Gateway WebFlux and keeps domain authorization in the owning services.

## Public routes

The gateway routes only `/api/v1/**` public contracts to service base URLs configured by environment variables:

| Route prefix | Target variable | Default local URL |
| --- | --- | --- |
| `/api/v1/accounts/**` | `ACCOUNT_BASE_URL` | `http://127.0.0.1:8081` |
| `/api/v1/catalog/**` | `CATALOG_BASE_URL` | `http://127.0.0.1:8082` |
| `/api/v1/supply/**` | `SUPPLY_BASE_URL` | `http://127.0.0.1:8083` |
| `/api/v1/carts`, `/api/v1/carts/**`, `/api/v1/checkout/preview`, `/api/v1/orders`, `/api/v1/orders/**`, `/api/v1/vendor/orders`, `/api/v1/vendor/orders/**` | `COMMERCE_BASE_URL` | `http://127.0.0.1:8084` |
| `/api/v1/fulfillment/**`, `/api/v1/fulfillments/**` | `FULFILLMENT_BASE_URL` | `http://127.0.0.1:8085` |

`/internal/**` is blocked at the gateway and is never routed publicly. Internal service APIs still require service authentication when called directly on private service networks.

## Security

The gateway validates bearer JWTs with:

- `OIDC_ISSUER_URI`
- `OIDC_AUDIENCE` (`fresveg-gateway` by default)
- `OIDC_JWK_SET_URI`

Coarse gateway role checks use `CUSTOMER`, `VENDOR_ADMIN`, `VENDOR_STAFF` and `PLATFORM_ADMIN` from token role claims. They are a routing guard only. Catalog, Supply, Commerce, Account and Fulfillment still verify persisted ownership, customer identity, vendor membership, service subjects and scopes before performing business actions.

Public read routes for active catalog data, supply offers/listing availability and fulfillment slots can be called without a token. Protected API routes return 401 with `WWW-Authenticate: Bearer` when a token is missing or invalid. Forbidden or non-routed requests return RFC 9457 Problem Details with a `code` and `correlationId`.

## Cross-cutting behavior

- CORS is configured by `GATEWAY_ALLOWED_ORIGINS`; defaults allow local browser development origins.
- `Authorization` and `X-Correlation-ID` are propagated to downstream services.
- Missing or unsafe correlation IDs are replaced with a generated UUID.
- Security headers are set on gateway responses: `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy` and `Permissions-Policy`.
- A pluggable `GatewayRateLimiter` abstraction is installed with an in-memory per-minute implementation for the current node. `GATEWAY_RATE_LIMIT_PER_MINUTE` tunes the limit.
- Route-level logs record method, path, status, duration and correlation ID.

Gateway health remains database-independent and exposes only `/actuator/health`, `/actuator/health/liveness` and `/actuator/health/readiness`.
