package com.fresveg.catalog.infrastructure.persistence;
import com.fresveg.catalog.domain.ProductCategory;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProductCategoryRepository extends JpaRepository<ProductCategory,UUID> { List<ProductCategory> findByProductIdOrderByCategoryIdAsc(UUID productId);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from ProductCategory r where r.productId=:productId")
    void removeForProduct(@org.springframework.data.repository.query.Param("productId") UUID productId); }
