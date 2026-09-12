package com.fresveg.supply.infrastructure.persistence;
import com.fresveg.supply.domain.Inventory;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
public interface InventoryRepository extends JpaRepository<Inventory,UUID> { @Query("select i from Inventory i where i.id=:id") @Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Inventory> locked(UUID id);
Optional<Inventory> findByListingId(UUID listingId);
@Query("select i from Inventory i, VendorListing l where i.listingId=l.id and l.vendorId=:vendor and (:cursor is null or i.id>:cursor) order by i.id")
List<Inventory> page(UUID vendor,UUID cursor,org.springframework.data.domain.Pageable page); }
