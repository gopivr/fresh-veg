package com.fresveg.supply.api.dto;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fresveg.supply.domain.*;
public record TierRequest(@NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=12,fraction=6) BigDecimal minQuantity,@NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=13,fraction=6) BigDecimal unitPrice) { }
