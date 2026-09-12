package com.fresveg.supply.api;

import com.fresveg.supply.api.dto.*;
import com.fresveg.supply.application.SupplyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value="/api/v1/supply",produces="application/json")
@Tag(name="Supply offers")
public class SupplyController {
    private final SupplyService supply;
    public SupplyController(SupplyService supply) { this.supply=supply; }
    @GetMapping("/products/{productId}/offers")
    @Operation(operationId="listProductOffers",summary="Resolve current currency and quantity prices for active product offers")
    public CollectionResponse<OfferResponse> offers(@PathVariable UUID productId,@RequestParam(required=false) UUID variantId,
            @RequestParam String currency,@RequestParam BigDecimal quantity,@RequestParam(defaultValue="20") int pageSize,
            @RequestParam(required=false) String cursor,HttpServletRequest request) {
        return collection(supply.offers(productId,variantId,currency,quantity,pageSize,cursor),request);
    }
    @GetMapping("/listings/{listingId}")
    @Operation(operationId="getListing",summary="Read active listing metadata and currently effective price schedules")
    public ApiResponse<ListingResponse> listing(@PathVariable UUID listingId,HttpServletRequest request) { return response(supply.listing(listingId),request); }
    @GetMapping("/vendor/listings")
    @Operation(operationId="listVendorListings",summary="List listings for a vendor selected from verified memberships")
    @SecurityRequirement(name="bearerAuth")
    public CollectionResponse<ListingSummary> listings(@RequestParam UUID vendorId,@RequestParam(defaultValue="20") int pageSize,
            @RequestParam(required=false) String cursor,HttpServletRequest request) { return collection(supply.vendorListings(vendorId,pageSize,cursor),request); }
    @GetMapping("/vendor/listings/{listingId}")
    @Operation(operationId="getVendorListing",summary="Read an owned listing including configured future and expired schedules")
    @SecurityRequirement(name="bearerAuth")
    public ApiResponse<ListingResponse> ownedListing(@PathVariable UUID listingId,HttpServletRequest request) { return response(supply.vendorListing(listingId),request); }
    @PostMapping(value="/vendor/listings",consumes="application/json")
    @Operation(operationId="createVendorListing",summary="Vendor administrator creates an owned listing and price schedules")
    @SecurityRequirement(name="bearerAuth")
    public ResponseEntity<ApiResponse<ListingResponse>> createListing(@RequestParam UUID vendorId,@Valid @RequestBody CreateListingRequest body,HttpServletRequest request) {
        var listing=supply.createListing(vendorId,body);
        return ResponseEntity.created(URI.create("/api/v1/supply/vendor/listings/"+listing.listingId())).body(response(listing,request));
    }
    @PutMapping(value="/vendor/listings/{listingId}",consumes="application/json")
    @Operation(operationId="replaceVendorListing",summary="Replace owned listing metadata and configured schedules using the current version")
    @SecurityRequirement(name="bearerAuth")
    public ApiResponse<ListingResponse> updateListing(@PathVariable UUID listingId,@Valid @RequestBody UpdateListingRequest body,HttpServletRequest request) {
        return response(supply.updateListing(listingId,body),request);
    }
    @GetMapping("/vendor/locations")
    @Operation(operationId="listVendorLocations",summary="List locations for a verified vendor")
    @SecurityRequirement(name="bearerAuth")
    public CollectionResponse<LocationResponse> locations(@RequestParam UUID vendorId,@RequestParam(defaultValue="20") int pageSize,
            @RequestParam(required=false) String cursor,HttpServletRequest request) { return collection(supply.vendorLocations(vendorId,pageSize,cursor),request); }
    @PostMapping(value="/vendor/locations",consumes="application/json")
    @Operation(operationId="createVendorLocation",summary="Vendor administrator creates an owned location")
    @SecurityRequirement(name="bearerAuth")
    public ResponseEntity<ApiResponse<LocationResponse>> createLocation(@RequestParam UUID vendorId,@Valid @RequestBody CreateLocationRequest body,HttpServletRequest request) {
        var location=supply.createLocation(vendorId,body);
        return ResponseEntity.created(URI.create("/api/v1/supply/vendor/locations/"+location.locationId())).body(response(location,request));
    }
    @PutMapping(value="/vendor/locations/{locationId}",consumes="application/json")
    @Operation(operationId="replaceVendorLocation",summary="Vendor administrator updates an owned location using its version")
    @SecurityRequirement(name="bearerAuth")
    public ApiResponse<LocationResponse> updateLocation(@PathVariable UUID locationId,@Valid @RequestBody UpdateLocationRequest body,HttpServletRequest request) {
        return response(supply.updateLocation(locationId,body),request);
    }
    private static <T> ApiResponse<T> response(T data,HttpServletRequest request) { return new ApiResponse<>(data,meta(request)); }
    private static <T> CollectionResponse<T> collection(SupplyService.Page<T> page,HttpServletRequest request) { return new CollectionResponse<>(page.data(),new CollectionResponse.Pagination(page.nextCursor(),page.hasNext()),meta(request)); }
    private static ApiResponse.Meta meta(HttpServletRequest request) { return new ApiResponse.Meta(SupplyProblems.requestId(request),Instant.now()); }
}
