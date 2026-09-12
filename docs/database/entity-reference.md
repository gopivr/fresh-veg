# Entity reference

| Service | Core entities |
| --- | --- |
| Account | users, customer_profiles, account_addresses, vendors, vendor_memberships, account_roles, preferences |
| Catalog | categories, products, product_variants, units, product_images, product_metadata |
| Supply | vendor_locations, listings, listing_prices, inventory, inventory_batches, inventory_reservations, inventory_transactions |
| Commerce | carts, cart_items, checkout/orders, order_items, order_status_history, idempotency_records, outbox_events, payment_attempts, refunds |
| Fulfillment | delivery_slots, fulfillments, fulfillment_items, shipments, shipment_events, delivery_assignments |

Order and fulfillment tables store snapshots for data that can change later, including product names, prices, delivery slots and shipping addresses.
