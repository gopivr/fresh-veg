package com.fresveg.supply.api.dto;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fresveg.supply.domain.*;
public record PriceResponse(UUID priceId,String currency,BigDecimal unitPrice,BigDecimal minQuantity,Instant validFrom,Instant validTo,PriceStatus status,List<TierResponse> tiers) { }
