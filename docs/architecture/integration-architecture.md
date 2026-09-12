# Integration architecture

```mermaid
sequenceDiagram
  participant C as Customer
  participant G as Gateway
  participant Com as Commerce
  participant Acc as Account
  participant Sup as Supply
  participant Ful as Fulfillment
  C->>G: Create order with Idempotency-Key
  G->>Com: Forward authenticated command
  Com->>Acc: Resolve customer/address
  Com->>Sup: Reserve inventory
  Com->>Com: Authorize payment and persist order snapshots
  Com->>Ful: Create fulfillment
  Com->>Com: Append outbox events
  Com-->>C: Confirmed order
```

Commerce coordinates checkout through synchronous calls and durable local state. Supply owns inventory reservation correctness. Fulfillment owns delivery capacity and tracking after an order is confirmed. Outbox events provide an asynchronous extension point for downstream consumers.
