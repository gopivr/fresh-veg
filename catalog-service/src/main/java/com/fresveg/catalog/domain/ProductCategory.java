package com.fresveg.catalog.domain;
import jakarta.persistence.*;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity
@Table(name="product_categories", schema="catalog")
@AttributeOverride(name="id", column=@Column(name="product_category_id"))
public class ProductCategory extends AuditedEntity {
    @Column(name="product_id",nullable=false)
    private UUID productId;
    @Column(name="category_id",nullable=false)
    private UUID categoryId;
    protected ProductCategory() { }
    public UUID getProductId() { return productId; }
    public UUID getCategoryId() { return categoryId; }

    public ProductCategory(UUID productId,UUID categoryId,UUID actor) { id=UUID.randomUUID(); this.productId=productId; this.categoryId=categoryId; createdBy=actor; updatedBy=actor; }

}
