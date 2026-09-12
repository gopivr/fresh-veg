package com.fresveg.supply.domain;
import jakarta.persistence.*;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(name="inventory_reservations",schema="supply")
@AttributeOverride(name="id",column=@Column(name="reservation_id"))
public class InventoryReservation extends AuditedEntity {
    @Column(name="inventory_id") private UUID inventoryId;
    @Column(name="client_id",length=160) private String clientId;
    @Column(name="external_reference",length=160) private String externalReference;
    @Column(name="quantity",precision=18,scale=6) private BigDecimal quantity;
    @Column(name="status",length=16) private String status;
    @Column(name="expires_at") private Instant expiresAt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="allocations",columnDefinition="jsonb") private Map<String,String> allocations;
    public InventoryReservation() { }
    public void initialize(UUID actor) { id=UUID.randomUUID();createdBy=actor;updatedBy=actor; }
    public void touch(UUID actor) { updatedBy=actor;updatedAt=Instant.now(); }
    public UUID getInventoryId() { return inventoryId; }
    public void setInventoryId(UUID value) { inventoryId=value; }
    public String getClientId() { return clientId; }
    public void setClientId(String value) { clientId=value; }
    public String getExternalReference() { return externalReference; }
    public void setExternalReference(String value) { externalReference=value; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal value) { quantity=value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status=value; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant value) { expiresAt=value; }
    public Map<String,String> getAllocations() { return allocations; }
    public void setAllocations(Map<String,String> value) { allocations=value; }
}
