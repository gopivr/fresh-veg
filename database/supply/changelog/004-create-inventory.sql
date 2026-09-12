-- Inventory aggregate locks serialize every supported stock mutation; ledger is append-only.
CREATE TABLE supply.inventory (
inventory_id UUID PRIMARY KEY,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
created_by UUID, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
updated_by UUID, version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
listing_id UUID NOT NULL UNIQUE REFERENCES supply.vendor_listings(listing_id),
quantity_on_hand NUMERIC(18,6) NOT NULL DEFAULT 0 CHECK(quantity_on_hand>=0),
reserved_quantity NUMERIC(18,6) NOT NULL DEFAULT 0 CHECK(reserved_quantity>=0 AND reserved_quantity<=quantity_on_hand));
CREATE TABLE supply.inventory_batches (
batch_id UUID PRIMARY KEY,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
created_by UUID, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
updated_by UUID, version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
inventory_id UUID NOT NULL REFERENCES supply.inventory(inventory_id),
batch_number VARCHAR(64) NOT NULL CHECK(length(btrim(batch_number))>0),
harvest_date DATE, received_date DATE NOT NULL, best_before_date DATE, expiry_date DATE,
origin VARCHAR(160) NOT NULL, grade VARCHAR(64) NOT NULL, certification_data JSONB NOT NULL CHECK(jsonb_typeof(certification_data)='object'),
quantity_received NUMERIC(18,6) NOT NULL CHECK(quantity_received>0),
quantity_remaining NUMERIC(18,6) NOT NULL CHECK(quantity_remaining>=0 AND quantity_remaining<=quantity_received),
reserved_quantity NUMERIC(18,6) NOT NULL DEFAULT 0 CHECK(reserved_quantity>=0 AND reserved_quantity<=quantity_remaining),
status VARCHAR(16) NOT NULL CHECK(status IN ('ACTIVE','DEPLETED')),
CHECK(harvest_date IS NULL OR harvest_date<=received_date),
CHECK(best_before_date IS NULL OR best_before_date>=received_date),
CHECK(expiry_date IS NULL OR expiry_date>=received_date),
CHECK(best_before_date IS NULL OR expiry_date IS NULL OR best_before_date<=expiry_date),
UNIQUE(inventory_id,batch_number));
CREATE TABLE supply.inventory_reservations (
reservation_id UUID PRIMARY KEY,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
created_by UUID, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
updated_by UUID, version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
inventory_id UUID NOT NULL REFERENCES supply.inventory(inventory_id),
client_id VARCHAR(160) NOT NULL, external_reference VARCHAR(160) NOT NULL CHECK(length(btrim(external_reference))>0),
quantity NUMERIC(18,6) NOT NULL CHECK(quantity>0),
status VARCHAR(16) NOT NULL CHECK(status IN ('ACTIVE','COMMITTED','RELEASED','EXPIRED')),
expires_at TIMESTAMPTZ NOT NULL, allocations JSONB NOT NULL CHECK(jsonb_typeof(allocations)='object'),
UNIQUE(client_id,external_reference));
CREATE TABLE supply.inventory_transactions (
transaction_id UUID PRIMARY KEY,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
created_by UUID, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
updated_by UUID, version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
inventory_id UUID NOT NULL REFERENCES supply.inventory(inventory_id),
batch_id UUID NOT NULL REFERENCES supply.inventory_batches(batch_id),
reservation_id UUID REFERENCES supply.inventory_reservations(reservation_id),
kind VARCHAR(16) NOT NULL CHECK(kind IN ('RECEIPT','ADJUSTMENT','RESERVE','COMMIT','RELEASE','EXPIRE')),
quantity_delta NUMERIC(18,6) NOT NULL, reserved_delta NUMERIC(18,6) NOT NULL,
reason VARCHAR(240) NOT NULL CHECK(length(btrim(reason))>0),
CHECK(quantity_delta<>0 OR reserved_delta<>0));
CREATE INDEX idx_batches_fefo ON supply.inventory_batches(inventory_id,expiry_date,batch_id);
CREATE INDEX idx_reservations_expiration ON supply.inventory_reservations(expires_at,inventory_id) WHERE status='ACTIVE';
CREATE INDEX idx_reservations_inventory ON supply.inventory_reservations(inventory_id);
CREATE INDEX idx_transactions_inventory ON supply.inventory_transactions(inventory_id,transaction_id);
CREATE INDEX idx_transactions_batch ON supply.inventory_transactions(batch_id);
CREATE INDEX idx_transactions_reservation ON supply.inventory_transactions(reservation_id);
COMMENT ON TABLE supply.inventory IS 'Supply-owned Phase 6 stock and auditable reservation lifecycle; no cross-owner foreign keys.';
COMMENT ON TABLE supply.inventory_batches IS 'Supply-owned Phase 6 stock and auditable reservation lifecycle; no cross-owner foreign keys.';
COMMENT ON TABLE supply.inventory_reservations IS 'Supply-owned Phase 6 stock and auditable reservation lifecycle; no cross-owner foreign keys.';
COMMENT ON TABLE supply.inventory_transactions IS 'Supply-owned Phase 6 stock and auditable reservation lifecycle; no cross-owner foreign keys.';
GRANT SELECT, INSERT, UPDATE ON supply.inventory, supply.inventory_batches, supply.inventory_reservations TO fresveg_supply;
GRANT SELECT, INSERT ON supply.inventory_transactions TO fresveg_supply;
