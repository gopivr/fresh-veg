-- =============================================================================
-- FresVeg Platform: Unified Microservice Tables (Exact Hibernate Entity Match)
-- =============================================================================

-- Re-create schemas cleanly to ensure exact entity field alignment
DROP SCHEMA IF EXISTS account CASCADE;
DROP SCHEMA IF EXISTS catalog CASCADE;
DROP SCHEMA IF EXISTS supply CASCADE;
DROP SCHEMA IF EXISTS commerce CASCADE;
DROP SCHEMA IF EXISTS fulfillment CASCADE;

CREATE SCHEMA account;
CREATE SCHEMA catalog;
CREATE SCHEMA supply;
CREATE SCHEMA commerce;
CREATE SCHEMA fulfillment;

-- =============================================================================
-- 1. ACCOUNT DOMAIN (Schema: account)
-- =============================================================================

CREATE TABLE account.users (
    user_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_users_version CHECK (version >= 0),
    CONSTRAINT fk_users_created_by FOREIGN KEY (created_by) REFERENCES account.users(user_id),
    CONSTRAINT fk_users_updated_by FOREIGN KEY (updated_by) REFERENCES account.users(user_id),
    oidc_issuer VARCHAR(512) NOT NULL,
    oidc_subject VARCHAR(255) NOT NULL,
    display_name VARCHAR(160),
    email VARCHAR(320),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uk_users_oidc_identity UNIQUE (oidc_issuer, oidc_subject),
    CONSTRAINT ck_users_oidc_identity CHECK (length(btrim(oidc_issuer)) > 0 AND length(btrim(oidc_subject)) > 0),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'))
);

CREATE TABLE account.customer_profiles (
    customer_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_customer_profiles_version CHECK (version >= 0),
    CONSTRAINT fk_customer_profiles_created_by FOREIGN KEY (created_by) REFERENCES account.users(user_id),
    CONSTRAINT fk_customer_profiles_updated_by FOREIGN KEY (updated_by) REFERENCES account.users(user_id),
    user_id UUID NOT NULL,
    full_name VARCHAR(160),
    contact_phone VARCHAR(32),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uk_customer_profiles_user_id UNIQUE (user_id),
    CONSTRAINT fk_customer_profiles_users FOREIGN KEY (user_id) REFERENCES account.users(user_id),
    CONSTRAINT ck_customer_profiles_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'))
);

CREATE TABLE account.addresses (
    address_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_addresses_version CHECK (version >= 0),
    CONSTRAINT fk_addresses_created_by FOREIGN KEY (created_by) REFERENCES account.users(user_id),
    CONSTRAINT fk_addresses_updated_by FOREIGN KEY (updated_by) REFERENCES account.users(user_id),
    customer_id UUID NOT NULL,
    label VARCHAR(50),
    recipient_name VARCHAR(120) NOT NULL,
    line1 VARCHAR(200) NOT NULL,
    line2 VARCHAR(200),
    city VARCHAR(100) NOT NULL,
    region VARCHAR(100),
    postal_code VARCHAR(20) NOT NULL,
    country_code VARCHAR(2) NOT NULL,
    phone VARCHAR(32),
    CONSTRAINT fk_addresses_customer_profiles FOREIGN KEY (customer_id) REFERENCES account.customer_profiles(customer_id),
    CONSTRAINT ck_addresses_required_text CHECK (length(btrim(recipient_name)) > 0 AND length(btrim(line1)) > 0 AND length(btrim(city)) > 0 AND length(btrim(postal_code)) > 0),
    CONSTRAINT ck_addresses_country_code CHECK (country_code ~ '^[A-Z]{2}$')
);

