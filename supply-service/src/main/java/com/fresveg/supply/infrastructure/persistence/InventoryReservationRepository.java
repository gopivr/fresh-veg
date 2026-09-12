package com.fresveg.supply.infrastructure.persistence;
import com.fresveg.supply.domain.InventoryReservation;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation,UUID> { Optional<InventoryReservation> findByClientIdAndExternalReference(String clientId,String externalReference);
List<InventoryReservation> findByInventoryIdAndStatusAndExpiresAtLessThanEqual(UUID inventoryId,String status,java.time.Instant now);
@Query(value="select distinct inventory_id from supply.inventory_reservations where status='ACTIVE' and expires_at<=:now limit 100",nativeQuery=true)
List<UUID> expiredInventories(java.time.Instant now); }
