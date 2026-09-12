# Checkout preview — Phase 8

POST `/api/v1/checkout/preview` accepts exactly cartId, deliveryAddressId and deliverySlotId. It resolves the authenticated Account customer, their cart and address, current Supply prices, stock eligible through the delivery window, and server-configured delivery/pricing policies. It returns a complete monetary breakdown without creating an order, reservation or persisted quote. Existing cart and stock mutations remain unchanged.

## Ownership and read consistency

Commerce reuses its local JWT validation and Account-backed CartIdentity. Only that customer can preview the cart. Address lookup follows Account's existing authenticated address pages (100 records per page, at most 100 pages) and verifies the requested address exists in that customer's collection. Repeated/malformed cursors fail closed. Foreign/missing carts or addresses return the same 404 detail. No client customer ID, address fields, prices, tax, fees or totals are accepted.

The entire cart is read in one repeatable-read Commerce transaction, up to the existing 50-item limit. Checkout does not paginate or accidentally calculate only the first ten cart-read items. Empty carts return 409. The response carries cartVersion and the current owned address with its version. Prices, availability and remote address data are live lookups, not a distributed snapshot; later edits do not alter the returned breakdown but require revalidation before order creation.

Owner calls reuse the Phase 7 timeout/concurrency/failure-cooldown client. Account address pages forward the validated bearer token and correlation ID; Supply public lookups are anonymous. A 30-second preview deadline is checked before address, price and availability calls; an in-flight request may extend it by its configured request timeout. Failure returns 503 rather than cached prices or invented availability.

## Supply availability addition

GET `/api/v1/supply/listings/{listingId}/availability?quantity={q}&requiredUntil={instant}` is a small public read API required by checkout. It returns listingId, requested quantity/horizon, available and checkedAt. It does not expose vendor addresses, inventory IDs or exact stock balances, and never creates or modifies inventory/reservations.

Supply applies its existing public listing validation: active listing/location, live active Catalog product/variant/unit, and current pricing. It sums active batch `quantityRemaining - reservedQuantity` for batches that remain usable through requiredUntil. The listing MOQ must be satisfied. Missing inventory returns available=false. Existing reserved quantities, including expired holds not yet cleaned by the worker, remain excluded conservatively. No expiry worker or stock mutation is triggered by this read.

requiredUntil must be from server now through thirty days ahead, with at most microsecond precision; checkout passes the selected slot's endsAt. Batch expiry is exclusive UTC midnight. A batch expiring exactly at requiredUntil can satisfy the window up to that boundary, matching the reservation allocation convention. The indexed batch aggregate query uses the existing inventory/expiry index; no speculative index or migration is added. Stock can change immediately after the read. Checkout returns stockReserved=false and makes no allocation guarantee.

## Delivery abstraction and configuration

DeliverySlotProvider separates checkout from the Fulfillment domain until Commerce is explicitly wired to it in a later phase. ConfiguredDeliverySlots remains the Phase 8 adapter: operators explicitly configure slot IDs, windows, served country/postal codes, currency and fee. These are merchant-owned preview rules, not a claim that Fulfillment capacity was reserved or even checked. Phase 11 introduces Fulfillment tables and APIs separately, but this checkout adapter still does not reserve Fulfillment capacity. A future Fulfillment adapter can replace this interface implementation.

`CHECKOUT_DELIVERY_SLOTS` is a JSON array. Each entry requires:

| Field | Meaning |
| --- | --- |
| deliverySlotId | UUID selected by the caller from an externally supplied, operator-managed slot catalog |
| startsAt / endsAt | ISO instants; endsAt after startsAt; microsecond precision |
| countryCode / postalCodes | ISO country and nonempty exact postal-code allowlist |
| currency / deliveryFee | Supported ISO currency and nonnegative amount representable at its minor-unit scale |

Preview requires a matching address/currency, a future start and an end within thirty days. Unknown, elapsed, out-of-area or currency-mismatched slots return 409. Empty configuration returns 503. Checkout still does not call slot discovery in this phase. Invalid/duplicate configuration fails startup. The adapter returns source=CONFIGURED so callers can distinguish this initial provider from future Fulfillment-backed quotes.

