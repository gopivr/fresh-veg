package com.fresveg.account.infrastructure.persistence;
import com.fresveg.account.domain.Vendor;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface VendorRepository extends JpaRepository<Vendor,UUID> { }
