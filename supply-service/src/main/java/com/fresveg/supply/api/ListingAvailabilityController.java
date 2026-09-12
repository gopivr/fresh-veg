package com.fresveg.supply.api;
import com.fresveg.supply.api.dto.ApiResponse;
import com.fresveg.supply.application.ListingAvailabilityService;
import com.fresveg.supply.application.ListingAvailabilityService.AvailabilityResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
@RestController
public class ListingAvailabilityController {
 private final ListingAvailabilityService service;
 public ListingAvailabilityController(ListingAvailabilityService service) { this.service=service; }
 @GetMapping(value="/api/v1/supply/listings/{listingId}/availability",produces="application/json")
 @Operation(operationId="checkListingAvailability",summary="Check eligible unreserved stock through a requested time without creating a reservation")
 public ApiResponse<AvailabilityResponse> check(@PathVariable UUID listingId,@RequestParam BigDecimal quantity,@RequestParam Instant requiredUntil,HttpServletRequest r) {
  return new ApiResponse<>(service.check(listingId,quantity,requiredUntil),new ApiResponse.Meta(SupplyProblems.requestId(r),Instant.now()));
 }
}
