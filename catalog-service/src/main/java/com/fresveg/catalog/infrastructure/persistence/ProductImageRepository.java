package com.fresveg.catalog.infrastructure.persistence;
import com.fresveg.catalog.domain.ProductImage;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProductImageRepository extends JpaRepository<ProductImage,UUID> { List<ProductImage> findByProductIdOrderByPositionAsc(UUID productId);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from ProductImage r where r.productId=:productId")
    void removeForProduct(@org.springframework.data.repository.query.Param("productId") UUID productId); }
