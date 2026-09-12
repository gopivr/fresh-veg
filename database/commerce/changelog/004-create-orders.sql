-- Durable immutable order facts; only lifecycle columns can be updated by runtime.
CREATE TABLE commerce.orders (
 order_id UUID PRIMARY KEY, order_number VARCHAR(40) NOT NULL UNIQUE, customer_id UUID NOT NULL,
 cart_id UUID NOT NULL REFERENCES commerce.carts(cart_id), cart_version BIGINT NOT NULL CHECK(cart_version>=0),
 status VARCHAR(24) NOT NULL CHECK(status IN ('PENDING_PAYMENT','CANCEL_PENDING','CANCELLED')),
 currency VARCHAR(3) NOT NULL CHECK(currency ~ '^[A-Z]{3}$'),
 subtotal NUMERIC(38,6) NOT NULL CHECK(subtotal>=0), discount_amount NUMERIC(38,6) NOT NULL CHECK(discount_amount>=0 AND discount_amount<=subtotal),
 tax_amount NUMERIC(38,6) NOT NULL CHECK(tax_amount>=0), delivery_fee NUMERIC(38,6) NOT NULL CHECK(delivery_fee>=0),
 grand_total NUMERIC(38,6) NOT NULL CHECK(grand_total=subtotal-discount_amount+tax_amount+delivery_fee),
 shipping_address_snapshot JSONB NOT NULL CHECK(jsonb_typeof(shipping_address_snapshot)='object'),
 delivery_slot_id UUID NOT NULL, delivery_slot_snapshot JSONB NOT NULL, pricing_policy_id VARCHAR(100) NOT NULL,
 payment_method_id UUID, reservation_expires_at TIMESTAMPTZ NOT NULL, response_snapshot JSONB NOT NULL,
 created_at TIMESTAMPTZ NOT NULL, created_by UUID NOT NULL, updated_at TIMESTAMPTZ NOT NULL, updated_by UUID NOT NULL,
 version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0), UNIQUE(cart_id,cart_version));
CREATE INDEX idx_orders_customer ON commerce.orders(customer_id,order_id);
CREATE INDEX idx_orders_expiry ON commerce.orders(status,reservation_expires_at);
CREATE TABLE commerce.order_items (
 order_item_id UUID PRIMARY KEY, order_id UUID NOT NULL REFERENCES commerce.orders(order_id), product_id UUID NOT NULL,
 listing_id UUID NOT NULL, vendor_id UUID NOT NULL, product_name VARCHAR(240) NOT NULL, vendor_name VARCHAR(160) NOT NULL,
 vendor_sku VARCHAR(64) NOT NULL, unit_code VARCHAR(32) NOT NULL, quantity NUMERIC(18,6) NOT NULL CHECK(quantity>0),
 unit_price NUMERIC(19,6) NOT NULL CHECK(unit_price>0), discount_amount NUMERIC(38,6) NOT NULL CHECK(discount_amount>=0),
 tax_amount NUMERIC(38,6) NOT NULL CHECK(tax_amount>=0), line_total NUMERIC(38,6) NOT NULL CHECK(line_total>=0),
 vendor_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK(vendor_status IN ('PENDING','ACCEPTED','REJECTED')),
 product_snapshot JSONB NOT NULL, reservation_id UUID NOT NULL, created_at TIMESTAMPTZ NOT NULL, UNIQUE(order_id,listing_id));
CREATE INDEX idx_order_items_vendor ON commerce.order_items(vendor_id,order_id);
CREATE TABLE commerce.order_status_history (
 history_id UUID PRIMARY KEY, order_id UUID NOT NULL REFERENCES commerce.orders(order_id), status VARCHAR(24) NOT NULL,
 reason VARCHAR(240) NOT NULL, actor_id UUID NOT NULL, created_at TIMESTAMPTZ NOT NULL);
CREATE INDEX idx_order_status_history_order ON commerce.order_status_history(order_id,created_at);
CREATE TABLE commerce.idempotency_records (
 record_id UUID PRIMARY KEY, customer_id UUID NOT NULL, idempotency_key VARCHAR(128) NOT NULL,
 request_hash VARCHAR(64) NOT NULL, status VARCHAR(24) NOT NULL CHECK(status IN ('PREPARED','SUCCEEDED','FAILED','COMPENSATED')),
 plan JSONB NOT NULL, response JSONB, order_id UUID REFERENCES commerce.orders(order_id),
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, UNIQUE(customer_id,idempotency_key));
CREATE INDEX idx_idempotency_recovery ON commerce.idempotency_records(status,updated_at);
CREATE TABLE commerce.outbox_events (
 event_id UUID PRIMARY KEY, aggregate_type VARCHAR(64) NOT NULL, aggregate_id UUID NOT NULL REFERENCES commerce.orders(order_id),
 event_type VARCHAR(64) NOT NULL, payload JSONB NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','PUBLISHED')),
 created_at TIMESTAMPTZ NOT NULL, published_at TIMESTAMPTZ, retry_count INTEGER NOT NULL DEFAULT 0 CHECK(retry_count>=0));
CREATE INDEX idx_outbox_pending ON commerce.outbox_events(status,created_at,event_id);
GRANT SELECT,INSERT ON commerce.orders,commerce.order_items,commerce.order_status_history,commerce.idempotency_records,commerce.outbox_events TO fresveg_commerce;
GRANT UPDATE(vendor_status) ON commerce.order_items TO fresveg_commerce;
GRANT UPDATE(status,updated_at,updated_by,version) ON commerce.orders TO fresveg_commerce;
GRANT UPDATE(status,response,order_id,updated_at) ON commerce.idempotency_records TO fresveg_commerce;
COMMENT ON TABLE commerce.idempotency_records IS 'Permanent customer-scoped request keys and precommitted reservation intent for crash compensation; no automatic key reuse.';
COMMENT ON TABLE commerce.outbox_events IS 'Order event inserted atomically with order state. Publication belongs to Phase 12.';
