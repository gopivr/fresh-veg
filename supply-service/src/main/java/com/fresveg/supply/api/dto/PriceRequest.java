package com.fresveg.supply.api.dto;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fresveg.supply.domain.*;
public record PriceRequest(UUID priceId,@NotBlank @Pattern(regexp="[A-Z]{3}") String currency,@NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=13,fraction=6) BigDecimal unitPrice,@NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=12,fraction=6) BigDecimal minQuantity,@NotNull Instant validFrom,Instant validTo,@NotNull @Size(max=50) List<@NotNull @Valid TierRequest> tiers) { }
