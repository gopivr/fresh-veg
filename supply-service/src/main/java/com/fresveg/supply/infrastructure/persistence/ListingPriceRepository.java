package com.fresveg.supply.infrastructure.persistence;
import com.fresveg.supply.domain.*;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ListingPriceRepository extends JpaRepository<ListingPrice,UUID> { List<ListingPrice> findByListingIdAndStatusOrderByValidFromAsc(UUID listingId,PriceStatus status); }
