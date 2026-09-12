package com.fresveg.catalog.application;

import com.fresveg.catalog.api.dto.*;
import com.fresveg.catalog.domain.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class CatalogMapper {
    public ProductSummary summary(Product p) { return new ProductSummary(p.getId(),p.getCode(),p.getName(),p.getOrganic(),p.getStatus(),p.getVersion()); }
    public CategoryResponse category(Category c) { return new CategoryResponse(c.getId(),c.getCode(),c.getName(),c.getParentCategoryId(),c.getVersion()); }
    public ProductResponse product(Product p,List<UUID> categories,List<ProductVariant> variants,Map<UUID,String> units,List<ProductImage> images) {
        return new ProductResponse(p.getId(),p.getCode(),p.getName(),p.getDescription(),p.getOrganic(),p.getStatus(),
                p.getAttributes(),categories,variants.stream().map(v -> new VariantResponse(v.getId(),v.getCode(),v.getName(),units.get(v.getUnitId()),v.getQuantity(),v.getStatus())).toList(),
                images.stream().map(i -> new ImageResponse(i.getId(),i.getUrl(),i.getAltText(),i.getPosition())).toList(),p.getVersion());
    }
}
