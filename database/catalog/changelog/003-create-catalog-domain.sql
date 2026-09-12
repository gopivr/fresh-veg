-- Reusable master catalog only: no vendor offers, prices or inventory.

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
COMMENT ON TABLE catalog.units_of_measure IS 'Catalog-owned units_of_measure; audit actor UUIDs refer to Account through APIs, without cross-schema foreign keys.';

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
COMMENT ON TABLE catalog.categories IS 'Catalog-owned categories; audit actor UUIDs refer to Account through APIs, without cross-schema foreign keys.';

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
COMMENT ON TABLE catalog.products IS 'Catalog-owned products; audit actor UUIDs refer to Account through APIs, without cross-schema foreign keys.';

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
COMMENT ON TABLE catalog.product_variants IS 'Catalog-owned product_variants; audit actor UUIDs refer to Account through APIs, without cross-schema foreign keys.';

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
COMMENT ON TABLE catalog.product_categories IS 'Catalog-owned product_categories; audit actor UUIDs refer to Account through APIs, without cross-schema foreign keys.';

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
COMMENT ON TABLE catalog.product_images IS 'Catalog-owned product_images; audit actor UUIDs refer to Account through APIs, without cross-schema foreign keys.';

-- Product code uniqueness supplies the product-code lookup index.
-- Exact supported sort and relation access paths; no speculative inventory indexes.
CREATE INDEX idx_products_status_name_id ON catalog.products(status, name, product_id);
CREATE INDEX idx_products_status_code_id ON catalog.products(status, code, product_id);
CREATE INDEX idx_products_name_search ON catalog.products USING GIN (to_tsvector('simple', name));
-- The public attributes containment filter uses @>, hence jsonb_path_ops.
CREATE INDEX idx_products_attributes ON catalog.products USING GIN (attributes jsonb_path_ops);
CREATE INDEX idx_categories_parent ON catalog.categories(parent_category_id, category_id);
CREATE INDEX idx_product_categories_category_product ON catalog.product_categories(category_id, product_id);
CREATE INDEX idx_product_variants_product ON catalog.product_variants(product_id, code);

-- Stable unit vocabulary only; no sample products or categories.
INSERT INTO catalog.units_of_measure(unit_id,code,name,dimension) VALUES
 ('30000000-0000-0000-0000-000000000001','KG','Kilogram','MASS'),
 ('30000000-0000-0000-0000-000000000002','G','Gram','MASS'),
 ('30000000-0000-0000-0000-000000000003','EA','Each','COUNT'),
 ('30000000-0000-0000-0000-000000000004','L','Litre','VOLUME'),
 ('30000000-0000-0000-0000-000000000005','ML','Millilitre','VOLUME');
GRANT SELECT ON catalog.units_of_measure TO fresveg_catalog;
GRANT SELECT, INSERT ON catalog.categories TO fresveg_catalog;
GRANT SELECT, INSERT, UPDATE ON catalog.products, catalog.product_variants TO fresveg_catalog;
GRANT SELECT, INSERT, DELETE ON catalog.product_categories, catalog.product_images TO fresveg_catalog;
