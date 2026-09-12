# Supply listings and pricing — Phase 5

Supply owns vendor offers independently of Catalog master products. Phase 5 implemented four tables and nine operations. Phase 6 adds inventory and reservations as documented in the [inventory guide](inventory.md); checkout remains future work. An offer is a currently effective price, not a promise of stock.

## Configuration and authority

Use the existing [migration guide](../database/migration-guide.md) to bootstrap schemas and provision separate `fresveg_supply` runtime and `fresveg_supply_migrator` credentials. Supply requires DB_URL (default profile), DB_PASSWORD, MIGRATION_DB_PASSWORD, OIDC_ISSUER_URI, OIDC_AUDIENCE, OIDC_JWK_SET_URI, ACCOUNT_BASE_URL and CATALOG_BASE_URL. Local defaults use port 8083 and PostgreSQL localhost; secrets have no defaults. JPA remains validate-only. API_DOCS_ENABLED defaults false, local/test true.

Vendor tokens must validate against both Supply and Account. Supply validates issuer, audience, RS256 signature, subject and expiry itself, then forwards the bearer token and correlation ID to Account `/api/v1/accounts/me` and `/vendor-memberships`. Account's active local identity and stored memberships are authoritative. VENDOR_STAFF can read their vendor's resources; VENDOR_ADMIN can also write. A supplied role claim or stored PLATFORM_ADMIN role alone grants no vendor access. The `vendorId` query parameter selects among verified memberships and does not establish ownership. Body vendorId is rejected. Owned private records outside the caller's membership return the same 404 detail as absent records.

Authorization results last one request only. Membership pages are bounded to 100 pages of 100, with repeated-cursor detection and a 10-second deadline checked before calls. HTTP calls have a 2-second connection timeout and 5-second request timeout, no redirects or retries, and bounded responses. Dependency errors or malformed responses fail closed with 503; denied Account identity fails with 403. No production cross-schema queries, foreign keys or service-module dependencies are introduced.

Catalog product detail is read anonymously through its existing public API. Creation and publication require an ACTIVE product and ACTIVE variant with the exact unit code. Public Supply reads recheck that relationship, plus ACTIVE listing/location and current pricing. Catalog archival or a changed variant unit hides stale offers. Archiving an existing Supply listing remains possible when its Catalog reference is no longer active.

Account currently has no public vendor-status lookup. Revoked membership or suspended Account/vendor state immediately blocks management through Account's existing policy, but does not automatically deactivate previously published Supply offers. Operators must deactivate Supply listings/locations through an authorized vendor administrator before suspension when public withdrawal is required. A future owner contract/event propagation mechanism is still needed; it is not implemented here.

## HTTP operations

All paths below start with `/api/v1/supply`. See the [generated OpenAPI 3.1 contract](../../contracts/openapi/supply-api.yaml).

| Method | Path | Access / behavior |
| --- | --- | --- |
| GET | `/products/{productId}/offers` | Public effective offers; required currency and quantity, optional variantId |
| GET | `/listings/{listingId}` | Public active listing with only currently effective prices |
| GET | `/vendor/listings?vendorId={id}` | Member's paginated listing summaries, including draft/archived |
| POST | `/vendor/listings?vendorId={id}` | Vendor admin creates listing and schedules atomically |
| GET | `/vendor/listings/{listingId}` | Member's listing including non-cancelled expired/current/future schedules |
| PUT | `/vendor/listings/{listingId}` | Vendor admin replaces editable fields/schedules with required version |
| GET | `/vendor/locations?vendorId={id}` | Member's paginated locations |
| POST | `/vendor/locations?vendorId={id}` | Vendor admin creates private location |
| PUT | `/vendor/locations/{locationId}` | Vendor admin updates location with required version |

Location operations make the required listing location usable without direct database writes. Private listing detail allows owners to retrieve schedule IDs and versions for safe replacement. POST returns 201 and Location. Success uses data/meta, collections add pagination; failures use RFC 9457 application/problem+json with code, correlationId and instance. Invalid input is 400, missing/invalid authentication 401, insufficient role 403, hidden/missing record 404, duplicate/stale/conflicting state 409 and unavailable owner dependency 503.

