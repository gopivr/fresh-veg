package com.fresveg.catalog.api.dto;
import java.util.*;
import java.math.BigDecimal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fresveg.catalog.domain.*;
public record CreateProductRequest(@NotBlank @Pattern(regexp="[A-Z0-9][A-Z0-9_-]{0,63}") String code,
        @NotBlank @Size(max=160) String name, @Size(max=4000) String description,
        @NotNull Boolean organic, @NotNull ProductStatus status,
        @NotNull Map<String,Object> attributes, @NotNull @Size(max=50) List<@NotNull UUID> categoryIds,
        @NotNull @Size(max=50) List<@NotNull @Valid VariantRequest> variants,
        @NotNull @Size(max=20) List<@NotNull @Valid ImageRequest> images) implements ProductInput { }
