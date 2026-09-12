package com.fresveg.supply.infrastructure.persistence;
import com.fresveg.supply.domain.*;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
public interface VendorLocationRepository extends JpaRepository<VendorLocation,UUID> { Optional<VendorLocation> findByIdAndVendorId(UUID id,UUID vendorId);
    List<VendorLocation> findByVendorIdOrderByIdAsc(UUID vendorId,Pageable page);
    List<VendorLocation> findByVendorIdAndIdGreaterThanOrderByIdAsc(UUID vendorId,UUID id,Pageable page); }
