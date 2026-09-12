package com.fresveg.supply.api.dto;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fresveg.supply.domain.*;
public record UpdateLocationRequest(@NotBlank @Pattern(regexp="[A-Z0-9][A-Z0-9_-]{0,63}") String code,
        @NotBlank @Size(max=160) String name,@NotBlank @Size(max=200) String line1,
        @NotBlank @Size(max=100) String city,@NotBlank @Size(max=20) String postalCode,
        @NotBlank @Pattern(regexp="[A-Z]{2}") String countryCode,@NotNull LocationStatus status, @NotNull @PositiveOrZero Long version) implements LocationInput { }
