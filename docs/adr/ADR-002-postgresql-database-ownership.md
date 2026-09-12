# ADR-002: PostgreSQL database ownership

Date: 2026-09-07

Status: Implemented in Phase 2; execution details are refined by [ADR-008](ADR-008-liquibase-execution-boundaries.md).

## Context

The master permits an initial shared cluster while requiring exclusive service ownership.

## Decision

Plan one local fresveg database with account, catalog, supply, commerce and fulfillment schemas and separate credentials. Enforce privileges, use local foreign keys and external UUIDs without cross-schema foreign keys. Separate runtime DML access from migration DDL privileges in production.

## Consequences and alternatives

Schema isolation reduces local overhead but does not provide physical failure isolation. Separate production databases remain possible. Shared unrestricted credentials are rejected because schema names alone do not enforce ownership.

## Implementation and validation

Phase 2 defines roles/grants and verifies denied cross-schema access; later phases validate owned migrations.

Source: [master implementation](../codex/FRESVEG_MASTER_IMPLEMENTATION.md).
