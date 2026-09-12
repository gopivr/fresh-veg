# Security architecture

FresVeg uses bearer tokens at the gateway and service boundaries. The gateway validates public requests, propagates the token and correlation ID, and blocks public access to internal routes. Domain services enforce their own audience, role and ownership checks because they remain the source of truth.

Database security follows least privilege: application roles are separate from migrator roles, no service has cross-schema privileges, and Liquibase history tables are protected from runtime credentials. PII is limited to Account profile/address data and Commerce/Fulfillment snapshots required for audit and delivery.
