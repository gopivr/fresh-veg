# Database ownership

Phase 2 implements PostgreSQL database `fresveg` with five owned schemas, scoped runtime/migration roles and Liquibase metadata. Phases 3–11 implement eight Account, six Catalog, eight Supply, nine Commerce and six Fulfillment business tables below.

| Schema / service | Owned tables (implementation status stated below) |
| --- | --- |
| account / account-service | users, customer_profiles, addresses, vendors, vendor_users, roles, user_roles, user_preferences |
| catalog / catalog-service | products, product_variants, categories, product_categories, units_of_measure, product_images; product_attributes only if normalization is justified |
| supply / supply-service | Implemented: vendor_locations, vendor_listings, vendor_listing_prices, price_tiers, inventory, inventory_batches, inventory_transactions, inventory_reservations |
| commerce / commerce-service | Implemented: carts, cart_items, orders, order_items, order_status_history, idempotency_records, outbox_events, payment_attempts, refunds |
| fulfillment / fulfillment-service | delivery_slots, fulfillments, fulfillment_items, shipments, shipment_events, delivery_assignments |

Gateway and common own no business schema. Separate service credentials (`fresveg_account`, `fresveg_catalog`, `fresveg_supply`, `fresveg_commerce`, `fresveg_fulfillment`) restrict access to the owned schema. Production migration roles have necessary DDL privileges; runtime roles receive only required access. Phase 2 revokes PUBLIC database/schema access, protects metadata and proves isolation through real scoped connections. Account, Catalog, Supply and Fulfillment migration 003 each grant only the DML needed for their APIs; later owners must likewise use explicit grants. Supply 004 adds stock constraints and an append-only movement ledger with SELECT/INSERT-only runtime grants. Commerce 003 grants cart audit/version updates and item quantity/audit/version updates only, protecting stored ownership/listing identity. Commerce 004 and 005 protect order/payment financial snapshots, history, events and payment/refund rows from runtime rewrites while allowing the narrow lifecycle/recovery columns required by the application. Commerce 006 additionally allows only outbox `status`, `published_at` and `retry_count` updates for publication. Fulfillment 003 grants slot capacity reservation, fulfillment status, shipment status and event insert privileges without runtime DDL or snapshot rewrites. Secrets are environment-supplied.

Internal relationships use foreign keys; external references remain UUID values without cross-schema foreign keys or JPA associations. For example, commerce.order_items.order_id has a local FK, while commerce.orders.customer_id and supply.vendor_listings.product_id do not have external FKs. Resolve external validity through owner APIs and preserve historical snapshots.

## Migration plan

Use `database/master/db.changelog-master.yaml` and `database/<owner>/db.changelog-<owner>-master.yaml`, with numbered files under each owner's `changelog/`. The root includes account, catalog, supply, commerce, fulfillment in that order, using explicit relative include resolution. Each service runs only its own packaged master; root bootstrap is a separate local/operator path. Bootstrap and service startup use disjoint required contexts and stable logical paths. The administrator root records only bootstrap changes in public.fresveg_bootstrap_changelog; service changes use owned databasechangelog/databasechangeloglock tables. See [the migration guide](../database/migration-guide.md) and [ADR-008](../adr/ADR-008-liquibase-execution-boundaries.md).

Every change has a stable service-prefixed ID and author, comments, applicable preconditions, constraints/indexes, and rollback when safe. Deployed changesets are immutable. Use expand/migrate/contract for future incompatible changes. Validate clean application and an unchanged second run against PostgreSQL.

## Data conventions

UUID primary keys; TIMESTAMPTZ timestamps; NUMERIC/DECIMAL for money and quantity with explicitly chosen precision/scale; ISO-4217 currencies. Use VARCHAR plus application enums/check constraints rather than PostgreSQL enum types. Most business tables include created_at, created_by, updated_at, updated_by and version; use `@Version` where needed. Historical transactions are retained; business status replaces indiscriminate soft deletion.

Use `idx_<table>_<columns>`, `uk_<table>_<columns>`, `fk_<child>_<parent>` and `ck_<table>_<rule>`. Index real query patterns, not every column. Inventory must enforce `quantity_on_hand >= 0`, `reserved_quantity >= 0`, and `reserved_quantity <= quantity_on_hand` alongside concurrency-safe writes. Demo data belongs only to local/test profiles. Hibernate validates and never creates/updates schema.

See [ADR-002](../adr/ADR-002-postgresql-database-ownership.md) and [ADR-003](../adr/ADR-003-liquibase-strategy.md).
