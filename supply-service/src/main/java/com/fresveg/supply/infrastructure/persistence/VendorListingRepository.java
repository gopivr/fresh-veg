package com.fresveg.supply.infrastructure.persistence;
import com.fresveg.supply.domain.*;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
public interface VendorListingRepository extends JpaRepository<VendorListing,UUID> { List<VendorListing> findByVendorIdOrderByIdAsc(UUID vendorId,Pageable page);
    List<VendorListing> findByVendorIdAndIdGreaterThanOrderByIdAsc(UUID vendorId,UUID id,Pageable page); }
