# Inventory and batches — Phase 6

Supply now owns inventory, inventory_batches, inventory_transactions and inventory_reservations in addition to Phase 5 offers/pricing. Existing offer responses and price semantics remain unchanged. No cart, checkout or order implementation is introduced.

## Stock and concurrency

Each listing can have one inventory aggregate. Initialization creates zero stock, safely returning the existing aggregate on sequential repeats. A simultaneous initialization may return 409 on the unique listing constraint; reload and retry. Vendor admins receive stock by creating a batch. Every receipt, adjustment, reserve, commit, release and expiration appends an inventory transaction in the same PostgreSQL transaction as the quantity change. Runtime credentials cannot update/delete ledger rows or delete stock/history.

Every mutation locks the inventory row with SELECT FOR UPDATE. Batch changes, reservation allocations and the movement ledger use that same transaction and lock order. Inventory rows are refreshed after lock acquisition to avoid stale persistence-context state. Reservation state is refreshed after acquiring its inventory lock. Vendor mutations also require the caller's current inventory version; races return 409. Internal lifecycle retries use persisted identity/status rather than a client-provided stock balance. Database checks enforce on-hand >= 0, reserved >= 0 and reserved <= on-hand for inventory and batches.

Quantities are NUMERIC(18,6), mapped to BigDecimal; no floating-point arithmetic or stored derived availability. Vendor availableQuantity is the physical balance `quantityOnHand - reservedQuantity`. It may include expired physical stock; reservation eligibility additionally excludes batches that cannot remain usable for the entire hold. Dispose of expired stock through an explicit negative adjustment with a reason. Expiry does not silently subtract physical stock.

Batch receipt records harvest/received/best-before/expiry dates, origin, grade, certification metadata, quantity received and remaining. Expiry is exclusive at UTC midnight; batches expiring today cannot be received. Harvest must not follow receipt; best-before and expiry cannot precede receipt, and best-before cannot follow expiry. Certification metadata is bounded to 64 string entries, keys 160 characters and values 240 characters. Batch numbers are unique within inventory. Quantities and receipt metadata are retained; empty batches become DEPLETED.

Corrections require a batch, signed nonzero quantityDelta, reason and aggregate version. They cannot consume reserved stock or increase remaining quantity beyond the original receipt. Record new stock as a new batch; a positive correction can restore a previously reduced receipt. Neither endpoint accepts an authoritative available/reserved balance from callers. PUT inventory and POST adjustments use the same audited correction operation.

## Reservation lifecycle

A caller supplies inventoryId, externalReference, quantity and expiresAt. The server derives caller identity from a validated JWT. The pair (service subject, external reference) is permanently unique. An identical retry returns the existing reservation, including terminal status; reuse with different inventory, quantity or expiration returns 409. There is no retention/cleanup job that could silently reuse these keys.

Expiry must be in the future, no more than one hour ahead and use microsecond precision. Reservations allocate eligible batch balances in FEFO order (expiry ascending, null last, then UUID), potentially spanning batches. A batch must remain usable through the requested expiry. The reservation retains batch allocations and increments each batch and aggregate reserved balance. It returns 409 INV-409-001 when eligible stock is insufficient; partial allocations are never persisted. Listing and location must be ACTIVE. This operation does not resolve prices or make additional Account/Catalog calls; the trusted future checkout caller must validate those domain requirements separately.

Commit consumes exactly the recorded batch quantities and releases their reserved balances atomically. Release leaves on-hand unchanged and releases the same allocation. Repeating the same terminal action is a no-op; switching between COMMITTED and RELEASED returns 409. A late commit/release expires an ACTIVE reservation and returns EXPIRED without consuming stock. Only the creating service subject can act on its reservation; another service gets 404.

The automatic worker defaults on, polling every 30 seconds and selecting up to 100 inventory aggregates with due reservations per run. Each inventory is expired in its own transaction. Row locks and rechecked state make repeated/multiple-worker expiration safe. Reserve also expires due reservations for its inventory before allocation, while commit/release check their target's deadline. Worker failures are logged without credentials or request data and retried on later polls. Processing delay can temporarily retain expired holds in vendor balances; it cannot authorize a late commit. Large batch/active-reservation sets currently load per aggregate and need measurement before high-volume deployment.

