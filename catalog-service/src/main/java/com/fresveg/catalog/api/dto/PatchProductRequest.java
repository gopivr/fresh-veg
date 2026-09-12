package com.fresveg.catalog.api.dto;
import java.util.*;
import java.math.BigDecimal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fresveg.catalog.domain.*;
public record PatchProductRequest(@NotNull @PositiveOrZero Long version, @NotNull ProductStatus status) { }
