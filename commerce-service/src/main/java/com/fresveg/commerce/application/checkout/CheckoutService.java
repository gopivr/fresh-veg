package com.fresveg.commerce.application.checkout;
import com.fresveg.commerce.api.dto.CheckoutContracts.*;
import com.fresveg.commerce.application.SupplyClient;
import com.fresveg.commerce.application.observability.CommerceMetrics;
import com.fresveg.commerce.infrastructure.client.*;
import com.fresveg.commerce.infrastructure.persistence.*;
import com.fresveg.commerce.infrastructure.security.CartIdentity;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class CheckoutService {
 private final CartRepository carts;private final CartItemRepository items;private final CartIdentity identity;
 private final CheckoutAddressClient addresses;private final SupplyClient prices;private final CheckoutAvailabilityClient availability;
 private final DeliverySlotProvider delivery;private final CheckoutPricingPolicy pricing;private final CommerceMetrics metrics;private final Clock clock;
 public CheckoutService(CartRepository carts,CartItemRepository items,CartIdentity identity,CheckoutAddressClient addresses,SupplyClient prices,CheckoutAvailabilityClient availability,DeliverySlotProvider delivery,CheckoutPricingPolicy pricing,CommerceMetrics metrics,Clock clock) {
  this.carts=carts;this.items=items;this.identity=identity;this.addresses=addresses;this.prices=prices;this.availability=availability;this.delivery=delivery;this.pricing=pricing;this.metrics=metrics;this.clock=clock;
 }
 @PreAuthorize("isAuthenticated()")
 public PreviewResponse preview(PreviewRequest request) {
  try {
  long deadline=System.nanoTime()+Duration.ofSeconds(30).toNanos();var customer=identity.current();
  var cart=carts.findByIdAndCustomerId(request.cartId(),customer.customerId()).orElseThrow(CheckoutErrors::missing);
  var rows=items.page(cart.getId(),null,PageRequest.of(0,51));if(rows.isEmpty() || rows.size()>50)throw CheckoutErrors.conflict("Checkout requires a nonempty cart with at most fifty items.");
  var address=addresses.owned(request.deliveryAddressId(),deadline);var slot=delivery.resolve(request.deliverySlotId(),address,cart.getCurrency());
  var lines=new ArrayList<PreviewLine>();
  for(var item:rows) {
   if(System.nanoTime()>deadline)throw OwnerHttp.unavailable();
   var quote=prices.quote(item.getListingId(),cart.getCurrency(),item.getQuantity());if(!"PRICED".equals(quote.status()) || quote.price()==null)throw CheckoutErrors.conflict("A cart item cannot currently be priced at its quantity and currency.");
   if(System.nanoTime()>deadline)throw OwnerHttp.unavailable();
   Instant checkedAt=availability.require(item.getListingId(),item.getQuantity(),slot.endsAt());var price=quote.price();
   lines.add(new PreviewLine(item.getId(),item.getListingId(),item.getQuantity(),price.priceId(),price.tierId(),price.unitPrice(),pricing.lineSubtotal(price.unitPrice(),item.getQuantity(),cart.getCurrency()),price.fetchedAt(),checkedAt));
  }
  var totals=pricing.calculate(lines.stream().map(PreviewLine::lineSubtotal).toList(),address,slot,cart.getCurrency());
  return new PreviewResponse(cart.getId(),cart.getVersion(),cart.getCurrency(),address,slot,List.copyOf(lines),totals.subtotal(),totals.discount(),totals.tax(),totals.deliveryFee(),totals.grandTotal(),totals.policyId(),clock.instant(),false);
  } catch (RuntimeException error) {
   metrics.checkoutFailure();
   throw error;
  }
 }
}
