package com.fresveg.supply.domain;
import jakarta.persistence.*;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity
@Table(name="price_tiers", schema="supply")
@AttributeOverride(name="id", column=@Column(name="tier_id"))
public class PriceTier extends AuditedEntity {
    @Column(name="price_id",nullable=false,updatable=false)
    private UUID priceId;
    @Column(name="min_quantity",nullable=false,precision=18,scale=6,updatable=false)
    private BigDecimal minQuantity;
    @Column(name="unit_price",nullable=false,precision=19,scale=6,updatable=false)
    private BigDecimal unitPrice;
    protected PriceTier() { }
    public UUID getPriceId() { return priceId; }
    public BigDecimal getMinQuantity() { return minQuantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }

    public PriceTier(UUID price,BigDecimal minimum,BigDecimal amount,UUID actor) { id=UUID.randomUUID();priceId=price;minQuantity=minimum;unitPrice=amount;createdBy=actor;updatedBy=actor; }

}
