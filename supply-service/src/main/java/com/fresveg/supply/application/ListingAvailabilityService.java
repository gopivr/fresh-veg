package com.fresveg.supply.application;
import com.fresveg.supply.infrastructure.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class ListingAvailabilityService {
 private final SupplyService offers;private final InventoryRepository inventory;
 private final InventoryBatchRepository batches;private final PricingPolicy pricing;private final Clock clock;
 public ListingAvailabilityService(SupplyService offers,InventoryRepository inventory,InventoryBatchRepository batches,PricingPolicy pricing,Clock clock) { this.offers=offers;this.inventory=inventory;this.batches=batches;this.pricing=pricing;this.clock=clock; }
 public AvailabilityResponse check(UUID listingId,BigDecimal quantity,Instant requiredUntil) {
  pricing.quantity(quantity);Instant now=clock.instant();
  if(requiredUntil.isBefore(now) || requiredUntil.isAfter(now.plus(Duration.ofDays(30))) || requiredUntil.getNano()%1000!=0) throw new SupplyException(HttpStatus.BAD_REQUEST,"INV-400-002","requiredUntil must be within the next thirty days with microsecond precision.");
  var listing=offers.listing(listingId);var row=inventory.findByListingId(listingId);
  BigDecimal eligible=BigDecimal.ZERO;
  if(row.isPresent()) eligible=batches.eligibleQuantity(row.get().getId(),requiredUntil.atOffset(ZoneOffset.UTC).toLocalDate(),requiredUntil.equals(requiredUntil.atOffset(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant()));
  return new AvailabilityResponse(listingId,quantity,requiredUntil,quantity.compareTo(listing.minimumOrderQuantity())>=0 && eligible.compareTo(quantity)>=0,now);
 }
 public record AvailabilityResponse(UUID listingId,BigDecimal quantity,Instant requiredUntil,boolean available,Instant checkedAt) { }
}
