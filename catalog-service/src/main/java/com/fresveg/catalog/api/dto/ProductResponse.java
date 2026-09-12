package com.fresveg.catalog.api.dto;
import java.util.*;
import java.math.BigDecimal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fresveg.catalog.domain.*;
public record ProductResponse(UUID productId, String code, String name, String description, boolean organic, ProductStatus status, Map<String,Object> attributes, List<UUID> categoryIds, List<VariantResponse> variants, List<ImageResponse> images, long version) { }
