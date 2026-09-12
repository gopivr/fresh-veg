# Account service — Phase 3

Account implements local identity, customer profiles, owned delivery addresses, roles/preferences and vendor staff memberships. Its five public operations are available directly on port 8081 and through the Phase 13 gateway `/api/v1/accounts/**` route.

## Authentication and provisioning

Export `OIDC_ISSUER_URI`, `OIDC_AUDIENCE` and `OIDC_JWK_SET_URI`, along with the database settings in the [migration guide](../database/migration-guide.md). No identity provider, signing key or token is bundled with the application. Use HTTPS issuer/JWK URLs in deployed environments; HTTP is supported for isolated local providers. The JWK endpoint has five-second connection/read timeouts. It is fetched when tokens need verification, without issuer discovery at startup; database health alone does not establish IdP availability.

Spring Security Resource Server verifies RS256 JWT signatures against the configured JWK set, the exact issuer, intended audience, expiration and any not-before claim. Spring's default timestamp tolerance is 60 seconds. Tokens must contain a nonblank subject (up to 255 characters) and expiration. This phase supports the decoder's default `JWT` type handling; provider-specific token formats/algorithms require an explicit subsequent compatibility decision. Use an access token issued for this API, not an ID token issued for a frontend client.

`AccountPrincipalProvider` separates application identity from JWT transport. The `(issuer, subject)` pair identifies a local UUID user; neither email nor a token-supplied customer/vendor UUID is an ownership key. On first successful use, one transaction creates the user, customer profile, default preferences and CUSTOMER role. A database unique constraint and conflict-safe insert serialize concurrent first requests. Every authenticated user can therefore act as a customer; vendor staff retain their customer profile.

The initial display name and explicitly verified email are optional bounded snapshots from the trusted issuer. Unverified, oversized or wrong-type optional claims are omitted. Later tokens do not overwrite local profile or status. Email is not unique and never links accounts. Token role, customerId and vendorId claims cannot grant ownership or administrative access. Roles returned by `/me` are loaded from `account.user_roles`; vendor authority requires an active `vendor_users` relationship with an active vendor. Suspended/closed users are denied; inactive customer profiles cannot access customer resources. Marketing consent defaults to false.

## Public APIs

| Method | Path | Result |
| --- | --- | --- |
| GET | `/api/v1/accounts/me` | 200: local user/customer IDs, optional profile snapshot, status, stored roles and preferences |
| GET | `/api/v1/accounts/me/addresses` | 200: this customer's addresses |
| POST | `/api/v1/accounts/me/addresses` | 201: new owned address, version 0 and Location header |
| PUT | `/api/v1/accounts/me/addresses/{addressId}` | 200: replace an owned address using the submitted current version |
| GET | `/api/v1/accounts/me/vendor-memberships` | 200: active memberships in active vendors for this user |

All success responses use `data` and `meta` (`requestId`, UTC `timestamp`). Collections also include `pagination.nextCursor` and `pagination.hasNext`. `pageSize` defaults to 20 and must be 1–100. Pass the returned UUID `nextCursor` unchanged; omit it for the first page. Queries scope by customer/user before applying ascending UUID order and an exclusive cursor. Each query retrieves at most pageSize + 1 rows. Pagination is not a snapshot; concurrent inserts with earlier UUIDs may appear only on a new traversal.

Address creation requires `recipientName`, `line1`, `city`, `postalCode` and uppercase ISO 3166-1 alpha-2 `countryCode`. Optional fields are `label`, `line2`, `region` and `phone`. Text is trimmed, blank optional text becomes null, and DTO lengths match column bounds. PUT is a full replacement and also requires a nonnegative `version` from the latest response. JPA optimistic locking makes competing versions return one success and one 409; clients must reload and reconcile. Unknown JSON properties are rejected, including client-supplied ownership IDs. No delete, single-address GET, profile/preferences editing or vendor administration APIs are added in this phase.

Requests without a valid JWT receive 401 with a Bearer challenge. Missing and foreign-owned address IDs both return 404. Inactive accounts/profiles receive 403, invalid input 400, and stale versions 409. Errors use RFC 9457 `application/problem+json` with stable `code`, `correlationId` and `instance`. Safe `X-Correlation-ID` values are echoed; unsafe/missing IDs are replaced. Bodies, token values, claims and database parameters are not logged by Account code.

## Vendor and role administration

Vendors are organizations, independent of users. `vendor_users` allows many users per vendor and many vendors per user, with one unique relationship per pair and a scoped VENDOR_ADMIN or VENDOR_STAFF role. Global `user_roles` independently supports multiple roles per user. A global vendor role alone does not establish membership in any organization.

No unauthenticated bootstrap administrator or role-grant endpoint exists. Until an authorized management phase defines those workflows, a controlled Account operator must provision organizations, memberships, account status and elevated role assignments using reviewed database administration. The operator must verify the external identity and local user, use a transaction, provide an existing actor UUID in `created_by`/`updated_by` where available, maintain `updated_at` and increment `version` on changes. System provisioning can use null audit actors. Do not grant administrative DML to the runtime login or create users with incomplete customer/preferences/default-role rows. Prefer first-use provisioning before staff enrollment. No business people or vendors are seeded by migrations.

## Persistence and validation

[Changeset 003](../../database/account/changelog/003-create-account-domain.yaml) adds eight Account tables with UUID keys, audit columns, VARCHAR status constraints and local FKs. The only initial data is the four system role definitions. Unique constraints cover issuer/subject, customer/preferences per user, role code, vendor code, user/role and vendor/user. Two explicit composite indexes support the actual scoped cursor queries; primary/unique constraints supply their own indexes.

The runtime role can SELECT/INSERT users, customer profiles, preferences and user roles; SELECT/INSERT/UPDATE addresses; and SELECT roles, vendors and memberships. It cannot delete business rows, administer statuses/memberships, change schema or migration history, or access another owner's tables. Application authorization is still required within the Account schema; runtime database grants are a service boundary, not row-level customer authorization.

Rollback of 003 locks all eight tables and refuses once business data exists. It drops only these tables in dependency order, without CASCADE, and leaves the Phase 2 schema/roles/history intact. Once populated, use a reviewed forward migration/recovery plan. Existing Phase 2 changesets remain immutable.

`mvn clean verify` runs unit and PostgreSQL integration tests, including actual ephemeral RSA/JWK signature verification, concurrent provisioning, ownership attacks, validation, pagination, version races, multiple roles/vendor staff, migration upgrade/repeat/rollback and scoped grants. Tests generate `account-service/target/account-api.yaml` from SpringDoc. The checked-in [Account OpenAPI contract](../../contracts/openapi/account-api.yaml) is that generated artifact; after API edits run the tests, copy it to `contracts/openapi/account-api.yaml`, and validate with `python -m openapi_spec_validator`.

`/v3/api-docs` and `/v3/api-docs.yaml` are enabled for local/test profiles and disabled by default. `API_DOCS_ENABLED` overrides that choice; when enabled the contract is publicly readable. The packaged validation script verifies default/local behavior on Java 21, with inert test IdP configuration; real token verification is covered by the HTTP integration suite.
