# ADR-010: Catalog authorization and product aggregate

Date: 2026-09-08

Status: Accepted and implemented in Phase 4.

## Context

Catalog needs administrator mutations before comprehensive gateway/security integration. Phase 3 made Account's stored roles authoritative and exposed `/me`; using unverified role claims or cross-schema access would contradict that work. Product variants must retain stable identifiers for later Supply references.

## Decision

Allow public ACTIVE product/category reads. Validate supplied JWTs in Catalog and use method-level admin authorization backed by the existing Account `/me` API. Forward bearer/correlation headers to the configured trusted Account URL and require stored PLATFORM_ADMIN authority with active identity. Derive audit UUIDs from the Account response. Cache the result only within one request, reject malformed/unavailable responses and require tokens accepted by both services. Preserve Account code/contracts and all gateway phase boundaries.

Treat product metadata, classifications, variants and images as one versioned aggregate. Flush the parent optimistic version before child mutations and commit all changes together. Preserve variants by code/UUID and archive omissions; replace mutable image/classification metadata. PATCH is a documented status/version operation, while PUT replaces the full editable representation. Categories are created beneath existing parents and cannot be reparented in this phase.

Use bounded keyset pagination with name/code plus UUID tie-breakers and filter-bound cursors. Support simple-dictionary PostgreSQL name web search and JSONB containment, with matching GIN indexes; do not introduce search extensions or speculative indexes.

## Consequences

Writes and non-active reads depend on Account availability and its existing profile/status policy, and tokens need compatible audiences. Public reads remain independent of Account. A future service-to-service authority contract can replace `/me` deliberately; no new internal route or gateway implementation is introduced now.

Variant codes remain globally reserved after archival, protecting identity; link/image IDs may change on replacement. Cursors are not snapshots or security tokens. Unit conversion, fuzzy search, hierarchy mutation, uploads and management of normalized attributes remain future requirements, not Phase 4 scaffolding.

See [Catalog operations](../catalog/catalog-service.md), [ADR-009](ADR-009-account-identity-and-provisioning.md) and [database execution boundaries](ADR-008-liquibase-execution-boundaries.md).
