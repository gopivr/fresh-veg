package com.fresveg.account.application;
import com.fresveg.account.domain.AccountStatus;
import com.fresveg.account.infrastructure.persistence.VendorRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class VendorDirectory {
 private final VendorRepository vendors;
 public VendorDirectory(VendorRepository vendors) { this.vendors=vendors; }
 @PreAuthorize("isAuthenticated()") @Transactional(readOnly=true)
 public VendorSummary read(UUID id) {
  var v=vendors.findById(id).filter(x->x.getStatus()==AccountStatus.ACTIVE).orElseThrow(()->new AccountException(HttpStatus.NOT_FOUND,"ACC-404-001","Active vendor not found."));
  return new VendorSummary(id,v.getName());
 }
 public record VendorSummary(UUID vendorId,String name) { }
}
