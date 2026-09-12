# Orders, Payments And Outbox Publication — Phase 12

Commerce creates durable orders from a customer's current cart. It repeats checkout validation, snapshots Account address/vendor identity, Catalog product/variant metadata and Supply listing/price data, and reserves eligible Supply batches through the delivery window. Client amounts, customer identity and vendor ownership are never authoritative.

## Customer API

- `POST /api/v1/orders` requires `Idempotency-Key` and JSON `cartId`, `deliveryAddressId`, `deliverySlotId` and `paymentMethodId`. Commerce stores only the payment method UUID reference; raw card, bank or wallet credentials are never accepted.
- `GET /api/v1/orders/{orderId}` returns immutable snapshots and current order status/version.
- `GET /api/v1/orders?pageSize=10&cursor=<order UUID>` returns data containing `data`, `nextCursor`, `hasNext`, inside the normal data/meta envelope. Page size is 1–10; UUID ordering is stable, not chronological. A cursor must belong to the authenticated customer.
- `POST /api/v1/orders/{orderId}/cancel` cancels a pending unpaid order. Repeated cancellation is safe.

POST returns 201 and Location, including on successful replay. Keys contain 1–128 ASCII letters/digits/`.`/`_`/`:`/`-`, beginning with a letter/digit. They are permanently scoped to Account customer and order creation, with a SHA-256 fingerprint of the normalized request. A successful replay returns the original creation response, even if the cart, prices, address or order status later changes. GET returns current lifecycle state. Changed intent or an in-progress/failed key returns 409. Keys are never deleted or reused automatically. Missing/invalid keys and unknown JSON fields return 400.

A unique `(cart_id, cart_version)` prevents a second key from ordering the same cart revision. A new deliberate order requires a cart edit or new cart. Cart contents are preserved. Final persistence locks the cart and verifies the previewed version; concurrent edits invalidate placement and cause compensation. Foreign/missing customer resources return indistinguishable 404 responses.

## Vendor API

`GET /api/v1/vendor/orders` and `GET /api/v1/vendor/orders/{orderId}` require `vendorId` and a live Account VENDOR_ADMIN or VENDOR_STAFF membership. The list accepts the same bounded UUID pagination. Responses contain only that vendor's item snapshots, delivery window, order state/version and vendor decision, without the customer's shipping address or other vendors' totals/items.

`POST /api/v1/vendor/orders/{orderId}/accept` and `/reject` require `vendorId`, JSON `version`, and stored VENDOR_ADMIN membership. Decisions lock the order and enforce its version. Phase 10 normally advances newly created orders through payment and stock commit to CONFIRMED in the create request, so these decision endpoints now reject confirmed orders with 409. They remain scoped and versioned for future lifecycle work. Membership failures return 403; valid vendors cannot read unrelated order IDs (404).

## Lifecycle and failure recovery

Orders are first stored as PENDING_PAYMENT with Supply holds, then Commerce authorizes payment through a provider-neutral `PaymentGateway`. The built-in local adapter records deterministic references for tests and local runs. Authorized orders move to PAYMENT_AUTHORIZED, commit each Supply reservation through Supply's existing service endpoint, and then move to CONFIRMED. Declined authorization records PAYMENT_FAILED, writes a failed payment attempt/outbox event and releases all inventory holds.

Before any remote reservation, Commerce commits an idempotency record containing the complete snapshot plan, generated order UUID, expiry and deterministic per-listing reservation references. The placement transaction locks that record, reserves all items, then inserts order/items/history/OrderCreated outbox and successful replay response in one local transaction. It has a 120-second transaction timeout and a ninety-second placement deadline checked between bounded owner calls. Calls already in flight can extend a deadline by their configured timeout.

Failure rolls back order/items/history/outbox and attempts every planned release, resolving unknown reservation IDs by caller-owned external reference. The precommitted journal survives process failure and ambiguous network/database acknowledgements. A durably successful record is never compensated as a failed submission. Failed attempts remain journaled, and recovery revisits stale PREPARED/FAILED rows after two minutes, locking with SKIP LOCKED. It checks through expiry plus two minutes before marking cleanup complete, covering delayed/lost remote responses. Supply's independent expiry worker is an additional safety net.

Cancellation of PENDING_PAYMENT or PAYMENT_FAILED orders keeps the Phase 9 release path: it first commits CANCEL_PENDING, then advances to CANCELLED only after release/expiry is confirmed. Cancellation of a CONFIRMED order uses the stored authorized payment attempt, records a refund result through the payment gateway, and moves directly to CANCELLED after a completed local refund. Committed Supply reservations are not released during confirmed cancellation; Fulfillment/refund settlement details remain later-phase work.

Recovery runs every `ORDER_RECOVERY_DELAY_MS` (default 30000), at most 100 stale intents and 100 cancellation/expiry candidates per scan. Work is bounded per candidate, and failures log exception classes without tokens, SQL parameters or address snapshots. Permanent owner/credential outages retain cleanup intent and require operational restoration; they do not invent successful release.

