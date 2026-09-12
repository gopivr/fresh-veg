# ADR-015: Durable orders and reservation compensation

Date: 2026-09-09

Status: Accepted and implemented in Phase 9.

## Context

Supply reservations cannot join Commerce's database transaction. A response may be lost after Supply commits, or Commerce may crash before it records an order. Replaying a mutable cart after such a failure can duplicate stock holds or change financial intent. Phase 9 also requires durable snapshots, vendor decisions and an order outbox; later phases add payment and publication on top of that durable record.

## Decision

Commit a permanent customer/key/fingerprint record and full reservation plan before remote mutation. Generate deterministic order/listing references and bound holds to fifteen minutes (or delivery start). Lock the intent during placement and commit order/items/history/creation event/replay response together. Check cart version under its row lock before persistence and uniquely identify each ordered cart revision.

Compensate all planned references after failure, including unknown IDs through a service-only Supply lookup. Retain failed plans through the hold window; recover abandoned plans under row locks. Never compensate a successful durable intent. Cancellation persists its pending state before release and becomes terminal only after all holds are released/expired. Supply independently expires holds. These are durable compensating operations, not distributed atomicity.

Snapshot Account's owned address and active business identity, Catalog product/variant and Supply listing/pricing. Add only necessary owner API reads and horizon-aware reservation orchestration. Use an externally issued, rotatable service JWT file; never mint authentication or use a customer's JWT as a stock service credential.

Vendor acceptance is an item decision while payment remains pending. Vendor rejection cancels the whole order. Vendor reads omit other vendors' items and customer address. No payment, refund, inventory commit or Fulfillment handoff is started from vendor decisions. OrderCreated and OrderCancelled are written to the transactional outbox for the Phase 12 publisher.

## Consequences

Permanent idempotency data and immutable order facts require future retention planning. Failed attempts return 409 on the same key; successful retries retain their original creation response. Read APIs expose current lifecycle state. New keys cannot reorder an unchanged cart revision. Recovery and service-token rotation must be operated; owner outages may leave cancellation pending until restored. Configured delivery windows are not capacity reservations. Explicit owner-local JDBC supports restricted snapshot grants alongside validate-only JPA carts.

See the [order guide](../commerce/orders.md) for exact contracts, arithmetic, lifecycle and validation, and [ADR-006](ADR-006-outbox-pattern.md) for publication sequencing.
