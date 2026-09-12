# ADR-004: API gateway

Date: 2026-09-07

Status: Accepted; implemented through Phase 13.

## Context

Clients need one public entry point; the master names Spring Cloud Gateway and forbids exposing internal APIs.

## Decision

Use a separate Spring Cloud Gateway module for public routes, JWT validation, CORS, request correlation, security headers and rate limiting abstraction. Route both fulfillment and fulfillments prefixes as specified. Each service retains business authorization.

## Consequences and alternatives

Central routing simplifies clients but adds an operational dependency. It must not become the sole authorization boundary. Gateway/common own no domain database.

## Implementation and validation

Health was scaffolded in Phase 1. Phase 13 implements routing, JWT validation, CORS, security headers, correlation propagation, route logging, a rate-limit abstraction and internal-route rejection tests.

Source: [master implementation](../codex/FRESVEG_MASTER_IMPLEMENTATION.md).
