# Service boundaries

| Component | Owns | Boundary rule |
| --- | --- | --- |
| Account | Users, profiles, addresses, vendors, memberships, roles and preferences | External identity authenticates; Account stores FresVeg identity and membership state. |
| Catalog | Categories, products, variants, units, images and product metadata | Global product identity only; no vendor prices or inventory. |
| Supply | Vendor locations, listings, price tiers, inventory, batches, ledger and reservations | Authoritative offer and stock state; references Account/Catalog UUIDs without cross-schema foreign keys. |
| Commerce | Carts, checkout, order snapshots, status history, idempotency, payments, refunds and outbox | Owns purchase intent and historical snapshots; never trusts client-calculated totals. |
| Fulfillment | Delivery slots, fulfillment records, items, shipments, shipment events and assignments | Owns delivery lifecycle; references orders by UUID. |
| API Gateway | Public routing, JWT validation, CORS, correlation, security headers and coarse rate limiting | No domain persistence; `/internal/**` is not publicly routed. |
| Platform Common | Reusable technical primitives | No shared domain model or cross-service repositories. |

No service reads or writes another service schema. Shared integration happens through versioned HTTP contracts and events.
