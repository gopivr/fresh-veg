# FresVeg OpenAPI contracts

These files are the stable frontend/backend contracts for the FresVeg services:

- `account-api.yaml`
- `catalog-api.yaml`
- `supply-api.yaml`
- `commerce-api.yaml`
- `fulfillment-api.yaml`

The contracts use OpenAPI 3.1 and the public HTTP namespace is `/api/v1`. Internal service-to-service APIs are documented in the owning service contract when they are intentionally callable by another FresVeg service.

## Shared conventions

Authentication is bearer-token based. Public customer, vendor, admin and internal-service routes define the expected authorization in the endpoint description and are enforced by the owning Spring Security configuration. Internal routes require a service principal audience for the target service.

Errors use `application/problem+json`. Error responses include a problem `type`, `title`, HTTP `status`, a stable `code` when one exists, and the request correlation value. Validation errors include field-level details where the service can safely expose them.

Paginated list endpoints accept `pageSize` and cursor or service-specific filters. Responses include the result collection and pagination metadata or a next cursor when another page is available. New list APIs should be cursor-compatible even when the initial implementation supports only bounded page sizes.

Idempotent command endpoints accept `Idempotency-Key`. The key is scoped to the authenticated actor and command surface. Replays return the original durable result instead of creating a second aggregate.

Every endpoint accepts `X-Correlation-ID` and echoes it in responses. If the caller omits it, platform filters create one and attach it to logs, metrics and problem responses.