Collections use pageSize 1–100 (default 20), UUID keyset ordering and an opaque nextCursor bound to the resource, owner and offer filters. Filtering occurs before pagination, including Catalog variant/unit validity. Offer currency and quantity must remain unchanged when using a cursor. A read transaction uses PostgreSQL repeatable-read for consistent listing/price versions within that request; later pages and remote Catalog reads are live, not a distributed snapshot.

A listing's vendor, location, product, variant and unit identity cannot change after creation. Create a new listing for a new identity. SKU, MOQ, status and attributes are editable with optimistic version checks. Vendor SKU and location code remain reserved within that vendor. Attributes are a JSON object bounded to 16 KiB UTF-8, 64 keys and depth 8. Location country codes are ISO alpha-2. Locations and their addresses are private vendor resources.

## Pricing contract

Currency is an explicit ISO-4217 code; no default or currency conversion is applied. Quantities use positive NUMERIC(18,6), prices positive NUMERIC(19,6), with BigDecimal throughout. Schedule minQuantity equals the listing MOQ. Offer quantities below MOQ yield no offer. Prices are exact unit prices; rounding final order amounts belongs to a later phase.

Schedules use server UTC time with microsecond input precision. `validFrom` is inclusive, `validTo` exclusive; null validTo means unbounded. Active schedules for the same listing/currency cannot overlap; adjacent windows and different currencies are allowed. Clients cannot select a future evaluation time. The response includes evaluatedAt, selected priceId, optional tierId and listingVersion.

Tier thresholds strictly increase above MOQ and unit prices may only stay equal or decrease. The highest threshold satisfied by the requested quantity sets the unit price for the entire quantity; tiers are not graduated bands. For example, MOQ 1 with base USD 2.75 and a threshold 5 at USD 2.50 returns USD 2.50 per unit for quantity 6.

PUT supplies the complete desired set of non-cancelled schedules, at most 50 with at most 50 tiers each. Include an existing priceId only with its unchanged currency, amount, MOQ, dates and tiers. Omitted schedules become CANCELLED and remain in the database with their tiers; replacements receive new IDs. Cancelled schedules cannot be reactivated. A listing version serializes all aggregate changes, including price-only edits; competing updates produce one success and one 409. Validation or child insertion failure rolls back parent and children together.

The database grants runtime price UPDATE only for status and update-audit/version columns. Amounts and windows are immutable; tier rows allow only SELECT/INSERT. An active-start unique index and local FKs reinforce aggregate validation. Interval overlap is validated in the versioned application aggregate, without PostgreSQL extensions. Runtime credentials are trusted application credentials, not a public pricing administration interface. No business DELETE grants exist.

## Migration and verification

New changeset `supply-003-create-offers` creates vendor_locations, vendor_listings, vendor_listing_prices and price_tiers in supply. All use UUID PKs, TIMESTAMPTZ audits, external Account audit UUIDs and BIGINT version. The listing/location composite FK enforces matching vendor ownership; prices and tiers use local parent FKs. Product/variant/vendor UUIDs have no cross-owner FKs. Six explicit indexes support implemented queries. No seed data or stock schema is added.

Rollback locks all four tables and refuses if any contains data. Empty rollback drops only these objects in dependency order without CASCADE, preserving previous infrastructure/history. Populated installations need a reviewed forward migration or recovery procedure. Earlier numbered changesets remain immutable.

Run `mvn clean verify` from the root. Supply HTTP tests launch the unchanged Account and Catalog JARs against their own isolated PostgreSQL databases, with ephemeral RSA/JWK identity and test-only administrator/member fixtures. The full reactor builds these dependencies first; targeted Supply integration runs need those already-built JARs. A fixed test clock covers exact effective-date boundaries, while production uses Clock.systemUTC(). Tests cover tier decimals, authorization/revocation, foreign ownership, schedule validation/history, rollback, cursor scopes and concurrent edits. Testcontainers validates clean/repeat migration, baseline upgrade, guarded rollback and runtime grants. `scripts/validate-packaged-services.py` also verifies Java 21 default/local startup and the actual Maven bootstrap CLI against an isolated database.

The Phase 5 offer contracts remain unchanged. Phase 6 inventory and reservation operations are additive; cart, order, caching and messaging remain outside the completed Supply phases; Phase 13 gateway routing now exposes only the public Supply paths and excludes internal reservation paths. See [ADR-011](../adr/ADR-011-supply-ownership-and-effective-pricing.md).
