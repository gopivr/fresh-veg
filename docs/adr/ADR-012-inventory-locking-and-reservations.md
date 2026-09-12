# ADR-012: Inventory locking, batch allocation and reservation identity

Status: Accepted in Phase 6.

Stock changes must never oversell or lose movement history. Supply owns all four inventory tables and the existing listing reference. Each mutation acquires the inventory row's PostgreSQL write lock before updating aggregate, batches, reservations or ledger. Vendor edits additionally use the aggregate version. This keeps one lock order across all supported operations without optimistic retry loops or cross-service transactions.

Reservations record their FEFO batch allocation in bounded-by-stock JSONB within the reservation row and maintain per-batch reserved quantities. The inventory lock serializes all accesses; internal relational relationships use FKs, while allocation keys are validated through the application. Explicit allocations prevent later commits from consuming a different or expired batch. A batch must outlive the hold; commit after the hold deadline expires/releases it. Physical stock remains until commit or an audited correction, even when its expiry date passes.

Every quantity/reserved delta writes an immutable inventory transaction in the same transaction. Runtime ledger UPDATE/DELETE are denied. Zero initialization has no quantity delta. The runtime principal remains trusted application infrastructure: arbitrary direct writes could bypass application reconciliation and are not a supported operational interface.

Service reservation access requires the configured trusted issuer/audience, an explicit subject allowlist and inventory.reserve scope. The service subject owns the reservation and scopes the external reference. Exact request retries return persisted lifecycle state; changed intent conflicts. Commit/release replay cannot create another ledger movement. Expiration is periodic and also checked in lifecycle operations; inventory locks make multiple workers safe.

The existing nine offer/location/listing operations remain unchanged. Ten added operations make stock initialization, receipt, correction, history and internal lifecycle usable through authorized HTTP APIs. FEFO, expiration and stock correctness are implemented here; pricing/Account/Catalog validation orchestration, cart, checkout and order behavior stay in later phases. High-volume batch scanning, global service credential lifecycle and deployment network policy remain operational considerations. See the [inventory guide](../supply/inventory.md).
