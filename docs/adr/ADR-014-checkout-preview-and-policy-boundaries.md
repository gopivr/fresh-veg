# ADR-014: Checkout preview and explicit policy boundaries

Status: Accepted in Phase 8.

Checkout requires authoritative customer, address, price, stock and delivery inputs without creating an order. Commerce therefore reads the complete owned cart in a consistent local transaction, follows Account's existing authenticated address pagination, and reuses the current Supply pricing adapter. Client amounts and ownership fields are not accepted. The result is a nonbinding breakdown carrying source versions/timestamps; no quote, order or reservation is persisted.

Supply previously exposed only vendor stock reads and mutating internal reservations. A small additive public listing-availability API now checks a requested quantity through a delivery horizon, revealing only a boolean result. It uses Supply's own active listing/Catalog validation and existing batch balances/indexes, excludes reserved stock, and never mutates or expires holds. This avoids cross-schema reads, misusing vendor credentials or making temporary reservations merely to preview checkout. No migration is justified.

DeliverySlotProvider is an abstraction until Fulfillment is implemented. The initial configured adapter requires explicit slot IDs, address coverage, currency, future windows and fees, with empty configuration failing closed. It does not claim Fulfillment capacity or create a slot-management domain. CheckoutPricingPolicy similarly separates exact monetary arithmetic from orchestration, with explicit destination/currency discount, tax and delivery-tax rules. There are no silent zero-tax or free-delivery defaults.

Line amounts round HALF_UP to currency minor units before summation; merchandise discount is rounded before taxable base calculation; delivery is taxable only by explicit policy. Unit prices remain exact Supply values. Policy identity, breakdown and provider source are returned for traceability. This simple configured policy is replaceable when more granular business rules or real Fulfillment quoting exist.

Consequences: successful preview requires configured delivery/pricing policy and live owner services. Stock, prices, cart and address may change after evaluation; later order creation must revalidate and acquire durable reservations. Existing cart endpoints, database ownership and all numbered migrations remain unchanged. No gateway, order, payment or Fulfillment implementation is started. See the [checkout guide](../commerce/checkout.md).
