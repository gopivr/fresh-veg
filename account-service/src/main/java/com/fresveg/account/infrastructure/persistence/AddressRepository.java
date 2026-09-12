package com.fresveg.account.infrastructure.persistence;

import com.fresveg.account.domain.Address;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, UUID> {
    Optional<Address> findByIdAndCustomerId(UUID id, UUID customerId);
    List<Address> findByCustomerIdOrderByIdAsc(UUID customerId, Pageable pageable);
    List<Address> findByCustomerIdAndIdGreaterThanOrderByIdAsc(UUID customerId, UUID after, Pageable pageable);
}
