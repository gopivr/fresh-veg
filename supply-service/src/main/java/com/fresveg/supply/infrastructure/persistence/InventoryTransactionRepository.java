package com.fresveg.supply.infrastructure.persistence;
import com.fresveg.supply.domain.InventoryTransaction;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction,UUID> { @Query("select t from InventoryTransaction t where t.inventoryId=:id and (:cursor is null or t.id>:cursor) order by t.id")
List<InventoryTransaction> page(UUID id,UUID cursor,org.springframework.data.domain.Pageable page); }
