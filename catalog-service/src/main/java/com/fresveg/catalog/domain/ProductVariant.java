package com.fresveg.catalog.domain;
import jakarta.persistence.*;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity
@Table(name="product_variants", schema="catalog")
@AttributeOverride(name="id", column=@Column(name="variant_id"))
public class ProductVariant extends AuditedEntity {
    @Column(name="product_id",nullable=false)
    private UUID productId;
    @Column(nullable=false,length=64)
    private String code;
    @Column(nullable=false,length=160)
    private String name;
    @Column(name="unit_id",nullable=false)
    private UUID unitId;
    @Column(nullable=false,precision=18,scale=6)
    private BigDecimal quantity;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16)
    private VariantStatus status;
    protected ProductVariant() { }
    public UUID getProductId() { return productId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public UUID getUnitId() { return unitId; }
    public BigDecimal getQuantity() { return quantity; }
    public VariantStatus getStatus() { return status; }

    public ProductVariant(UUID productId,String code,UUID actor) { id=UUID.randomUUID(); this.productId=productId; this.code=code; createdBy=actor; }
    public void replace(String name,UUID unitId,BigDecimal quantity,VariantStatus status,UUID actor) {
        this.name=name.strip(); this.unitId=unitId; this.quantity=quantity; this.status=status; updatedBy=actor;
    }
    public void archive(UUID actor) { status=VariantStatus.ARCHIVED; updatedBy=actor; }

}
