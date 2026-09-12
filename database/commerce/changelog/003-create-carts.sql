-- Phase 7 customer carts only. No prices, checkout, orders or reservations are persisted.
CREATE TABLE commerce.carts (
cart_id UUID PRIMARY KEY, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, created_by UUID, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_by UUID, version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
customer_id UUID NOT NULL, currency VARCHAR(3) NOT NULL CHECK(currency ~ '^[A-Z]{3}$'));
CREATE TABLE commerce.cart_items (
item_id UUID PRIMARY KEY, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, created_by UUID, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_by UUID, version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
cart_id UUID NOT NULL REFERENCES commerce.carts(cart_id), listing_id UUID NOT NULL,
quantity NUMERIC(18,6) NOT NULL CHECK(quantity>0), UNIQUE(cart_id,listing_id));
CREATE INDEX idx_carts_customer_id ON commerce.carts(customer_id,cart_id);
CREATE INDEX idx_cart_items_cart_id ON commerce.cart_items(cart_id,item_id);
COMMENT ON TABLE commerce.carts IS 'Customer identity is verified through Account; external UUID has no cross-schema FK.';
COMMENT ON TABLE commerce.cart_items IS 'Supply listing reference and quantity only; current prices are resolved on reads.';
GRANT SELECT,INSERT ON commerce.carts TO fresveg_commerce;
GRANT UPDATE(updated_at,updated_by,version) ON commerce.carts TO fresveg_commerce;
GRANT SELECT,INSERT,DELETE ON commerce.cart_items TO fresveg_commerce;
GRANT UPDATE(quantity,updated_at,updated_by,version) ON commerce.cart_items TO fresveg_commerce;
