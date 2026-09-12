# ADR-011: Supply ownership and effective pricing

Status: Accepted in Phase 5.

Supply offers must reference the existing Account vendors and Catalog product/variant identities without accessing their databases. Vendor authority must come from stored membership, and effective prices must be resolved on the server with exact decimal arithmetic.

Supply uses the existing Account identity and paginated membership APIs for each protected request. A vendor selector is accepted only after membership verification; VENDOR_STAFF reads and VENDOR_ADMIN writes. Catalog public product detail supplies active variant/unit validation. No Account/Catalog production changes or cross-owner persistence dependencies are needed. Failure closes the protected operation. Public vendor suspension propagation remains a documented contract gap because Account has no public vendor-status lookup.

Four Supply-owned tables represent locations, listings, immutable price schedules and quantity tiers. The listing's owner/location/product/variant/unit identity is fixed. A local composite FK enforces location/vendor agreement. UUIDs referencing Account/Catalog have no cross-schema FK. Runtime grants prevent price-amount/window and tier mutation, as well as business deletion.

Price validity is half-open UTC time, currency-specific and non-overlapping within the desired schedule set. Quantity and currency are explicit offer inputs. The highest applicable tier selects one exact unit price for the whole quantity. Schedule base minimum equals listing MOQ. Retaining an existing price ID requires unchanged business fields; replacement cancels old rows and inserts new immutable schedules, retaining history. Parent optimistic locking serializes the entire write and repeatable-read keeps database reads consistent within each response. Application overlap checks avoid a new PostgreSQL extension; they rely on all supported writes using this versioned aggregate.

The private listing-detail and three location operations supplement the master's listing APIs so callers can manage required locations and retrieve configured future price IDs through authorized HTTP operations. They do not implement stock. OpenAPI, unit tests, real owner-service HTTP integration, migration/rollback and Java 21 packaged validation define the phase's acceptance evidence.

Consequences: owner availability and token audiences are required for vendor management, and Catalog availability is required for public offer validation. Cross-page and cross-service reads are live rather than atomic. Cancellation history is retained but not exposed as a separate history API. Public offers do not assert availability or reserve inventory. Later phases must preserve these ownership, monetary and migration boundaries. See the [Supply guide](../supply/supply-service.md) for contract and operational details.
