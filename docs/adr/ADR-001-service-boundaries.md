# ADR-001: Service boundaries

Date: 2026-09-07

Status: Accepted planning baseline for the requested master architecture; implementation pending.

## Context

The master separates global products, vendor offers, customer identity, transactions and delivery. No existing service implementation constrains the split.

## Decision

Adopt Account, Catalog, Supply, Commerce and Fulfillment as independent domain owners, with a separate Gateway and narrowly scoped Common library. Communicate through APIs/events using UUID references, never shared repositories or domain entities.

## Consequences and alternatives

This preserves independent ownership but requires explicit contracts, resilience, snapshots and remote-failure compensation. A monolith or shared domain database would simplify calls but violates the requested architecture.

## Implementation and validation

Implement modules in Phase 1 and domains in Phases 3–11; test service isolation and client failure paths.

Source: [master implementation](../codex/FRESVEG_MASTER_IMPLEMENTATION.md).
