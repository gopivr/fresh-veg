-- Vendor offers and pricing only. No inventory, reservations or product-master prices.

CREATE TABLE supply.vendor_locations (
    location_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    vendor_id UUID NOT NULL,
    code VARCHAR(64) NOT NULL CHECK (code ~ '^[A-Z0-9][A-Z0-9_-]{0,63}$'),
    name VARCHAR(160) NOT NULL CHECK (length(btrim(name)) > 0),
    line1 VARCHAR(200) NOT NULL CHECK (length(btrim(line1)) > 0),
    city VARCHAR(100) NOT NULL CHECK (length(btrim(city)) > 0),
    postal_code VARCHAR(20) NOT NULL CHECK (length(btrim(postal_code)) > 0),
    country_code VARCHAR(2) NOT NULL CHECK (country_code ~ '^[A-Z]{2}$'),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','INACTIVE')),
    UNIQUE(vendor_id, code),
    UNIQUE(location_id, vendor_id)
);
COMMENT ON TABLE supply.vendor_locations IS 'Supply-owned vendor_locations; external Account and Catalog identifiers have no cross-schema foreign keys.';

CREATE TABLE supply.vendor_listings (
    listing_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    vendor_id UUID NOT NULL,
    location_id UUID NOT NULL,
    product_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    vendor_sku VARCHAR(64) NOT NULL CHECK (vendor_sku ~ '^[A-Z0-9][A-Z0-9_-]{0,63}$'),
    uom_code VARCHAR(16) NOT NULL CHECK (length(btrim(uom_code)) > 0),
    minimum_order_quantity NUMERIC(18,6) NOT NULL CHECK (minimum_order_quantity > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED')),
    attributes JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(attributes) = 'object'),
    UNIQUE(vendor_id, vendor_sku),
    FOREIGN KEY(location_id, vendor_id) REFERENCES supply.vendor_locations(location_id, vendor_id)
);
COMMENT ON TABLE supply.vendor_listings IS 'Supply-owned vendor_listings; external Account and Catalog identifiers have no cross-schema foreign keys.';

CREATE TABLE supply.vendor_listing_prices (
    price_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    listing_id UUID NOT NULL REFERENCES supply.vendor_listings(listing_id),
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    unit_price NUMERIC(19,6) NOT NULL CHECK (unit_price > 0),
    min_quantity NUMERIC(18,6) NOT NULL CHECK (min_quantity > 0),
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','CANCELLED')),
    CHECK (valid_to IS NULL OR valid_to > valid_from)
);
COMMENT ON TABLE supply.vendor_listing_prices IS 'Supply-owned vendor_listing_prices; external Account and Catalog identifiers have no cross-schema foreign keys.';

CREATE TABLE supply.price_tiers (
    tier_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    price_id UUID NOT NULL REFERENCES supply.vendor_listing_prices(price_id),
    min_quantity NUMERIC(18,6) NOT NULL CHECK (min_quantity > 0),
    unit_price NUMERIC(19,6) NOT NULL CHECK (unit_price > 0),
    UNIQUE(price_id, min_quantity)
);
COMMENT ON TABLE supply.price_tiers IS 'Supply-owned price_tiers; external Account and Catalog identifiers have no cross-schema foreign keys.';

-- Owner-scoped cursors and the actual public offer lookup.
CREATE INDEX idx_vendor_locations_vendor_id ON supply.vendor_locations(vendor_id, location_id);
CREATE INDEX idx_vendor_listings_vendor_id ON supply.vendor_listings(vendor_id, listing_id);
CREATE INDEX idx_vendor_listings_product_status_id ON supply.vendor_listings(product_id, status, listing_id);
CREATE INDEX idx_vendor_listings_location_vendor ON supply.vendor_listings(location_id, vendor_id);
CREATE INDEX idx_listing_prices_effective ON supply.vendor_listing_prices(listing_id, currency, valid_from, valid_to) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX uk_listing_prices_active_start ON supply.vendor_listing_prices(listing_id, currency, valid_from) WHERE status = 'ACTIVE';
-- Tier uniqueness supplies its parent/quantity index. Attributes are stored, not searched: no speculative GIN.
GRANT SELECT, INSERT, UPDATE ON supply.vendor_locations, supply.vendor_listings TO fresveg_supply;
GRANT SELECT, INSERT ON supply.vendor_listing_prices TO fresveg_supply;
GRANT UPDATE(status, updated_at, updated_by, version) ON supply.vendor_listing_prices TO fresveg_supply;
GRANT SELECT, INSERT ON supply.price_tiers TO fresveg_supply;
