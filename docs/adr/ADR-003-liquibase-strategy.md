# ADR-003: Liquibase strategy

Date: 2026-09-07

Status: Implemented in Phase 2; execution details are refined by [ADR-008](ADR-008-liquibase-execution-boundaries.md).

## Context

There are no changelogs or deployed changesets. Both root bootstrap and service-owned migration execution are required.

## Decision

Use immutable service-prefixed changesets, one master per service, and a deterministic account/catalog/supply/commerce/fulfillment root master. Runtime services load only their own master. Hibernate uses ddl-auto=validate. Define consistent paths and tracking/locking placement for root and service execution in Phase 2.

## Consequences and alternatives

A root operator path helps bootstrap but must not become a service permission bypass or cause changeset duplication. Hibernate generation and unmanaged schema SQL are rejected. Destructive rollbacks require explicit treatment, not invented safe reversibility.

## Implementation and validation

Phase 2 tests clean apply, unchanged rerun, and root/service interoperability against PostgreSQL.

Source: [master implementation](../codex/FRESVEG_MASTER_IMPLEMENTATION.md).
