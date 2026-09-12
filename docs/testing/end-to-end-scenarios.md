# End-to-end scenario coverage

Phase 18 validates the core FresVeg business flow through service integration tests and local orchestration. The commerce tests drive checkout against account, catalog, supply and fulfillment seams, while supply and fulfillment tests verify the owned inventory and delivery invariants directly.

| Scenario | Coverage |
| --- | --- |
| Admin creates category and product, vendor creates listing, price and inventory, customer checks out, inventory is reserved, payment is authorized, order is confirmed and fulfillment is created | `CatalogApplicationIT`, `SupplyApplicationIT`, `CommerceApplicationIT`, and `FulfillmentApplicationIT` cover the aggregate creation path, checkout preview, order creation, reservation, payment authorization, outbox rows and fulfillment creation. |
| Two customers attempt to purchase final stock | `SupplyApplicationIT` validates row-locked reservations and the commerce order path asserts final stock cannot be oversold. |
| Payment failure releases reservation | `CommerceApplicationIT` uses the declined local payment method, records failed payment/outbox events and releases the active supply reservation. |
| Duplicate order submission with the same `Idempotency-Key` | `CommerceApplicationIT` replays checkout command submission and verifies one durable order/result is returned. |
| Product price changes after order creation | `CommerceApplicationIT` asserts order item price and product snapshots remain at the original checkout values. |
| Customer modifies saved address after order | `CommerceApplicationIT` verifies the order shipping address snapshot remains unchanged after the source account address changes. |

Run the scenario set with:

```bash
./scripts/run-e2e.sh
```

Run the whole platform verification, including Liquibase migration tests, with:

```bash
mvn clean verify
```
