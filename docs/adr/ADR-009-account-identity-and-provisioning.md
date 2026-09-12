# ADR-009: Account identity and first-use provisioning

Date: 2026-09-08

Status: Accepted and implemented in Phase 3.

## Context

Phase 3 needs customer ownership and vendor identity before gateway routing and management workflows exist. The external identity provider owns authentication; client claims must not establish local ownership or privileged roles.

## Decision

Validate externally signed JWTs in Account and expose issuer/subject through an application principal interface. Map their exact pair to a unique local UUID. First successful use atomically provisions the user, one customer profile, default preferences and CUSTOMER role. Concurrent requests use a unique constraint and insert-on-conflict without overwriting existing profiles. Every user can be a customer, including vendor staff. Display name and verified email are optional initial snapshots, never identity keys.

Load roles and vendor memberships from Account data. Use a many-to-many user/role relation and a separate many-to-many vendor/user relation with organization-scoped staff roles and status. Reject inactive accounts/profiles and derive address ownership from the authenticated identity. Address replacement requires an optimistic version. Do not add management APIs or bootstrap administrators without a defined authorized workflow; controlled Account administration provisions vendors and elevated memberships meanwhile.

## Consequences

Account can serve authorized direct requests independently of the gateway. A valid new identity causes local database writes on its first successful operation, including GET; this is deliberate first-use provisioning. A failed operation rolls back that provisioning. Issuer migration/account linking and profile synchronization need explicit future decisions. Vendor membership is authoritative only through Account's stored relationship, never a token or global vendor role alone.

Provider deployment, privileged administration APIs, authenticated service-to-service lookups and gateway routes remain outside Phase 3. See the [Account guide](../account/account-service.md) for configuration, operational provisioning, API semantics and validation, and [ADR-005](ADR-005-authentication.md) for the overall security baseline.
