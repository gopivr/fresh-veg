package com.fresveg.supply.api.dto;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fresveg.supply.domain.*;
public record UpdateListingRequest(@NotNull UUID locationId, @NotNull UUID productId, @NotNull UUID variantId,
        @NotBlank @Pattern(regexp="[A-Z0-9][A-Z0-9_-]{0,63}") String vendorSku,
        @NotBlank @Size(max=16) String uomCode, @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=12,fraction=6) BigDecimal minimumOrderQuantity,
        @NotNull ListingStatus status, @NotNull Map<String,Object> attributes,
        @NotNull @Size(max=50) List<@NotNull @Valid PriceRequest> prices, @NotNull @PositiveOrZero Long version) implements ListingInput { }
