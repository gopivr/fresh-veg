package com.fresveg.supply.api.dto;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fresveg.supply.domain.*;
public record ListingSummary(UUID listingId,UUID vendorId,UUID locationId,UUID productId,UUID variantId,String vendorSku,String uomCode,BigDecimal minimumOrderQuantity,ListingStatus status,long version) { }
