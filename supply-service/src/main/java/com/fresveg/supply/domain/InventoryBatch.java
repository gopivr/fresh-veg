package com.fresveg.supply.domain;
import jakarta.persistence.*;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(name="inventory_batches",schema="supply")
@AttributeOverride(name="id",column=@Column(name="batch_id"))
public class InventoryBatch extends AuditedEntity {
    @Column(name="inventory_id") private UUID inventoryId;
    @Column(name="batch_number",length=64) private String batchNumber;
    @Column(name="harvest_date") private LocalDate harvestDate;
    @Column(name="received_date") private LocalDate receivedDate;
    @Column(name="best_before_date") private LocalDate bestBeforeDate;
    @Column(name="expiry_date") private LocalDate expiryDate;
    @Column(name="origin",length=160) private String origin;
    @Column(name="grade",length=64) private String grade;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="certification_data",columnDefinition="jsonb") private Map<String,String> certificationData;
    @Column(name="quantity_received",precision=18,scale=6) private BigDecimal quantityReceived;
    @Column(name="quantity_remaining",precision=18,scale=6) private BigDecimal quantityRemaining;
    @Column(name="reserved_quantity",precision=18,scale=6) private BigDecimal reservedQuantity;
    @Column(name="status",length=16) private String status;
    public InventoryBatch() { }
    public void initialize(UUID actor) { id=UUID.randomUUID();createdBy=actor;updatedBy=actor; }
    public void touch(UUID actor) { updatedBy=actor;updatedAt=Instant.now(); }
    public UUID getInventoryId() { return inventoryId; }
    public void setInventoryId(UUID value) { inventoryId=value; }
    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String value) { batchNumber=value; }
    public LocalDate getHarvestDate() { return harvestDate; }
    public void setHarvestDate(LocalDate value) { harvestDate=value; }
    public LocalDate getReceivedDate() { return receivedDate; }
    public void setReceivedDate(LocalDate value) { receivedDate=value; }
    public LocalDate getBestBeforeDate() { return bestBeforeDate; }
    public void setBestBeforeDate(LocalDate value) { bestBeforeDate=value; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate value) { expiryDate=value; }
    public String getOrigin() { return origin; }
    public void setOrigin(String value) { origin=value; }
    public String getGrade() { return grade; }
    public void setGrade(String value) { grade=value; }
    public Map<String,String> getCertificationData() { return certificationData; }
    public void setCertificationData(Map<String,String> value) { certificationData=value; }
    public BigDecimal getQuantityReceived() { return quantityReceived; }
    public void setQuantityReceived(BigDecimal value) { quantityReceived=value; }
    public BigDecimal getQuantityRemaining() { return quantityRemaining; }
    public void setQuantityRemaining(BigDecimal value) { quantityRemaining=value; }
    public BigDecimal getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(BigDecimal value) { reservedQuantity=value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status=value; }
}
