# Phase 14 cache and index decisions

Phase 14 adds a shared TTL cache abstraction for read-only, repeatable responses that remain authoritative in PostgreSQL. The default implementation is in-process and can be disabled with `fresveg.cache.enabled=false`; services use `fresveg.cache.ttl`, which defaults to 60 seconds. Redis remains a deployment concern behind this abstraction and is not used as source of truth for inventory, orders, payments or fulfillment state.

Cached reads:

- Catalog product browsing and category lists cache `ACTIVE` public read pages by normalized filters, sort, page size and cursor.
- Supply public offers and public listing detail cache selected offer reads by normalized product, variant, currency, quantity and cursor, or listing id.
- Fulfillment delivery slot searches cache open slot windows by service area, time window and requested page size.

Invalidation is deliberately coarse for correctness. Catalog product/category writes evict the catalog prefix. Supply location/listing/price writes evict the supply prefix. Fulfillment slot creation and slot reservation during fulfillment creation evict slot caches. No authoritative write path reads or writes cache state.

Indexes were selected from the actual SQL executed by the services:

- Catalog already had the needed Phase 14 indexes before this phase: `(status, name, product_id)`, `(status, code, product_id)`, GIN full-text on product names, GIN JSONB attributes, category/product relation indexes, and category parent indexes. No duplicate catalog indexes were added.
- Supply adds `idx_vendor_listings_active_offer_scan` for active offer scans by product, variant, UOM, minimum order quantity and listing cursor.
- Supply adds `idx_listing_prices_active_offer_scan` for the correlated active price existence check by listing, currency, minimum quantity and effective window.
- Fulfillment adds `idx_delivery_slots_open_window` for open-slot queries without a service area filter.
- Fulfillment adds `idx_delivery_slots_open_area_window` for open-slot queries with a service area filter.

The EXPLAIN ANALYZE target queries for this phase are:

```sql
EXPLAIN ANALYZE SELECT p.* FROM catalog.products p WHERE p.status='ACTIVE' ORDER BY p.name,p.product_id LIMIT 21;
EXPLAIN ANALYZE SELECT p.* FROM catalog.products p WHERE p.status='ACTIVE' AND to_tsvector('simple',p.name) @@ websearch_to_tsquery('simple','apple') ORDER BY p.name,p.product_id LIMIT 21;
EXPLAIN ANALYZE SELECT l.* FROM supply.vendor_listings l JOIN supply.vendor_locations v ON v.location_id=l.location_id AND v.vendor_id=l.vendor_id WHERE l.product_id='00000000-0000-0000-0000-000000000000' AND l.status='ACTIVE' AND v.status='ACTIVE' AND l.minimum_order_quantity<=1 AND EXISTS (SELECT 1 FROM supply.vendor_listing_prices p WHERE p.listing_id=l.listing_id AND p.status='ACTIVE' AND p.currency='USD' AND p.min_quantity<=1 AND p.valid_from<=CURRENT_TIMESTAMP AND (p.valid_to IS NULL OR p.valid_to>CURRENT_TIMESTAMP)) ORDER BY l.listing_id LIMIT 21;
EXPLAIN ANALYZE SELECT slot_id,service_area,start_time,end_time,capacity,reserved_capacity,status,version FROM fulfillment.delivery_slots WHERE status='OPEN' AND start_time>=CURRENT_TIMESTAMP AND start_time<CURRENT_TIMESTAMP + INTERVAL '14 days' ORDER BY start_time,slot_id LIMIT 20;
```
