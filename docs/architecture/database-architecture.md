# Database architecture

FresVeg uses one PostgreSQL database with isolated schemas. The schema boundary is the service ownership boundary.

```mermaid
erDiagram
  ACCOUNT ||--o{ ACCOUNT_ADDRESS : owns
  CATALOG ||--o{ PRODUCT : owns
  SUPPLY ||--o{ INVENTORY_RESERVATION : owns
  COMMERCE ||--o{ ORDER_ITEM : snapshots
  COMMERCE ||--o{ PAYMENT_ATTEMPT : records
  FULFILLMENT ||--o{ SHIPMENT_EVENT : tracks
```

Each service has a runtime role and a separate migrator role. Bootstrap creates schemas and roles through the root Liquibase changelog. Services then run only their own `service` context migrations and keep Liquibase history in their owned schema. Runtime users do not own schemas and cannot modify Liquibase metadata.
