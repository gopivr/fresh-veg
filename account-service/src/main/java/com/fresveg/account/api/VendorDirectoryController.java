package com.fresveg.account.api;
import com.fresveg.account.application.VendorDirectory;
import com.fresveg.account.api.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping(value="/api/v1/accounts/vendors",produces="application/json") @SecurityRequirement(name="bearerAuth")
public class VendorDirectoryController {
 private final VendorDirectory service;
 public VendorDirectoryController(VendorDirectory service) { this.service=service; }
 @GetMapping("/{vendorId}") @Operation(operationId="getVendorSummary",summary="Read an active vendor business name for transaction snapshots")
 public ApiResponse<VendorDirectory.VendorSummary> read(@PathVariable UUID vendorId,HttpServletRequest r) {
  return new ApiResponse<>(service.read(vendorId),new ApiResponse.Meta(AccountProblems.requestId(r),Instant.now()));
 }
}
