package com.fresveg.supply.api.dto;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fresveg.supply.domain.*;
public record OfferResponse(UUID listingId,UUID vendorId,UUID productId,UUID variantId,String uomCode,BigDecimal minimumOrderQuantity,BigDecimal quantity,String currency,BigDecimal unitPrice,UUID priceId,UUID tierId,Instant validFrom,Instant validTo,Instant evaluatedAt,long listingVersion) { }
