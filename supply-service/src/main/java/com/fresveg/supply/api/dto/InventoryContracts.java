package com.fresveg.supply.api.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public final class InventoryContracts {
 private InventoryContracts() { }
 public record InitializeInventoryRequest(@NotNull UUID listingId) { }
 public record ReceiveBatchRequest(@NotNull @PositiveOrZero Long version,@NotBlank @Size(max=64) String batchNumber,
 LocalDate harvestDate,@NotNull LocalDate receivedDate,LocalDate bestBeforeDate,LocalDate expiryDate,
 @NotBlank @Size(max=160) String origin,@NotBlank @Size(max=64) String grade,@NotNull Map<String,String> certificationData,
 @NotNull @Positive @Digits(integer=12,fraction=6) BigDecimal quantity) { }
 public record AdjustInventoryRequest(@NotNull @PositiveOrZero Long version,@NotNull UUID batchId,
 @NotNull @Digits(integer=12,fraction=6) BigDecimal quantityDelta,@NotBlank @Size(max=240) String reason) { }
 public record ReserveInventoryRequest(@NotNull UUID inventoryId,@NotBlank @Size(max=160) String externalReference,
 @NotNull @Positive @Digits(integer=12,fraction=6) BigDecimal quantity,@NotNull Instant expiresAt) { }
 public record OrderReservationRequest(@NotNull UUID listingId,@NotBlank @Size(max=160) String externalReference,
 @NotNull @Positive @Digits(integer=12,fraction=6) BigDecimal quantity,@NotNull Instant expiresAt,@NotNull Instant requiredUntil) { }
 public record InventoryResponse(UUID inventoryId,UUID listingId,BigDecimal quantityOnHand,BigDecimal reservedQuantity,BigDecimal availableQuantity,long version) { }
 public record BatchResponse(UUID batchId,UUID inventoryId,String batchNumber,LocalDate harvestDate,LocalDate receivedDate,
 LocalDate bestBeforeDate,LocalDate expiryDate,String origin,String grade,Map<String,String> certificationData,
 BigDecimal quantityReceived,BigDecimal quantityRemaining,BigDecimal reservedQuantity,String status,long version) { }
 public record ReservationResponse(UUID reservationId,UUID inventoryId,String externalReference,BigDecimal quantity,String status,Instant expiresAt,long version) { }
 public record TransactionResponse(UUID transactionId,UUID inventoryId,UUID batchId,UUID reservationId,String kind,
 BigDecimal quantityDelta,BigDecimal reservedDelta,String reason) { }
}
