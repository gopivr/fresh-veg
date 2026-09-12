# Fulfillment service

Phase 11 implements the Fulfillment delivery domain. Fulfillment owns delivery slots and capacity, fulfillment records and items, shipments, shipment events and delivery assignments. It references Commerce orders, customers, products, listings and vendors by UUID only; it does not use cross-schema foreign keys or shared repositories.

## API surface

Public reads:

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/api/v1/fulfillment/slots` | List open delivery slots by service area and time window. |
| GET | `/api/v1/fulfillments/{id}` | Read an authenticated fulfillment view with items and shipments. |
| GET | `/api/v1/fulfillments/{id}/tracking` | Read shipment tracking events for a fulfillment. |

Internal service operations:

| Method | Endpoint | Purpose |
| --- | --- | --- |
| POST | `/internal/v1/fulfillments` | Create a fulfillment for an existing order and reserve one delivery-slot capacity unit. |
| POST | `/internal/v1/fulfillments/{id}/transitions` | Advance fulfillment state with optimistic version checks. |
| POST | `/internal/v1/fulfillment/slots` | Create an operational delivery slot for configured service subjects. |

All responses use the normal `data` / `meta.requestId` envelope. Missing or invalid JWTs return 401, forbidden service subjects return 403 and domain conflicts return RFC 9457 problem responses with `code` and `correlationId`.

## Lifecycle

Fulfillments start at `CREATED`, then advance through `PACKING`, `READY_FOR_DELIVERY`, `OUT_FOR_DELIVERY` and `DELIVERED`. `CREATED`, `PACKING` and `READY_FOR_DELIVERY` may transition to `CANCELLED`. Terminal states may be repeated idempotently with the same target. `OUT_FOR_DELIVERY`, `DELIVERED` and `CANCELLED` update the associated shipment and append tracking events.

Fulfillment creation is stable for replay by `orderId`: if a fulfillment already exists for an order, the service returns that existing fulfillment instead of consuming additional slot capacity. New creation locks the selected delivery slot, verifies it is open, verifies request area/window matches the slot and fails with 409 when capacity is exhausted.

## Database

Changeset `fulfillment-003-create-domain` creates six Fulfillment-owned tables:

- `fulfillment.delivery_slots`
- `fulfillment.fulfillments`
- `fulfillment.fulfillment_items`
- `fulfillment.shipments`
- `fulfillment.shipment_events`
- `fulfillment.delivery_assignments`

Primary keys are UUIDs. Timestamps are `TIMESTAMPTZ`. Status columns use VARCHAR plus check constraints. Local foreign keys connect Fulfillment-owned tables only. Runtime grants allow reads, inserts and the narrow lifecycle/capacity updates required by the service; runtime cannot delete rows, mutate order/customer snapshot identifiers or access Liquibase metadata.

The rollback script locks all six business tables and refuses rollback once any business data exists. Empty rollback drops only the Phase 11 tables in dependency order.

## Configuration

Fulfillment requires the shared datasource settings plus trusted JWT validation settings:

- `OIDC_ISSUER_URI`
- `OIDC_AUDIENCE` (typically `fresveg-fulfillment`)
- `OIDC_JWK_SET_URI`
- `FULFILLMENT_SERVICE_SUBJECTS` for internal service operations; the empty default denies all internal callers.

OpenAPI docs follow the existing profile behavior: disabled by default in packaged/default runs and enabled in local/test.

## Validation

Phase 11 tests cover clean migration, service-history count, table creation, restricted runtime grants, populated rollback refusal, public slot reads, authenticated fulfillment/tracking reads, service-subject enforcement, idempotent order fulfillment creation, capacity exhaustion and state transitions. The generated contract is stored at [`contracts/openapi/fulfillment-api.yaml`](../../contracts/openapi/fulfillment-api.yaml).
