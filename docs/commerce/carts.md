# Customer carts — Phase 7

Commerce owns carts and cart_items. A cart stores the verified Account customer UUID and an immutable ISO currency; an item stores a Supply listing UUID and exact positive NUMERIC(18,6) quantity. No prices, totals, stock balances or reservation IDs are persisted. The previous Account, Catalog and Supply contracts remain unchanged.

## Ownership and lifecycle

Commerce validates its OIDC JWT locally (trusted issuer/JWKs, RS256, audience, subject and expiry), then forwards the token and correlation ID to Account `/api/v1/accounts/me`. Account's active customerId establishes ownership; its userId supplies audit UUIDs. Customer tokens therefore need Commerce and Account audiences. Supplied customerId, vendorId, price or total fields are rejected as unknown input. A valid token's customer/role claims cannot override the stored Account mapping.

Each POST creates a separate cart in the requested currency. Multiple carts per customer are allowed; there is no implicit active-cart singleton, anonymous cart, merge or expiry policy. Every read/edit/delete verifies the cart's customer first. Foreign and absent carts/items use the same 404 detail. There is no cross-owner database access or FK.

Add and PATCH require the current cart version; DELETE requires a version query parameter. Every item mutation updates the aggregate version. Concurrent edits with the same version produce one success and one 409, and failed child persistence rolls back the version increment. A cart allows at most 50 distinct listings and one item per listing. Duplicate additions return 409; PATCH replaces the absolute quantity rather than incrementing it. Removing an already absent item returns 404. New stock/checkout/order idempotency is not implied by these cart APIs.

## Current pricing

The application depends on a SupplyClient interface implemented by an HTTP adapter. It calls the existing public Supply listing-detail API. Supply remains authoritative for visibility, current schedules, currency amounts and ordered tier thresholds. The adapter selects the cart currency's current schedule and the highest quantity tier from that response, applying the existing whole-quantity unit-price contract. It does not call Supply's product-wide offers collection or alter Supply's pricing APIs.

Owner JSON decimals are parsed as BigDecimal, including thirteen integer/six fractional price digits. The client validates required identities, active status, positive amounts, effective windows, base MOQ and monotonically ordered discount tiers. Malformed data fails with 503. No previous price is cached or used as a fallback.

A read item has pricingStatus and nullable currentPrice (priceId, optional tierId, exact unitPrice and fetchedAt):

- PRICED: a matching current schedule and quantity tier were resolved.
- UNAVAILABLE: Supply returned 404 for the public listing.
- MINIMUM_NOT_MET: current MOQ exceeds the stored cart quantity.
- NO_CURRENT_PRICE: no current price in the cart currency at lookup time.

Unavailable items remain in the cart so customers can remove them. Add/PATCH require PRICED and otherwise return 409. DELETE only needs Account and Commerce; Supply outage does not prevent removal. A dependency outage fails the read with 503 rather than mislabeling an outage as a removed listing. Prices are live per item and may differ across page requests; they are not checkout commitments. The [Phase 8 checkout preview](checkout.md) revalidates these inputs. No subtotal, discount, tax, delivery charge or grand total is calculated here.

## HTTP contract

See the [generated OpenAPI 3.1 contract](../../contracts/openapi/commerce-api.yaml).

| Method | Endpoint | Input / response |
| --- | --- | --- |
| POST | `/api/v1/carts` | currency; 201 CartResponse and Location |
| GET | `/api/v1/carts/{cartId}` | pageSize/cursor; 200 cart metadata, current item prices and pagination |
| POST | `/api/v1/carts/{cartId}/items` | version, listingId, quantity; 200 cartId/version/itemId |
| PATCH | `/api/v1/carts/{cartId}/items/{itemId}` | version, quantity; 200 cartId/version/itemId |
| DELETE | `/api/v1/carts/{cartId}/items/{itemId}?version={version}` | 200 cartId/version/removed itemId |

