-- Rollback is permitted only before any account business data exists.
LOCK TABLE account.users, account.customer_profiles, account.addresses, account.vendors,
    account.vendor_users, account.roles, account.user_roles, account.user_preferences
    IN ACCESS EXCLUSIVE MODE;
DO $rollback$
BEGIN
    IF EXISTS (SELECT 1 FROM account.users) OR
                   EXISTS (SELECT 1 FROM account.customer_profiles) OR
                   EXISTS (SELECT 1 FROM account.addresses) OR
                   EXISTS (SELECT 1 FROM account.vendors) OR
                   EXISTS (SELECT 1 FROM account.vendor_users) OR
                   EXISTS (SELECT 1 FROM account.user_roles) OR
                   EXISTS (SELECT 1 FROM account.user_preferences)
       OR (SELECT count(*) FROM account.roles) <> 4 THEN
        RAISE EXCEPTION 'Account rollback refused: business data exists' USING ERRCODE = '55000';
    END IF;
END
$rollback$;
DROP TABLE account.vendor_users;
DROP TABLE account.user_roles;
DROP TABLE account.user_preferences;
DROP TABLE account.addresses;
DROP TABLE account.customer_profiles;
DROP TABLE account.vendors;
DROP TABLE account.roles;
DROP TABLE account.users;
