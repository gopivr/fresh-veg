package com.fresveg.supply.domain;
import jakarta.persistence.*;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity
@Table(name="vendor_listing_prices", schema="supply")
@AttributeOverride(name="id", column=@Column(name="price_id"))
public class ListingPrice extends AuditedEntity {
    @Column(name="listing_id",nullable=false,updatable=false)
    private UUID listingId;
    @Column(nullable=false,length=3,updatable=false)
    private String currency;
    @Column(name="unit_price",nullable=false,precision=19,scale=6,updatable=false)
    private BigDecimal unitPrice;
    @Column(name="min_quantity",nullable=false,precision=18,scale=6,updatable=false)
    private BigDecimal minQuantity;
    @Column(name="valid_from",nullable=false,updatable=false)
    private Instant validFrom;
    @Column(name="valid_to",updatable=false)
    private Instant validTo;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16)
    private PriceStatus status;
    protected ListingPrice() { }
    public UUID getListingId() { return listingId; }
    public String getCurrency() { return currency; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getMinQuantity() { return minQuantity; }
    public Instant getValidFrom() { return validFrom; }
    public Instant getValidTo() { return validTo; }
    public PriceStatus getStatus() { return status; }

    public ListingPrice(UUID listing,String currency,BigDecimal amount,BigDecimal minimum,Instant from,Instant to,UUID actor) {
        id=UUID.randomUUID();listingId=listing;this.currency=currency;unitPrice=amount;minQuantity=minimum;validFrom=from;validTo=to;
        status=PriceStatus.ACTIVE;createdBy=actor;updatedBy=actor;
    }
    public void cancel(UUID actor) { status=PriceStatus.CANCELLED;updatedBy=actor;updatedAt=Instant.now(); }

}