CREATE TABLE account.vendors (
    vendor_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_vendors_version CHECK (version >= 0),
    CONSTRAINT fk_vendors_created_by FOREIGN KEY (created_by) REFERENCES account.users(user_id),
    CONSTRAINT fk_vendors_updated_by FOREIGN KEY (updated_by) REFERENCES account.users(user_id),
    vendor_code VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uk_vendors_vendor_code UNIQUE (vendor_code),
    CONSTRAINT ck_vendors_required_text CHECK (length(btrim(vendor_code)) > 0 AND length(btrim(name)) > 0),
    CONSTRAINT ck_vendors_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'))
);

CREATE TABLE account.roles (
    role_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_roles_version CHECK (version >= 0),
    CONSTRAINT fk_roles_created_by FOREIGN KEY (created_by) REFERENCES account.users(user_id),
    CONSTRAINT fk_roles_updated_by FOREIGN KEY (updated_by) REFERENCES account.users(user_id),
    code VARCHAR(40) NOT NULL,
    description VARCHAR(200) NOT NULL,
    CONSTRAINT uk_roles_code UNIQUE (code),
    CONSTRAINT ck_roles_code CHECK (code IN ('CUSTOMER', 'VENDOR_ADMIN', 'VENDOR_STAFF', 'PLATFORM_ADMIN'))
);

CREATE TABLE account.vendor_users (
    vendor_user_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_vendor_users_version CHECK (version >= 0),
    CONSTRAINT fk_vendor_users_created_by FOREIGN KEY (created_by) REFERENCES account.users(user_id),
    CONSTRAINT fk_vendor_users_updated_by FOREIGN KEY (updated_by) REFERENCES account.users(user_id),
    vendor_id UUID NOT NULL,
    user_id UUID NOT NULL,
    membership_role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uk_vendor_users_vendor_user UNIQUE (vendor_id, user_id),
    CONSTRAINT fk_vendor_users_vendors FOREIGN KEY (vendor_id) REFERENCES account.vendors(vendor_id),
    CONSTRAINT fk_vendor_users_users FOREIGN KEY (user_id) REFERENCES account.users(user_id),
    CONSTRAINT ck_vendor_users_role CHECK (membership_role IN ('VENDOR_ADMIN', 'VENDOR_STAFF')),
    CONSTRAINT ck_vendor_users_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE account.user_roles (
    user_role_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_user_roles_version CHECK (version >= 0),
    CONSTRAINT fk_user_roles_created_by FOREIGN KEY (created_by) REFERENCES account.users(user_id),
    CONSTRAINT fk_user_roles_updated_by FOREIGN KEY (updated_by) REFERENCES account.users(user_id),
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    CONSTRAINT uk_user_roles_user_role UNIQUE (user_id, role_id),
    CONSTRAINT fk_user_roles_users FOREIGN KEY (user_id) REFERENCES account.users(user_id),
    CONSTRAINT fk_user_roles_roles FOREIGN KEY (role_id) REFERENCES account.roles(role_id)
);

CREATE TABLE account.user_preferences (
    preference_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_user_preferences_version CHECK (version >= 0),
    CONSTRAINT fk_user_preferences_created_by FOREIGN KEY (created_by) REFERENCES account.users(user_id),
    CONSTRAINT fk_user_preferences_updated_by FOREIGN KEY (updated_by) REFERENCES account.users(user_id),
    user_id UUID NOT NULL,
    locale VARCHAR(35),
    time_zone VARCHAR(64),
    marketing_opt_in BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_user_preferences_user_id UNIQUE (user_id),
    CONSTRAINT fk_user_preferences_users FOREIGN KEY (user_id) REFERENCES account.users(user_id)
);

CREATE INDEX idx_addresses_customer_id_address_id ON account.addresses(customer_id, address_id);
CREATE INDEX idx_vendor_users_user_id_status_id ON account.vendor_users(user_id, status, vendor_user_id);

INSERT INTO account.roles (role_id, code, description) VALUES
 ('10000000-0000-0000-0000-000000000001', 'CUSTOMER', 'Customer account'),
 ('10000000-0000-0000-0000-000000000002', 'VENDOR_ADMIN', 'Vendor organization administrator'),
 ('10000000-0000-0000-0000-000000000003', 'VENDOR_STAFF', 'Vendor organization staff'),
 ('10000000-0000-0000-0000-000000000004', 'PLATFORM_ADMIN', 'Platform administrator')
ON CONFLICT (role_id) DO NOTHING;

-- =============================================================================
-- 2. CATALOG DOMAIN (Schema: catalog)
-- =============================================================================

CREATE TABLE catalog.units_of_measure (
    unit_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    code VARCHAR(16) NOT NULL UNIQUE CHECK (code ~ '^[A-Z][A-Z0-9_]{0,15}$'),
    name VARCHAR(80) NOT NULL CHECK (length(btrim(name)) > 0),
    dimension VARCHAR(16) NOT NULL CHECK (dimension IN ('MASS','VOLUME','COUNT'))
);

CREATE TABLE catalog.categories (
    category_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    code VARCHAR(64) NOT NULL UNIQUE CHECK (code ~ '^[A-Z0-9][A-Z0-9_-]{0,63}$'),
    name VARCHAR(160) NOT NULL CHECK (length(btrim(name)) > 0),
    parent_category_id UUID REFERENCES catalog.categories(category_id),
    CHECK (parent_category_id IS NULL OR parent_category_id <> category_id)
);

CREATE TABLE catalog.products (
    product_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    code VARCHAR(64) NOT NULL UNIQUE CHECK (code ~ '^[A-Z0-9][A-Z0-9_-]{0,63}$'),
    name VARCHAR(160) NOT NULL CHECK (length(btrim(name)) > 0),
    description VARCHAR(4000),
    organic BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED')),
    attributes JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(attributes) = 'object')
);

