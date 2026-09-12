package com.fresveg.catalog.infrastructure.persistence;
import com.fresveg.catalog.domain.ProductVariant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProductVariantRepository extends JpaRepository<ProductVariant,UUID> { List<ProductVariant> findByProductIdOrderByCodeAsc(UUID productId); }