Responses use data/meta envelopes and RFC 9457 errors with code/correlationId/instance. Item pagination is nested within the cart resource, ordered by item UUID. pageSize defaults 10 and is bounded 1–10 to limit remote work. nextCursor binds the authenticated customer, cart ID and cart version, so any edit requires restarting traversal. Database reads within a page use repeatable-read; remote quotes are live and not a distributed snapshot.

Typical errors: 400 invalid input/cursor, 401 missing or invalid JWT, 403 Account identity denied, 404 hidden/absent cart/item, 409 stale/duplicate/unpriceable item or cart-size limit, 503 owner failure/timeout/resilience rejection. Existing health, correlation, structured logging and docs-profile behavior are preserved. No public gateway routes or internal reservation calls are added.

## Configuration and resilience

Required: existing Commerce DB_URL/default profile, DB_PASSWORD, MIGRATION_DB_PASSWORD, OIDC_ISSUER_URI, OIDC_AUDIENCE, OIDC_JWK_SET_URI, ACCOUNT_BASE_URL and SUPPLY_BASE_URL. Account tokens are forwarded only to the configured Account URL; Supply listing reads are anonymous. Trusted base URLs reject userinfo/query/fragment and must be HTTP(S). Default API docs are off, local/test on, with API_DOCS_ENABLED override.

| Environment variable | Default |
| --- | --- |
| OWNER_CONNECT_TIMEOUT_MS | 2000 |
| OWNER_REQUEST_TIMEOUT_MS | 3000 |
| OWNER_COOLDOWN_MS | 5000 |
| OWNER_MAX_CONCURRENT | 8 per owner; valid range 1–100 |

Account and Supply have separate concurrency gates and failure counters. Three consecutive transport/non-200/malformed-envelope failures open that owner's gate for the cooldown period. Requests during cooldown or above concurrency capacity fail fast with 503. Successful calls and expected not-found/denied responses reset the failure count. There are no automatic retries or stale data fallbacks. Detailed malformed domain payloads also fail closed, but do not count toward the transport gate. Each cart page checks a 20-second quote-loop deadline before calls; an in-flight request may finish up to its configured timeout after that deadline. These bounds are basic local resilience, not a distributed breaker or retry engine.

Supply public listing data includes tiers, so one remote lookup resolves each item. There is no batch quote API yet; large carts require paginated reads. Connection/thread/heap tuning and production load testing remain future hardening work. Health verifies local database readiness, not owner/IdP availability.

## Migration, testing and boundaries

`commerce-003-create-carts` adds two tables with UUID PKs, TIMESTAMPTZ audits, BIGINT versions, a local item-to-cart FK, unique cart/listing relation and two lookup indexes. Customer/listing references are external UUIDs with no FK. Runtime updates are restricted to cart audit/version and item quantity/audit/version; cart deletion and identity mutation are denied. Item deletion is allowed for the requested lifecycle. Hibernate validates schema only. No production seed data is added.

Empty rollback locks both tables and drops them without CASCADE; any cart/item data refuses rollback. Integration tests validate clean/repeat migration, upgrade from the infrastructure baseline, empty rollback/reapply, populated refusal and runtime permissions.

Run `mvn clean verify` from the root. Commerce HTTP tests launch unchanged Account, Catalog and Supply JARs with separate disposable PostgreSQL databases and ephemeral signed identity. A test-only proxy injects Supply failure without changing its production code. Focused Commerce integration tests require those owner JARs already built. Tests cover ownership, unknown fields, current/replaced/tier prices, exact large decimals, stale/concurrent edits, duplicate rollback, cursor invalidation, unavailable items and outage-safe removal. HTTP client tests cover malformed data, timeouts and circuit rejection. The packaged validator runs Java 21 default/local profiles and the actual root Liquibase CLI.

The cart endpoints remain unchanged. Phase 8 adds a separate checkout preview; orders, payments, gateway, cache and events remain outside the completed phases. Preview does not reserve stock. See [ADR-013](../adr/ADR-013-cart-ownership-and-live-pricing.md).
