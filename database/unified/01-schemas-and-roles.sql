-- =============================================================================
-- FresVeg Platform: Unified Schema & Role Initialization
-- Creates schemas and safe service roles for local/production environments
-- =============================================================================

-- 1. Create Core Service Schemas
CREATE SCHEMA IF NOT EXISTS account;
CREATE SCHEMA IF NOT EXISTS catalog;
CREATE SCHEMA IF NOT EXISTS supply;
CREATE SCHEMA IF NOT EXISTS commerce;
CREATE SCHEMA IF NOT EXISTS fulfillment;

-- 2. Create Service Roles (safely check if role exists before creating)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fresveg_account') THEN
        CREATE ROLE fresveg_account LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fresveg_account_migrator') THEN
        CREATE ROLE fresveg_account_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fresveg_catalog') THEN
        CREATE ROLE fresveg_catalog LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fresveg_catalog_migrator') THEN
        CREATE ROLE fresveg_catalog_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fresveg_supply') THEN
        CREATE ROLE fresveg_supply LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fresveg_supply_migrator') THEN
        CREATE ROLE fresveg_supply_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fresveg_commerce') THEN
        CREATE ROLE fresveg_commerce LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fresveg_commerce_migrator') THEN
        CREATE ROLE fresveg_commerce_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fresveg_fulfillment') THEN
        CREATE ROLE fresveg_fulfillment LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fresveg_fulfillment_migrator') THEN
        CREATE ROLE fresveg_fulfillment_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;
END $$;

-- 3. Grant schema permissions
DO $$
BEGIN
    GRANT USAGE ON SCHEMA account TO fresveg_account, fresveg_account_migrator;
    GRANT USAGE ON SCHEMA catalog TO fresveg_catalog, fresveg_catalog_migrator;
    GRANT USAGE ON SCHEMA supply TO fresveg_supply, fresveg_supply_migrator;
    GRANT USAGE ON SCHEMA commerce TO fresveg_commerce, fresveg_commerce_migrator;
    GRANT USAGE ON SCHEMA fulfillment TO fresveg_fulfillment, fresveg_fulfillment_migrator;
EXCEPTION
    WHEN OTHERS THEN NULL;
END $$;
