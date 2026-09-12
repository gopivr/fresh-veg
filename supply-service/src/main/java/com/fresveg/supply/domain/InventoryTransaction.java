package com.fresveg.supply.domain;
import jakarta.persistence.*;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(name="inventory_transactions",schema="supply")
@AttributeOverride(name="id",column=@Column(name="transaction_id"))
public class InventoryTransaction extends AuditedEntity {
    @Column(name="inventory_id") private UUID inventoryId;
    @Column(name="batch_id") private UUID batchId;
    @Column(name="reservation_id") private UUID reservationId;
    @Column(name="kind",length=16) private String kind;
    @Column(name="quantity_delta",precision=18,scale=6) private BigDecimal quantityDelta;
    @Column(name="reserved_delta",precision=18,scale=6) private BigDecimal reservedDelta;
    @Column(name="reason",length=240) private String reason;
    public InventoryTransaction() { }
    public void initialize(UUID actor) { id=UUID.randomUUID();createdBy=actor;updatedBy=actor; }
    public void touch(UUID actor) { updatedBy=actor;updatedAt=Instant.now(); }
    public UUID getInventoryId() { return inventoryId; }
    public void setInventoryId(UUID value) { inventoryId=value; }
    public UUID getBatchId() { return batchId; }
    public void setBatchId(UUID value) { batchId=value; }
    public UUID getReservationId() { return reservationId; }
    public void setReservationId(UUID value) { reservationId=value; }
    public String getKind() { return kind; }
    public void setKind(String value) { kind=value; }
    public BigDecimal getQuantityDelta() { return quantityDelta; }
    public void setQuantityDelta(BigDecimal value) { quantityDelta=value; }
    public BigDecimal getReservedDelta() { return reservedDelta; }
    public void setReservedDelta(BigDecimal value) { reservedDelta=value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason=value; }
}
