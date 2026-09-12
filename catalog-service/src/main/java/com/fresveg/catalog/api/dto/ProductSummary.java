package com.fresveg.catalog.api.dto;
import java.util.*;
import java.math.BigDecimal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fresveg.catalog.domain.*;
public record ProductSummary(UUID productId, String code, String name, boolean organic, ProductStatus status, long version) { }
