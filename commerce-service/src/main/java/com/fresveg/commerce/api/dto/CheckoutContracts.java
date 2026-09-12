package com.fresveg.commerce.api.dto;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
public final class CheckoutContracts {
 private CheckoutContracts() { }
 public record PreviewRequest(@NotNull UUID cartId,@NotNull UUID deliveryAddressId,@NotNull UUID deliverySlotId) { }
 public record Address(UUID addressId,String recipientName,String line1,String line2,String city,String region,String postalCode,String countryCode,String phone,long version) { }
 public record DeliveryQuote(UUID deliverySlotId,Instant startsAt,Instant endsAt,String currency,BigDecimal deliveryFee,String source) { }
 public record PreviewLine(UUID itemId,UUID listingId,BigDecimal quantity,UUID priceId,UUID tierId,BigDecimal unitPrice,BigDecimal lineSubtotal,Instant priceFetchedAt,Instant availabilityCheckedAt) { }
 public record Totals(BigDecimal subtotal,BigDecimal discount,BigDecimal tax,BigDecimal deliveryFee,BigDecimal grandTotal,String policyId) { }
 public record PreviewResponse(UUID cartId,long cartVersion,String currency,Address deliveryAddress,DeliveryQuote deliverySlot,List<PreviewLine> items,
 BigDecimal subtotal,BigDecimal discount,BigDecimal tax,BigDecimal deliveryFee,BigDecimal grandTotal,String pricingPolicyId,Instant evaluatedAt,boolean stockReserved) { }
}