## Pricing abstraction and calculation

CheckoutPricingPolicy owns line rounding and totals. ConfiguredCheckoutPricing implements explicit per-country/currency rules. `CHECKOUT_PRICING_RULES` is a JSON array requiring policyId, countryCode, currency, discountRate, taxRate and taxDelivery (boolean). Rates are decimal fractions between 0 and 1 with at most six fractional digits. Duplicate destination/currency rules or malformed configuration fail startup. Missing policy returns 503; no default tax exemption, discount or free delivery is inferred. Test fixtures use artificial rates only.

This initial policy is a configurable pricing rule, not a general jurisdictional tax engine. Operators must supply appropriate rules for their supported destinations; more granular jurisdiction/product/promotion rules belong behind the same interface. No tax amounts are hard-coded in the controller or checkout orchestration service.

All arithmetic uses BigDecimal, and currency minor units come from the ISO currency vocabulary. The policy applies HALF_UP rounding:

1. Multiply each current Supply unitPrice by its cart quantity and round the line subtotal to currency scale.
2. Sum the rounded line subtotals.
3. Round subtotal × discountRate to obtain discount.
4. Taxable amount is subtotal − discount, plus deliveryFee only when taxDelivery is true.
5. Round taxable amount × taxRate to obtain tax.
6. grandTotal = subtotal − discount + tax + deliveryFee.

The fixed configured fee must already fit currency scale. Unit prices retain Supply's original exact precision; line/totals rounding occurs only in this policy. Discounts apply to merchandise, not delivery. The response exposes lineSubtotal, subtotal, discount, tax, deliveryFee, grandTotal and pricingPolicyId, so the arithmetic can be reconciled. No FX, coupon acceptance, item-specific taxes or order/payment processing is implemented.

## Response and errors

The normal data/meta envelope contains cartId/cartVersion/currency, the owned deliveryAddress, deliverySlot quote, all priced items, the monetary breakdown, evaluatedAt and stockReserved=false. Each item contains item/listing IDs, quantity, selected price/tier IDs, unitPrice, rounded lineSubtotal, priceFetchedAt and availabilityCheckedAt. Prices are recalculated on every request through the existing SupplyClient; no cart amount is authoritative.

RFC 9457 errors retain code/correlationId/instance: 400 invalid or unknown input, 401 missing/invalid JWT, 403 Account identity denied, 404 missing/foreign cart/address, 409 empty/unpriceable/insufficient-stock cart or invalid delivery selection, 503 unavailable/malformed owner or missing delivery/pricing configuration. No partial preview is returned. The API docs profile defaults and all earlier endpoints remain compatible. See the [Commerce contract](../../contracts/openapi/commerce-api.yaml) and [Supply contract](../../contracts/openapi/supply-api.yaml).

## Validation and phase boundary

No database objects or changelogs change in Phase 8. Existing sixteen changesets, schema ownership and validate-only JPA startup remain intact. The full Maven suite and Java 21 packaged checks revalidate clean/repeat migration and existing guarded rollbacks.

Checkout HTTP tests use real Account, Catalog and Supply processes with isolated PostgreSQL databases. They verify exact discount/tax/fee totals, current repricing, owned addresses, insufficient stock, invalid delivery selection, outage handling, complete carts beyond the cart-read page and unchanged Commerce/reservation state. Supply tests verify reserved/expired stock exclusion, missing inventory, horizon validation and read-only behavior. Unit tests cover rounding for USD/JPY/KWD, delivery tax, missing/invalid policy, slot validation, address pagination and malformed/repeated cursors.

Preview is a nonbinding read, not order creation or an inventory/delivery hold. Phase 9 must revalidate and coordinate the durable order/reservation/snapshot lifecycle. See [ADR-014](../adr/ADR-014-checkout-preview-and-policy-boundaries.md).
