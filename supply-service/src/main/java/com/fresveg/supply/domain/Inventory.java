package com.fresveg.supply.domain;
import jakarta.persistence.*;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(name="inventory",schema="supply")
@AttributeOverride(name="id",column=@Column(name="inventory_id"))
public class Inventory extends AuditedEntity {
    @Column(name="listing_id") private UUID listingId;
    @Column(name="quantity_on_hand",precision=18,scale=6) private BigDecimal quantityOnHand;
    @Column(name="reserved_quantity",precision=18,scale=6) private BigDecimal reservedQuantity;
    public Inventory() { }
    public void initialize(UUID actor) { id=UUID.randomUUID();createdBy=actor;updatedBy=actor; }
    public void touch(UUID actor) { updatedBy=actor;updatedAt=Instant.now(); }
    public UUID getListingId() { return listingId; }
    public void setListingId(UUID value) { listingId=value; }
    public BigDecimal getQuantityOnHand() { return quantityOnHand; }
    public void setQuantityOnHand(BigDecimal value) { quantityOnHand=value; }
    public BigDecimal getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(BigDecimal value) { reservedQuantity=value; }
}
