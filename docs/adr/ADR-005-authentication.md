# ADR-005: Authentication

Date: 2026-09-07

Status: Accepted; Account service implemented in Phase 3, gateway and other domains pending.

## Context

Authentication must be external, while ownership and vendor membership are domain concerns. An external identity provider must be configured by deployment.

## Decision

Use an external OAuth2/OIDC provider and Spring Security Resource Server JWT validation at services and gateway. Map trusted issuer/subject to local identity. Derive customer identity from the principal; verify vendor membership through Account and enforce method-level authorization. Keep provider configuration/secrets external.

## Consequences and alternatives

Provider deployment remains an operator decision. Phase 3 requires issuer, audience and JWK URI settings, and uses ephemeral RSA/JWK test infrastructure. Role claims alone do not establish vendor ownership. Home-grown password authentication is excluded.

## Implementation and validation

Phase 3 implements JWT validation, principal abstraction, stored roles/memberships and negative ownership tests. [ADR-009](ADR-009-account-identity-and-provisioning.md) records first-use provisioning. Expand gateway and cross-service role integration in Phase 13.

Source: [master implementation](../codex/FRESVEG_MASTER_IMPLEMENTATION.md).