## Snapshot and money policy

Orders retain address/version, delivery quote, pricing policy, totals and full item snapshots. Items include product/listing/vendor UUIDs, product/vendor names, vendor SKU, unit, quantity, exact unit price, allocated discount/tax and line total, plus product/listing/vendor/price JSON. No later owner lookup changes order history. Vendor decisions alone are mutable item fields.

Phase 8's BigDecimal/HALF_UP currency rules remain authoritative. Order-level discount and tax are allocated in cart-item order proportionally over remaining rounded subtotals, rounding each share DOWN to currency minor units and assigning the final residual to the last line. Remaining-weight allocation avoids negative lines for many small items near a full discount. Fee-related tax, when configured, is included in allocated tax. The sum of line totals plus delivery fee equals grand total. Totals use NUMERIC(38,6) to accommodate multiplication of permitted Supply quantity and unit-price ranges; unit prices remain NUMERIC(19,6), quantities NUMERIC(18,6).

## Owner APIs and credentials

Commerce requires existing Account/Supply URLs and checkout configuration, plus:

- `CATALOG_BASE_URL`: trusted Catalog base URL for product snapshots. Empty disables successful order creation without preventing cart/health startup.
- `INVENTORY_SERVICE_TOKEN_FILE`: mounted file containing an externally issued Supply-audience bearer JWT with scope `inventory.reserve` and a subject listed in Supply's `INVENTORY_SERVICE_SUBJECTS`. Empty fails closed for reservations. The file is read on each service call, supports rotation without restart, is bounded to 16 KiB, and is never logged. Mount it read-only with restricted filesystem permissions and rotate before expiry, preserving the service subject. A missing lookup during cancellation is not proof of release and remains pending. Customer bearer tokens are forwarded only to Account, never substituted for the service credential.
- Existing `OWNER_CONNECT_TIMEOUT_MS`, `OWNER_REQUEST_TIMEOUT_MS` and `OWNER_MAX_CONCURRENT` bound the new service/Catalog calls. There is no blind reservation retry; recovery uses durable references.
- `LOCAL_PAYMENT_DECLINED_METHOD_IDS`: comma-separated payment method UUIDs that the local adapter declines. Empty default authorizes.
- `LOCAL_PAYMENT_REFUND_FAILURE_ATTEMPT_IDS`: comma-separated payment attempt UUIDs that the local adapter treats as refund failures for testing. Empty default refunds.

Account adds authenticated `GET /api/v1/accounts/vendors/{vendorId}`, returning only active vendor ID and business name, never contact/member data. Inactive or absent vendors return 404. Order placement therefore rejects unavailable vendor identity, while existing public offer behavior is preserved.

Supply adds service-authorized `POST /internal/v1/inventory/order-reservations` with listingId, externalReference, quantity, expiresAt and requiredUntil. It reuses locked inventory, FEFO allocation and the existing ledger, but eligible batch expiry must cover requiredUntil (at most thirty days ahead and no earlier than hold expiry). Reused reservations must also cover the requested horizon. `GET /internal/v1/inventory/reservations/by-reference?externalReference=...` returns only the calling service's reservation or 404. Existing reservation/commit/release APIs remain compatible. Internal paths are never gateway routes.

## Persistence and limits

Commerce changeset 004 adds orders, order_items, order_status_history, idempotency_records and outbox_events. Changeset 005 appends payment_attempts and refunds and expands the order status check to include PAYMENT_AUTHORIZED, CONFIRMED and PAYMENT_FAILED. Changeset 006 grants only outbox publish-state updates. Every PK is UUID; all FKs remain within Commerce. Runtime privileges forbid order deletion, financial/address/item snapshot changes, history deletion, payment/refund mutation and event-payload rewriting. JDBC repositories issue explicit owner-local SQL under Spring transactions; existing JPA entities remain validate-only. Clean migration, Phase 7 upgrade, repeat/checksum stability, empty rollback/reapply, populated refusal, local FKs and restricted grants are tested. Rollback refuses payment data and preserves prior carts/orders.

Outbox events use the Phase 12 envelope (`eventId`, `eventType`, `eventVersion`, `aggregateId`, `occurredAt`, `correlationId`, `payload`). Only minimal order identifiers/status/currency/total are included; addresses and full snapshots are not broadcast. The publisher drains `PENDING` rows through no-op/local/Kafka modes. Publication failures increment `retry_count` and leave committed orders/payments intact for retry.

Remote observations are not a distributed snapshot. Delivery is the existing configured coverage/window policy, not a capacity reservation. Holds prevent stock overselling through delivery eligibility but do not guarantee shipping capacity. Account/Catalog/Supply availability and a valid service credential are required for new orders. Large carts make multiple bounded owner calls; batching, streamed response-size limits, recovery throughput and production load testing remain hardening work. See [ADR-015](../adr/ADR-015-durable-orders-and-reservation-compensation.md), [checkout](checkout.md) and [inventory](../supply/inventory.md).
