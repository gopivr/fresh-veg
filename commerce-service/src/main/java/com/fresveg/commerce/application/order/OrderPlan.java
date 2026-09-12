package com.fresveg.commerce.application.order;
import com.fresveg.commerce.api.dto.CheckoutContracts.PreviewResponse;
import com.fresveg.commerce.api.dto.OrderContracts.*;
import java.time.Instant;
import java.util.*;
/** Committed before any remote stock mutation; contains every deterministic reservation reference. */
public record OrderPlan(UUID orderId,UUID customerId,UUID actorId,CreateOrderRequest request,PreviewResponse preview,List<OrderLine> lines,Instant expiresAt,Instant createdAt) {
 public String reference(OrderLine line) { return "order:"+orderId+":"+line.listingId(); }
 public OrderResponse response() { return new OrderResponse(orderId,"FV-"+orderId.toString().replace("-",""),"PENDING_PAYMENT",preview.currency(),preview.subtotal(),preview.discount(),preview.tax(),preview.deliveryFee(),preview.grandTotal(),preview.deliveryAddress(),preview.deliverySlot(),preview.pricingPolicyId(),lines,expiresAt,createdAt,0); }
}
