# Solution architecture

FresVeg uses a service-per-domain architecture with an API gateway at the public edge. Each domain service owns its database schema, migrations and HTTP contract. Cross-service state is referenced by UUID and snapshots are stored where business history must remain immutable.

```mermaid
flowchart LR
  Web[Web/mobile clients] --> Gateway[API Gateway]
  Gateway --> Account[Account]
  Gateway --> Catalog[Catalog]
  Gateway --> Supply[Supply]
  Gateway --> Commerce[Commerce]
  Gateway --> Fulfillment[Fulfillment]
  Commerce --> Account
  Commerce --> Catalog
  Commerce --> Supply
  Commerce --> Fulfillment
  Supply --> Account
  Supply --> Catalog
  Account --> DB[(PostgreSQL)]
  Catalog --> DB
  Supply --> DB
  Commerce --> DB
  Fulfillment --> DB
  Commerce --> Outbox[(Outbox events)]
```

Synchronous APIs serve user and service commands. Commerce owns checkout consistency and uses durable idempotency records, order snapshots, payment attempts and outbox rows to survive retries and ambiguous downstream results.
