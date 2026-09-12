# ADR-013: Customer-owned carts and live Supply pricing

Status: Accepted in Phase 7.

Commerce needs a cart lifecycle without taking ownership of Account identity or Supply prices. Carts therefore store Account-verified customer UUID and currency, while items store listing UUID and quantity only. External UUIDs have no cross-service FK. No caller-supplied identity or price is accepted. Account /me supplies authoritative customer and audit identities on every request.

The cart is an optimistic versioned aggregate. Every item change increments its version, so simultaneous changes cannot silently overwrite each other or exceed the distinct-listing limit. Duplicate listings conflict; quantities are replaced explicitly. A failed item insertion rolls back the parent version. Runtime column grants protect immutable ownership and listing identity. Item deletion is a normal cart operation; no cart deletion or historical order behavior is added.

The SupplyClient port reads the existing public listing-detail contract. Its HTTP adapter selects the currency schedule and highest applicable whole-quantity tier from Supply's current authoritative amounts. It parses exact decimals and fails closed on malformed owner data. No amount is persisted or used as a fallback. This reuses the existing contract without scanning product-wide offers or changing Supply. Tier-selection compatibility is tested against the actual Supply JAR and must be maintained if that contract evolves.

Cart reads paginate at most ten items per page, with cursors tied to the cart version and owner. Unavailable or no-longer-priceable items remain visible without a price; owner outages fail the response instead of serving stale data. Deletion remains possible when Supply is unavailable. Configurable transport timeouts, per-owner concurrency gates and a short failure cooldown bound dependency load. No retries are introduced for mutating cart requests.

Consequences: customer access requires Account availability and multi-audience tokens; pricing requires Supply/Catalog availability through the existing Supply public policy. Per-item quotes are live rather than atomic. No stock is reserved, and no checkout totals are calculated. A future checkout must revalidate the cart and resolve all authoritative amounts, inventory and delivery inputs. See the [cart guide](../commerce/carts.md).
