package com.fresveg.catalog.application;

import com.fresveg.catalog.domain.ProductStatus;
import java.util.UUID;

public record CatalogQuery(String q, UUID categoryId, Boolean organic, ProductStatus status,
        String sort, int pageSize, String cursor, String attributes) { }
