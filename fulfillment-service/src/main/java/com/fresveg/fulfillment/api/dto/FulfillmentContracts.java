package com.fresveg.fulfillment.api.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
public final class FulfillmentContracts {
 private FulfillmentContracts() {}
 public record DeliverySlotResponse(UUID slotId,String serviceArea,Instant startTime,Instant endTime,int capacity,int reservedCapacity,String status,long version) {}
 public record FulfillmentItemResponse(UUID fulfillmentItemId,UUID orderItemId,UUID productId,UUID listingId,UUID vendorId,String productName,BigDecimal quantity,String unitCode,String status) {}
 public record ShipmentEventResponse(UUID eventId,UUID shipmentId,String status,String description,Instant occurredAt) {}
 public record ShipmentResponse(UUID shipmentId,String status,String carrier,String trackingReference,List<ShipmentEventResponse> events,long version) {}
 public record FulfillmentResponse(UUID fulfillmentId,UUID orderId,UUID customerId,UUID deliverySlotId,String status,String serviceArea,Map<String,Object> deliveryAddress,Instant requestedStart,Instant requestedEnd,List<FulfillmentItemResponse> items,List<ShipmentResponse> shipments,long version) {}
 public record TrackingResponse(UUID fulfillmentId,String status,List<ShipmentResponse> shipments) {}
 public record CreateFulfillmentRequest(@NotNull UUID orderId,@NotNull UUID customerId,@NotNull UUID deliverySlotId,@NotBlank @Size(max=80) String serviceArea,@NotNull Instant requestedStart,@NotNull Instant requestedEnd,@NotEmpty Map<@Size(max=80) String,Object> deliveryAddress,@NotEmpty List<@Valid CreateFulfillmentItem> items) {}
 public record CreateFulfillmentItem(@NotNull UUID orderItemId,@NotNull UUID productId,@NotNull UUID listingId,@NotNull UUID vendorId,@NotBlank @Size(max=240) String productName,@NotNull @DecimalMin(value="0.000001") BigDecimal quantity,@NotBlank @Size(max=32) String unitCode) {}
 public record TransitionRequest(@NotBlank @Pattern(regexp="PACKING|READY_FOR_DELIVERY|OUT_FOR_DELIVERY|DELIVERED|CANCELLED") String status,@NotNull Long version,@Size(max=240) String description) {}
 public record DeliverySlotSeedRequest(@NotBlank @Size(max=80) String serviceArea,@NotNull Instant startTime,@NotNull Instant endTime,@Min(0) int capacity,@Pattern(regexp="OPEN|CLOSED|CANCELLED") String status) {}
}
