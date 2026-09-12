package com.fresveg.supply.application;
import com.fresveg.supply.domain.*;
import com.fresveg.supply.api.dto.InventoryContracts.*;
import org.springframework.stereotype.Component;
@Component public class InventoryMapper {
 public InventoryResponse inventory(Inventory i) { return new InventoryResponse(i.getId(),i.getListingId(),i.getQuantityOnHand(),i.getReservedQuantity(),i.getQuantityOnHand().subtract(i.getReservedQuantity()),i.getVersion()); }
 public BatchResponse batch(InventoryBatch b) { return new BatchResponse(b.getId(),b.getInventoryId(),b.getBatchNumber(),b.getHarvestDate(),b.getReceivedDate(),b.getBestBeforeDate(),b.getExpiryDate(),b.getOrigin(),b.getGrade(),b.getCertificationData(),b.getQuantityReceived(),b.getQuantityRemaining(),b.getReservedQuantity(),b.getStatus(),b.getVersion()); }
 public ReservationResponse reservation(InventoryReservation r) { return new ReservationResponse(r.getId(),r.getInventoryId(),r.getExternalReference(),r.getQuantity(),r.getStatus(),r.getExpiresAt(),r.getVersion()); }
 public TransactionResponse transaction(InventoryTransaction t) { return new TransactionResponse(t.getId(),t.getInventoryId(),t.getBatchId(),t.getReservationId(),t.getKind(),t.getQuantityDelta(),t.getReservedDelta(),t.getReason()); }
}
