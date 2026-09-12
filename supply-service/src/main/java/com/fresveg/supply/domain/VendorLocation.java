package com.fresveg.supply.domain;
import jakarta.persistence.*;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity
@Table(name="vendor_locations", schema="supply")
@AttributeOverride(name="id", column=@Column(name="location_id"))
public class VendorLocation extends AuditedEntity {
    @Column(name="vendor_id",nullable=false,updatable=false)
    private UUID vendorId;
    @Column(nullable=false,length=64)
    private String code;
    @Column(nullable=false,length=160)
    private String name;
    @Column(nullable=false,length=200)
    private String line1;
    @Column(nullable=false,length=100)
    private String city;
    @Column(name="postal_code",nullable=false,length=20)
    private String postalCode;
    @Column(name="country_code",nullable=false,length=2)
    private String countryCode;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16)
    private LocationStatus status;
    protected VendorLocation() { }
    public UUID getVendorId() { return vendorId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getLine1() { return line1; }
    public String getCity() { return city; }
    public String getPostalCode() { return postalCode; }
    public String getCountryCode() { return countryCode; }
    public LocationStatus getStatus() { return status; }

    public VendorLocation(UUID vendor, UUID actor) { id=UUID.randomUUID(); vendorId=vendor; createdBy=actor; }
    public void replace(String code,String name,String line1,String city,String postal,String country,LocationStatus status,UUID actor) {
        this.code=code;this.name=name.strip();this.line1=line1.strip();this.city=city.strip();postalCode=postal.strip();countryCode=country;this.status=status;
        updatedBy=actor;updatedAt=Instant.now();
    }

}