CREATE TABLE catalog.product_variants (
    variant_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    product_id UUID NOT NULL REFERENCES catalog.products(product_id),
    code VARCHAR(64) NOT NULL UNIQUE CHECK (code ~ '^[A-Z0-9][A-Z0-9_-]{0,63}$'),
    name VARCHAR(160) NOT NULL CHECK (length(btrim(name)) > 0),
    unit_id UUID NOT NULL REFERENCES catalog.units_of_measure(unit_id),
    quantity NUMERIC(18,6) NOT NULL CHECK (quantity > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','ARCHIVED'))
);

CREATE TABLE catalog.product_categories (
    product_category_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    product_id UUID NOT NULL REFERENCES catalog.products(product_id),
    category_id UUID NOT NULL REFERENCES catalog.categories(category_id),
    UNIQUE(product_id, category_id)
);

CREATE TABLE catalog.product_images (
    image_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    product_id UUID NOT NULL REFERENCES catalog.products(product_id),
    url VARCHAR(2048) NOT NULL CHECK (url ~ '^https://[^[:space:]]+$'),
    alt_text VARCHAR(240) NOT NULL,
    position INTEGER NOT NULL CHECK (position >= 0),
    UNIQUE(product_id, position)
);

CREATE INDEX idx_products_status_name_id ON catalog.products(status, name, product_id);
CREATE INDEX idx_products_status_code_id ON catalog.products(status, code, product_id);
CREATE INDEX idx_products_name_search ON catalog.products USING GIN (to_tsvector('simple', name));
CREATE INDEX idx_products_attributes ON catalog.products USING GIN (attributes jsonb_path_ops);
CREATE INDEX idx_categories_parent ON catalog.categories(parent_category_id, category_id);
CREATE INDEX idx_product_categories_category_product ON catalog.product_categories(category_id, product_id);
CREATE INDEX idx_product_variants_product ON catalog.product_variants(product_id, code);

INSERT INTO catalog.units_of_measure(unit_id,code,name,dimension) VALUES
 ('30000000-0000-0000-0000-000000000001','KG','Kilogram','MASS'),
 ('30000000-0000-0000-0000-000000000002','G','Gram','MASS'),
 ('30000000-0000-0000-0000-000000000003','EA','Each','COUNT'),
 ('30000000-0000-0000-0000-000000000004','L','Litre','VOLUME'),
 ('30000000-0000-0000-0000-000000000005','ML','Millilitre','VOLUME')
ON CONFLICT (unit_id) DO NOTHING;

-- Seed Sample Categories and Products for testing
INSERT INTO catalog.categories (category_id, code, name) VALUES
 ('10000000-0000-0000-0000-000000000001', 'LEAFY-GREENS', 'Leafy Greens'),
 ('10000000-0000-0000-0000-000000000002', 'ROOT-VEGETABLES', 'Root Vegetables'),
 ('10000000-0000-0000-0000-000000000003', 'FRUIT-VEGETABLES', 'Fruit & Vegetables'),
 ('10000000-0000-0000-0000-000000000004', 'ORGANIC-HERBS', 'Organic Herbs')
ON CONFLICT (category_id) DO NOTHING;

INSERT INTO catalog.products (product_id, code, name, description, organic, status, attributes) VALUES
 ('20000000-0000-0000-0000-000000000001', 'FARM-SPINACH-01', 'Fresh Farm Spinach (Palak)', 'Organically grown, pesticide-free fresh farm spinach leaves.', true, 'ACTIVE', '{"origin": "Local Farm", "diet": "Organic"}'::jsonb),
 ('20000000-0000-0000-0000-000000000002', 'HYDROPONIC-TOMATO-01', 'Hydroponic Cherry Tomatoes', 'Crisp, sweet vine-ripened red cherry tomatoes.', true, 'ACTIVE', '{"origin": "Hydroponic Greenhouse", "diet": "Organic"}'::jsonb),
 ('20000000-0000-0000-0000-000000000003', 'ORANGE-CARROTS-01', 'Ooty Orange Carrots', 'Sweet, crunchy farm fresh orange carrots rich in vitamin A.', false, 'ACTIVE', '{"origin": "Ooty Valley"}'::jsonb)
ON CONFLICT (product_id) DO NOTHING;

INSERT INTO catalog.product_variants (variant_id, product_id, code, name, unit_id, quantity, status) VALUES
 ('21000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 'SPINACH-250G', 'Spinach Bunch (250g)', '30000000-0000-0000-0000-000000000002', 250, 'ACTIVE'),
 ('21000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000002', 'TOMATO-500G', 'Cherry Tomatoes (500g)', '30000000-0000-0000-0000-000000000002', 500, 'ACTIVE'),
 ('21000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000003', 'CARROTS-1KG', 'Orange Carrots (1kg)', '30000000-0000-0000-0000-000000000001', 1, 'ACTIVE')
ON CONFLICT (variant_id) DO NOTHING;

INSERT INTO catalog.product_categories (product_category_id, product_id, category_id) VALUES
 ('22000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001'),
 ('22000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000003'),
 ('22000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000002')
ON CONFLICT (product_category_id) DO NOTHING;

INSERT INTO catalog.product_images (image_id, product_id, url, alt_text, position) VALUES
 ('23000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 'https://images.unsplash.com/photo-1576045057995-568f588f82fb', 'Fresh Organic Spinach', 0),
 ('23000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000002', 'https://images.unsplash.com/photo-1592924357228-91a4daadcfea', 'Cherry Tomatoes on Vine', 0),
 ('23000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000003', 'https://images.unsplash.com/photo-1598170845058-32b9d6a5da37', 'Fresh Orange Carrots', 0)
ON CONFLICT (image_id) DO NOTHING;

-- =============================================================================
-- 3. SUPPLY DOMAIN (Schema: supply)
-- =============================================================================

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

CREATE TABLE supply.inventory (
    inventory_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
    listing_id UUID NOT NULL UNIQUE REFERENCES supply.vendor_listings(listing_id),
    quantity_on_hand NUMERIC(18,6) NOT NULL DEFAULT 0 CHECK(quantity_on_hand>=0),
    reserved_quantity NUMERIC(18,6) NOT NULL DEFAULT 0 CHECK(reserved_quantity>=0 AND reserved_quantity<=quantity_on_hand)
);

CREATE TABLE supply.inventory_batches (
    batch_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
    inventory_id UUID NOT NULL REFERENCES supply.inventory(inventory_id),
    batch_number VARCHAR(64) NOT NULL CHECK(length(btrim(batch_number))>0),
    harvest_date DATE,
    received_date DATE NOT NULL,
    best_before_date DATE,
    expiry_date DATE,
    origin VARCHAR(160) NOT NULL,
    grade VARCHAR(64) NOT NULL,
    certification_data JSONB NOT NULL CHECK(jsonb_typeof(certification_data)='object'),
    quantity_received NUMERIC(18,6) NOT NULL CHECK(quantity_received>0),
    quantity_remaining NUMERIC(18,6) NOT NULL CHECK(quantity_remaining>=0 AND quantity_remaining<=quantity_received),
    reserved_quantity NUMERIC(18,6) NOT NULL DEFAULT 0 CHECK(reserved_quantity>=0 AND reserved_quantity<=quantity_remaining),
    status VARCHAR(16) NOT NULL CHECK(status IN ('ACTIVE','DEPLETED')),
    CHECK(harvest_date IS NULL OR harvest_date<=received_date),
    CHECK(best_before_date IS NULL OR best_before_date>=received_date),
    CHECK(expiry_date IS NULL OR expiry_date>=received_date),
    CHECK(best_before_date IS NULL OR expiry_date IS NULL OR best_before_date<=expiry_date),
    UNIQUE(inventory_id,batch_number)
);

CREATE TABLE supply.inventory_reservations (
    reservation_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
    inventory_id UUID NOT NULL REFERENCES supply.inventory(inventory_id),
    client_id VARCHAR(160) NOT NULL,
    external_reference VARCHAR(160) NOT NULL CHECK(length(btrim(external_reference))>0),
    quantity NUMERIC(18,6) NOT NULL CHECK(quantity>0),
    status VARCHAR(16) NOT NULL CHECK(status IN ('ACTIVE','COMMITTED','RELEASED','EXPIRED')),
    expires_at TIMESTAMPTZ NOT NULL,
    allocations JSONB NOT NULL CHECK(jsonb_typeof(allocations)='object'),
    UNIQUE(client_id,external_reference)
);

CREATE TABLE supply.inventory_transactions (
    transaction_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
    inventory_id UUID NOT NULL REFERENCES supply.inventory(inventory_id),
    batch_id UUID NOT NULL REFERENCES supply.inventory_batches(batch_id),
    reservation_id UUID REFERENCES supply.inventory_reservations(reservation_id),
    kind VARCHAR(16) NOT NULL CHECK(kind IN ('RECEIPT','ADJUSTMENT','RESERVE','COMMIT','RELEASE','EXPIRE')),
    quantity_delta NUMERIC(18,6) NOT NULL,
    reserved_delta NUMERIC(18,6) NOT NULL,
    reason VARCHAR(240) NOT NULL CHECK(length(btrim(reason))>0),
    CHECK(quantity_delta<>0 OR reserved_delta<>0)
);

CREATE INDEX idx_vendor_locations_vendor_id ON supply.vendor_locations(vendor_id, location_id);
CREATE INDEX idx_vendor_listings_vendor_id ON supply.vendor_listings(vendor_id, listing_id);
CREATE INDEX idx_vendor_listings_product_status_id ON supply.vendor_listings(product_id, status, listing_id);
CREATE INDEX idx_vendor_listings_location_vendor ON supply.vendor_listings(location_id, vendor_id);
CREATE INDEX idx_listing_prices_effective ON supply.vendor_listing_prices(listing_id, currency, valid_from, valid_to) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX uk_listing_prices_active_start ON supply.vendor_listing_prices(listing_id, currency, valid_from) WHERE status = 'ACTIVE';
CREATE INDEX idx_batches_fefo ON supply.inventory_batches(inventory_id,expiry_date,batch_id);
CREATE INDEX idx_reservations_expiration ON supply.inventory_reservations(expires_at,inventory_id) WHERE status='ACTIVE';
CREATE INDEX idx_reservations_inventory ON supply.inventory_reservations(inventory_id);
CREATE INDEX idx_transactions_inventory ON supply.inventory_transactions(inventory_id,transaction_id);

-- =============================================================================
-- 4. COMMERCE DOMAIN (Schema: commerce)
-- =============================================================================

CREATE TABLE commerce.carts (
    cart_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
    customer_id UUID NOT NULL,
    currency VARCHAR(3) NOT NULL CHECK(currency ~ '^[A-Z]{3}$')
);

CREATE TABLE commerce.cart_items (
    item_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
    cart_id UUID NOT NULL REFERENCES commerce.carts(cart_id),
    listing_id UUID NOT NULL,
    quantity NUMERIC(18,6) NOT NULL CHECK(quantity>0),
    UNIQUE(cart_id,listing_id)
);

CREATE TABLE commerce.orders (
    order_id UUID PRIMARY KEY,
    order_number VARCHAR(40) NOT NULL UNIQUE,
    customer_id UUID NOT NULL,
    cart_id UUID NOT NULL REFERENCES commerce.carts(cart_id),
    cart_version BIGINT NOT NULL CHECK(cart_version>=0),
    status VARCHAR(24) NOT NULL CHECK(status IN ('PENDING_PAYMENT','PAYMENT_AUTHORIZED','CONFIRMED','PAYMENT_FAILED','CANCEL_PENDING','CANCELLED')),
    currency VARCHAR(3) NOT NULL CHECK(currency ~ '^[A-Z]{3}$'),
    subtotal NUMERIC(38,6) NOT NULL CHECK(subtotal>=0),
    discount_amount NUMERIC(38,6) NOT NULL CHECK(discount_amount>=0 AND discount_amount<=subtotal),
    tax_amount NUMERIC(38,6) NOT NULL CHECK(tax_amount>=0),
    delivery_fee NUMERIC(38,6) NOT NULL CHECK(delivery_fee>=0),
    grand_total NUMERIC(38,6) NOT NULL CHECK(grand_total=subtotal-discount_amount+tax_amount+delivery_fee),
    shipping_address_snapshot JSONB NOT NULL CHECK(jsonb_typeof(shipping_address_snapshot)='object'),
    delivery_slot_id UUID NOT NULL,
    delivery_slot_snapshot JSONB NOT NULL,
    pricing_policy_id VARCHAR(100) NOT NULL,
    payment_method_id UUID,
    reservation_expires_at TIMESTAMPTZ NOT NULL,
    response_snapshot JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
    UNIQUE(cart_id,cart_version)
);

CREATE TABLE commerce.order_items (
    order_item_id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES commerce.orders(order_id),
    product_id UUID NOT NULL,
    listing_id UUID NOT NULL,
    vendor_id UUID NOT NULL,
    product_name VARCHAR(240) NOT NULL,
    vendor_name VARCHAR(160) NOT NULL,
    vendor_sku VARCHAR(64) NOT NULL,
    unit_code VARCHAR(32) NOT NULL,
    quantity NUMERIC(18,6) NOT NULL CHECK(quantity>0),
    unit_price NUMERIC(19,6) NOT NULL CHECK(unit_price>0),
    discount_amount NUMERIC(38,6) NOT NULL CHECK(discount_amount>=0),
    tax_amount NUMERIC(38,6) NOT NULL CHECK(tax_amount>=0),
    line_total NUMERIC(38,6) NOT NULL CHECK(line_total>=0),
    vendor_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK(vendor_status IN ('PENDING','ACCEPTED','REJECTED')),
    product_snapshot JSONB NOT NULL,
    reservation_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE(order_id,listing_id)
);

CREATE TABLE commerce.order_status_history (
    history_id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES commerce.orders(order_id),
    status VARCHAR(24) NOT NULL,
    reason VARCHAR(240) NOT NULL,
    actor_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE commerce.idempotency_records (
    record_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL CHECK(status IN ('PREPARED','SUCCEEDED','FAILED','COMPENSATED')),
    plan JSONB NOT NULL,
    response JSONB,
    order_id UUID REFERENCES commerce.orders(order_id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE(customer_id,idempotency_key)
);

CREATE TABLE commerce.outbox_events (
    event_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL REFERENCES commerce.orders(order_id),
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','PUBLISHED')),
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    retry_count INTEGER NOT NULL DEFAULT 0 CHECK(retry_count>=0)
);

CREATE TABLE commerce.payment_attempts (
    payment_attempt_id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES commerce.orders(order_id),
    idempotency_key VARCHAR(160) NOT NULL,
    payment_method_id UUID NOT NULL,
    provider VARCHAR(40) NOT NULL,
    provider_reference VARCHAR(120),
    status VARCHAR(24) NOT NULL CHECK(status IN ('AUTHORIZED','FAILED')),
    currency VARCHAR(3) NOT NULL CHECK(currency ~ '^[A-Z]{3}$'),
    amount NUMERIC(38,6) NOT NULL CHECK(amount>=0),
    failure_code VARCHAR(80),
    failure_message VARCHAR(240),
    requested_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    UNIQUE(order_id,idempotency_key)
);

CREATE TABLE commerce.refunds (
    refund_id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES commerce.orders(order_id),
    payment_attempt_id UUID NOT NULL REFERENCES commerce.payment_attempts(payment_attempt_id),
    idempotency_key VARCHAR(160) NOT NULL,
    provider VARCHAR(40) NOT NULL,
    provider_reference VARCHAR(120),
    status VARCHAR(24) NOT NULL CHECK(status IN ('COMPLETED','FAILED')),
    currency VARCHAR(3) NOT NULL CHECK(currency ~ '^[A-Z]{3}$'),
    amount NUMERIC(38,6) NOT NULL CHECK(amount>=0),
    failure_code VARCHAR(80),
    failure_message VARCHAR(240),
    requested_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    UNIQUE(order_id,idempotency_key)
);

CREATE INDEX idx_carts_customer_id ON commerce.carts(customer_id,cart_id);
CREATE INDEX idx_cart_items_cart_id ON commerce.cart_items(cart_id,item_id);
CREATE INDEX idx_orders_customer ON commerce.orders(customer_id,order_id);
CREATE INDEX idx_orders_expiry ON commerce.orders(status,reservation_expires_at);
CREATE INDEX idx_order_items_vendor ON commerce.order_items(vendor_id,order_id);
CREATE INDEX idx_order_status_history_order ON commerce.order_status_history(order_id,created_at);
CREATE INDEX idx_idempotency_recovery ON commerce.idempotency_records(status,updated_at);
CREATE INDEX idx_outbox_pending ON commerce.outbox_events(status,created_at,event_id);
CREATE INDEX idx_payment_attempts_order ON commerce.payment_attempts(order_id,completed_at);
CREATE INDEX idx_refunds_order ON commerce.refunds(order_id,completed_at);

-- =============================================================================
-- 5. FULFILLMENT DOMAIN (Schema: fulfillment)
-- =============================================================================

CREATE TABLE fulfillment.delivery_slots (
    slot_id UUID PRIMARY KEY,
    service_area VARCHAR(80) NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    capacity INTEGER NOT NULL,
    reserved_capacity INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_delivery_slots_window CHECK(end_time>start_time),
    CONSTRAINT ck_delivery_slots_capacity CHECK(capacity>=0 AND reserved_capacity>=0 AND reserved_capacity<=capacity),
    CONSTRAINT ck_delivery_slots_status CHECK(status IN ('OPEN','CLOSED','CANCELLED'))
);

CREATE TABLE fulfillment.fulfillments (
    fulfillment_id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    delivery_slot_id UUID NOT NULL REFERENCES fulfillment.delivery_slots(slot_id),
    status VARCHAR(24) NOT NULL,
    service_area VARCHAR(80) NOT NULL,
    delivery_address JSONB NOT NULL,
    requested_start TIMESTAMPTZ NOT NULL,
    requested_end TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_fulfillments_order UNIQUE(order_id),
    CONSTRAINT ck_fulfillments_status CHECK(status IN ('CREATED','PACKING','READY_FOR_DELIVERY','OUT_FOR_DELIVERY','DELIVERED','CANCELLED'))
);

CREATE TABLE fulfillment.fulfillment_items (
    fulfillment_item_id UUID PRIMARY KEY,
    fulfillment_id UUID NOT NULL REFERENCES fulfillment.fulfillments(fulfillment_id),
    order_item_id UUID NOT NULL,
    product_id UUID NOT NULL,
    listing_id UUID NOT NULL,
    vendor_id UUID NOT NULL,
    product_name VARCHAR(240) NOT NULL,
    quantity NUMERIC(18,6) NOT NULL,
    unit_code VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'CREATED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_fulfillment_items_order_item UNIQUE(order_item_id),
    CONSTRAINT ck_fulfillment_items_quantity CHECK(quantity>0),
    CONSTRAINT ck_fulfillment_items_status CHECK(status IN ('CREATED','PACKED','SUBSTITUTED','CANCELLED'))
);

CREATE TABLE fulfillment.shipments (
    shipment_id UUID PRIMARY KEY,
    fulfillment_id UUID NOT NULL REFERENCES fulfillment.fulfillments(fulfillment_id),
    carrier VARCHAR(80),
    tracking_reference VARCHAR(120),
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_shipments_status CHECK(status IN ('PENDING','ASSIGNED','OUT_FOR_DELIVERY','DELIVERED','FAILED','CANCELLED'))
);

CREATE TABLE fulfillment.shipment_events (
    event_id UUID PRIMARY KEY,
    shipment_id UUID NOT NULL REFERENCES fulfillment.shipments(shipment_id),
    status VARCHAR(24) NOT NULL,
    description VARCHAR(240) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE fulfillment.delivery_assignments (
    assignment_id UUID PRIMARY KEY,
    shipment_id UUID NOT NULL REFERENCES fulfillment.shipments(shipment_id),
    assignee_id UUID,
    vehicle_reference VARCHAR(80),
    status VARCHAR(24) NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT ck_delivery_assignments_status CHECK(status IN ('ASSIGNED','COMPLETED','CANCELLED'))
);

CREATE INDEX idx_delivery_slots_area_time ON fulfillment.delivery_slots(service_area,start_time,slot_id);
CREATE INDEX idx_fulfillments_customer ON fulfillment.fulfillments(customer_id,fulfillment_id);
CREATE INDEX idx_fulfillment_items_fulfillment ON fulfillment.fulfillment_items(fulfillment_id);
CREATE INDEX idx_shipments_fulfillment ON fulfillment.shipments(fulfillment_id);
CREATE INDEX idx_shipment_events_shipment_time ON fulfillment.shipment_events(shipment_id,occurred_at,event_id);
CREATE INDEX idx_delivery_assignments_shipment ON fulfillment.delivery_assignments(shipment_id);

-- Safe grants
DO $$
BEGIN
    GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA account TO postgres;
    GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA catalog TO postgres;
    GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA supply TO postgres;
    GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA commerce TO postgres;
    GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA fulfillment TO postgres;
    
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA account TO fresveg_account;
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA catalog TO fresveg_catalog;
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA supply TO fresveg_supply;
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA commerce TO fresveg_commerce;
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA fulfillment TO fresveg_fulfillment;
EXCEPTION
    WHEN OTHERS THEN NULL;
END $$;
