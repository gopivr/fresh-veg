package com.fresveg.supply.infrastructure.persistence;
import com.fresveg.supply.domain.InventoryBatch;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
public interface InventoryBatchRepository extends JpaRepository<InventoryBatch,UUID> {
@Query("select coalesce(sum(b.quantityRemaining-b.reservedQuantity),0) from InventoryBatch b where b.inventoryId=:inventoryId and b.status='ACTIVE' and (b.expiryDate is null or b.expiryDate>:date or (:midnight=true and b.expiryDate=:date))")
java.math.BigDecimal eligibleQuantity(UUID inventoryId,java.time.LocalDate date,boolean midnight);
 List<InventoryBatch> findByInventoryIdOrderByExpiryDateAscIdAsc(UUID inventoryId);
@Query("select b from InventoryBatch b where b.inventoryId=:id and (:cursor is null or b.id>:cursor) order by b.id")
List<InventoryBatch> page(UUID id,UUID cursor,org.springframework.data.domain.Pageable page); }
