# API migration plan

No existing controllers, contracts or consumers were found. The Phase 0 inspection established a greenfield contract rollout; there are no known legacy endpoints to migrate. Reassess this assumption if external consumers or deployments appear.

| Phase | Owner | Planned public contract |
| --- | --- | --- |
| 1 | All deployables | Actuator health; no business endpoints |
| 3 (complete) | Account | GET `/api/v1/accounts/me`, GET/POST `/api/v1/accounts/me/addresses`, PUT `/api/v1/accounts/me/addresses/{addressId}`, GET `/api/v1/accounts/me/vendor-memberships`; management endpoints deferred |
| 4 (complete) | Catalog | Product list/detail; category list/products; product POST/PUT/PATCH and category POST under `/api/v1/catalog` |
| 5 (complete) | Supply offers | Product offers, public/private listing detail, vendor listing and location management under `/api/v1/supply` |
| 6 (complete) | Supply inventory | Vendor inventory, batch receipt/read, adjustments and ledger read under `/api/v1/supply`; service-only reservation lifecycle under `/internal/v1/inventory` |
| 7 (complete) | Commerce | POST carts, GET cart, POST/PATCH/DELETE cart items under `/api/v1/carts` |
| 8 (complete) | Commerce / Supply | POST `/api/v1/checkout/preview`; additive GET Supply listing availability for a quantity/delivery horizon |
| 9 (complete) | Commerce | POST/GET orders, GET detail, POST cancellation under `/api/v1/orders`; vendor list/detail/accept/reject under `/api/v1/vendor/orders` |
| 10 (complete) | Commerce | Payment abstraction and lifecycle; no additional public payment contract added |
| 11 (complete) | Fulfillment | GET `/api/v1/fulfillment/slots`, GET `/api/v1/fulfillments/{id}`, GET `/api/v1/fulfillments/{id}/tracking`; internal POST `/internal/v1/fulfillments`, `/internal/v1/fulfillments/{id}/transitions`, `/internal/v1/fulfillment/slots` |
| 13 (complete) | Gateway | Route and security integration for all implemented public contracts |
| 17 | Contracts | Consolidate service OpenAPI files and validate implementation alignment |

Supply Phase 6 adds POST `/internal/v1/inventory/reservations` and POST `/{id}/commit` / `/{id}/release` beneath that path. Fulfillment Phase 11 adds POST `/internal/v1/fulfillments` plus internal transition and slot administration endpoints. Define required authenticated service lookup contracts as their callers are implemented. Never route `/internal/**` through the public gateway.

Preserve both singular `/api/v1/fulfillment/**` (slots) and plural `/api/v1/fulfillments/**` (fulfillment resources) in gateway planning: the master's approximate route list omits the plural form. Avoid silently renaming the specified resource APIs.

Success responses contain data and meta (requestId, timestamp), with pagination for collections; cursor pagination is preferred for large transactional collections. Errors use RFC 9457 with code and correlationId. Propagate Authorization and X-Correlation-ID; services also handle direct requests. DTOs use camelCase and never expose JPA entities. Publish OpenAPI 3.1 as endpoints arrive, before Phase 17 consolidation.

JWT identity determines customer ownership. Vendor operations verify Account membership and order/listing scope; administrative writes require authority. Apply these checks in each business phase, with gateway/security integration implemented in Phase 13. Prices, tax, delivery charges, discounts, availability and totals remain server-authoritative.

Order creation and payment initiation require persistent idempotency semantics: scope keys to caller/operation, compare request intent, reject conflicting reuse and return stable retries. Define retention and failure behavior in the owning phase. Breaking published contracts require v2; additive evolution stays v1. Add contract/authorization tests as APIs appear. Phase 3 implements the five Account operations with a [generated OpenAPI contract](../../contracts/openapi/account-api.yaml); Phase 4 implements four Catalog reads and four mutations with its [generated contract](../../contracts/openapi/catalog-api.yaml); Phases 5–6 implement nineteen Supply operations with the [generated contract](../../contracts/openapi/supply-api.yaml). Phase 7 adds five cart operations with the [Commerce contract](../../contracts/openapi/commerce-api.yaml). Phase 8 adds checkout preview and read-only Supply availability; Phase 9 adds customer/vendor order APIs, active Account vendor identity lookup and service-only Supply order reservation/recovery reads; later rows remain planned.
