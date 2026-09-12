package com.fresveg.account.infrastructure.persistence;

import com.fresveg.account.domain.VendorUser;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface VendorMembershipRepository extends JpaRepository<VendorUser, UUID> {
    @Query("""
            select m from VendorUser m join fetch m.vendor v
            where m.userId = :userId and m.status = 'ACTIVE' and v.status = 'ACTIVE' order by m.id
            """)
    List<VendorUser> active(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
            select m from VendorUser m join fetch m.vendor v
            where m.userId = :userId and m.status = 'ACTIVE' and v.status = 'ACTIVE'
            and m.id > :after order by m.id
            """)
    List<VendorUser> activeAfter(@Param("userId") UUID userId, @Param("after") UUID after, Pageable pageable);
}
