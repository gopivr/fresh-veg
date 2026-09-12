-- Account domain only. No cross-service references or authentication credentials.

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
COMMENT ON TABLE account.users IS 'Account-owned users; retained identifiers and local relational ownership.';

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
COMMENT ON TABLE account.customer_profiles IS 'Account-owned customer profiles; retained identifiers and local relational ownership.';

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
COMMENT ON TABLE account.addresses IS 'Account-owned addresses; retained identifiers and local relational ownership.';

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
COMMENT ON TABLE account.vendors IS 'Account-owned vendors; retained identifiers and local relational ownership.';

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
COMMENT ON TABLE account.roles IS 'Account-owned roles; retained identifiers and local relational ownership.';

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
COMMENT ON TABLE account.vendor_users IS 'Account-owned vendor users; retained identifiers and local relational ownership.';

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
COMMENT ON TABLE account.user_roles IS 'Account-owned user roles; retained identifiers and local relational ownership.';

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
COMMENT ON TABLE account.user_preferences IS 'Account-owned user preferences; retained identifiers and local relational ownership.';

-- Keyset collection queries scope by owner and order by immutable UUID.
CREATE INDEX idx_addresses_customer_id_address_id ON account.addresses(customer_id, address_id);
CREATE INDEX idx_vendor_users_user_id_status_id ON account.vendor_users(user_id, status, vendor_user_id);

-- System role vocabulary, not demo users or grants to people.
INSERT INTO account.roles (role_id, code, description) VALUES
 ('10000000-0000-0000-0000-000000000001', 'CUSTOMER', 'Customer account'),
 ('10000000-0000-0000-0000-000000000002', 'VENDOR_ADMIN', 'Vendor organization administrator; membership is verified separately'),
 ('10000000-0000-0000-0000-000000000003', 'VENDOR_STAFF', 'Vendor organization staff; membership is verified separately'),
 ('10000000-0000-0000-0000-000000000004', 'PLATFORM_ADMIN', 'Platform administrator; no automatic assignment');

-- Permissions cover the implemented operations only. Administrative state changes
-- and membership management require operator credentials until their APIs exist.
GRANT SELECT, INSERT ON account.users, account.customer_profiles, account.user_preferences, account.user_roles TO fresveg_account;
GRANT SELECT, INSERT, UPDATE ON account.addresses TO fresveg_account;
GRANT SELECT ON account.roles, account.vendors, account.vendor_users TO fresveg_account;
