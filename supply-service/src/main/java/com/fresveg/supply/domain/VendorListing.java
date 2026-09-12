package com.fresveg.supply.domain;
import jakarta.persistence.*;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity
@Table(name="vendor_listings", schema="supply")
@AttributeOverride(name="id", column=@Column(name="listing_id"))
public class VendorListing extends AuditedEntity {
    @Column(name="vendor_id",nullable=false,updatable=false)
    private UUID vendorId;
    @Column(name="location_id",nullable=false,updatable=false)
    private UUID locationId;
    @Column(name="product_id",nullable=false,updatable=false)
    private UUID productId;
    @Column(name="variant_id",nullable=false,updatable=false)
    private UUID variantId;
    @Column(name="vendor_sku",nullable=false,length=64)
    private String vendorSku;
    @Column(name="uom_code",nullable=false,length=16,updatable=false)
    private String uomCode;
    @Column(name="minimum_order_quantity",nullable=false,precision=18,scale=6)
    private BigDecimal minimumOrderQuantity;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16)
    private ListingStatus status;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb")
    private Map<String,Object> attributes;
    protected VendorListing() { }
    public UUID getVendorId() { return vendorId; }
    public UUID getLocationId() { return locationId; }
    public UUID getProductId() { return productId; }
    public UUID getVariantId() { return variantId; }
    public String getVendorSku() { return vendorSku; }
    public String getUomCode() { return uomCode; }
    public BigDecimal getMinimumOrderQuantity() { return minimumOrderQuantity; }
    public ListingStatus getStatus() { return status; }
    public Map<String,Object> getAttributes() { return attributes; }

    public VendorListing(UUID vendor,UUID location,UUID product,UUID variant,String uom,UUID actor) {
        id=UUID.randomUUID();vendorId=vendor;locationId=location;productId=product;variantId=variant;uomCode=uom;createdBy=actor;
    }
    public void replace(String sku,BigDecimal minimum,ListingStatus status,Map<String,Object> attributes,UUID actor) {
        vendorSku=sku;minimumOrderQuantity=minimum;this.status=status;this.attributes=new LinkedHashMap<>(attributes);updatedBy=actor;updatedAt=Instant.now();
    }

}
