# ADR-006: Transactional outbox

Date: 2026-09-07

Status: Accepted and implemented through Phase 12 for Commerce publication.

## Context

Phase 9 requires order and event insertion in one transaction; Phase 10 adds payment lifecycle and refund events to the same local outbox. Phase 12 provides reliable publication. Remote inventory and payment gateways cannot join that local transaction.

## Decision

Create commerce.outbox_events with order persistence in Phase 9 and keep using the same table for Phase 10 payment/refund lifecycle events. Phase 12 reuses it with a publisher abstraction, no-op/local modes and an optional Kafka publisher. Use versioned event envelopes and durable retry state; publication failure must not undo committed orders or payment records. Plan idempotent consumers and compensating inventory operations.

## Consequences and alternatives

Delivery may be repeated; consumers deduplicate by eventId. Disabled publishing must not silently discard durable intent. Reconciliation is needed for remote failures/crashes. Direct database-plus-broker dual writes cannot guarantee atomicity.

## Implementation and validation

Phase 9 tests order/outbox atomicity and reservation compensation. Phase 10 tests payment authorization/failure/refund rows and outbox events. Phase 12 tests publisher retries, deduplication expectations and failure handling without rolling back committed business data.

Source: [master implementation](../codex/FRESVEG_MASTER_IMPLEMENTATION.md).

Phase 9 implementation: [ADR-015](ADR-015-durable-orders-and-reservation-compensation.md) and [order guide](../commerce/orders.md). OrderCreated, PaymentAuthorized, PaymentFailed, OrderConfirmed, RefundCompleted and OrderCancelled use schemaVersion=1 envelopes and remain PENDING; runtime cannot rewrite event payloads.