## Security and configuration

Vendor stock reads require existing Account-backed VENDOR_STAFF or VENDOR_ADMIN membership; initialization, receipts and corrections require VENDOR_ADMIN. Supplied vendor IDs are selectors verified against live membership; foreign owned inventory returns 404. Account and Catalog production code and their contracts are unchanged.

Internal POST endpoints require a correctly signed, unexpired Supply-audience JWT from the configured issuer, a subject explicitly listed in `INVENTORY_SERVICE_SUBJECTS`, and `inventory.reserve` in its scope. Empty configuration denies all reservation callers. The issuer must issue these subjects/scopes only to trusted service clients; do not allow customers to self-select them. Subjects are also the reservation ownership/idempotency namespace. These tokens do not need Account membership. Internal paths are not routed by the gateway. Restrict direct network exposure in deployment; gateway/network hardening remains scheduled for its phase.

| Variable | Default / meaning |
| --- | --- |
| INVENTORY_SERVICE_SUBJECTS | Empty; comma-separated trusted service JWT subjects |
| INVENTORY_EXPIRATION_ENABLED | true; false disables background polling but not lifecycle expiry checks |
| INVENTORY_EXPIRATION_DELAY_MS | 30000; initial and fixed delay between polls |

Existing Supply database, owner URL and OIDC variables remain required. Migration startup uses fresveg_supply_migrator; runtime uses fresveg_supply. JPA remains validate-only. The generated [Supply OpenAPI](../../contracts/openapi/supply-api.yaml) documents both vendor and internal APIs. API docs retain existing enabled/profile behavior.

## Added HTTP operations

All vendor paths below are prefixed `/api/v1/supply/vendor`.

| Method | Path | Request / result |
| --- | --- | --- |
| GET | `/inventory?vendorId={id}` | Inventory balances and versions |
| POST | `/inventory` | listingId; zero initialization or existing inventory |
| PUT | `/inventory/{inventoryId}` | version, batchId, quantityDelta, reason; corrected inventory |
| POST | `/inventory/{inventoryId}/adjustments` | Same audited correction contract |
| POST | `/inventory/{inventoryId}/batches` | version and receipt metadata/quantity; updated inventory |
| GET | `/inventory/{inventoryId}/batches` | Batch details and balances |
| GET | `/inventory/{inventoryId}/transactions` | Immutable movement history |
| POST | `/internal/v1/inventory/reservations` (no vendor prefix) | Create or replay a service-owned hold |
| POST | `/internal/v1/inventory/reservations/{id}/commit` (no vendor prefix) | Commit or replay terminal result |
| POST | `/internal/v1/inventory/reservations/{id}/release` (no vendor prefix) | Release or replay terminal result |

All successful additions return 200 data/meta envelopes. Collections use pageSize 1–100 (default 20) and opaque filter-bound UUID cursors. Ledger UUID ordering is a stable traversal order, not chronological ordering. Error envelopes remain RFC 9457 with code/correlationId/instance; invalid inputs 400, missing/invalid token 401, denied role/scope 403, hidden/missing resource 404 and conflicts 409. Existing Supply operations remain backward compatible.

## Migration and validation

`supply-004-create-inventory` appends four owned tables with UUID PKs, TIMESTAMPTZ audits and version columns, local FKs, quantity/date/status checks and six query indexes. Inventory's listing FK is within Supply; no external FK or cross-schema access is added. Runtime ledger grants are SELECT/INSERT only. No production seed rows, extensions or deletes are added. A guarded rollback locks the new tables, refuses any inventory data and otherwise drops only these objects, preserving populated Phase 5 listings.

`mvn clean verify` runs existing regressions and real PostgreSQL stock tests, including eight simultaneous last-unit attempts, competing commit/release actions, idempotency, service ownership, FEFO across batches, expiry, adjustment/receipt rollback, cursor scopes and SQL ledger/batch/aggregate reconciliation. Migration tests upgrade a populated Phase 5 listing installation, repeat unchanged, rollback/reapply empty inventory, refuse populated rollback and verify runtime privileges/stock constraints. The packaged validator checks all six services on Java 21 in default/local profiles and the actual root Liquibase CLI. See [ADR-012](../adr/ADR-012-inventory-locking-and-reservations.md).
