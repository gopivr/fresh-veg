package com.fresveg.catalog.api.dto;
import java.util.*;
import java.math.BigDecimal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fresveg.catalog.domain.*;
public interface ProductInput {
    String code(); String name(); String description(); Boolean organic(); ProductStatus status();
    Map<String,Object> attributes(); List<UUID> categoryIds(); List<VariantRequest> variants(); List<ImageRequest> images();
}
