package com.fresveg.commerce.api.dto;
import com.fresveg.commerce.api.dto.CheckoutContracts.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import tools.jackson.databind.JsonNode;
public final class OrderContracts {
 private OrderContracts() { }
 public record CreateOrderRequest(@NotNull UUID cartId,@NotNull UUID deliveryAddressId,@NotNull UUID deliverySlotId,@NotNull UUID paymentMethodId) { }
 public record OrderLine(UUID orderItemId,UUID productId,UUID listingId,UUID vendorId,String productName,String vendorName,String vendorSku,String unitCode,
 BigDecimal quantity,BigDecimal unitPrice,BigDecimal discountAmount,BigDecimal taxAmount,BigDecimal lineTotal,JsonNode productSnapshot) { }
 public record OrderResponse(UUID orderId,String orderNumber,String status,String currency,BigDecimal subtotal,BigDecimal discountAmount,BigDecimal taxAmount,
 BigDecimal deliveryFee,BigDecimal grandTotal,Address shippingAddress,DeliveryQuote deliverySlot,String pricingPolicyId,List<OrderLine> items,Instant reservationExpiresAt,Instant createdAt,long version) {
  public OrderResponse state(String s,long v) { return new OrderResponse(orderId,orderNumber,s,currency,subtotal,discountAmount,taxAmount,deliveryFee,grandTotal,shippingAddress,deliverySlot,pricingPolicyId,items,reservationExpiresAt,createdAt,v); }
 }
 public record VendorDecisionRequest(@NotNull @jakarta.validation.constraints.PositiveOrZero Long version) { }
 public record VendorOrderResponse(UUID orderId,String orderNumber,String status,String vendorStatus,String currency,DeliveryQuote deliverySlot,List<OrderLine> items,long version) { }
 public record VendorOrderPage(List<VendorOrderResponse> data,UUID nextCursor,boolean hasNext) { }
 public record OrderPage(List<OrderResponse> data,UUID nextCursor,boolean hasNext) { }
}
