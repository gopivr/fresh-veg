package com.fresveg.account.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "addresses", schema = "account")
@AttributeOverride(name = "id", column = @Column(name = "address_id"))
public class Address extends AuditedEntity {
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;
    @Column(name = "label", length = 50, nullable = true)
    private String label;
    @Column(name = "recipient_name", length = 120, nullable = false)
    private String recipientName;
    @Column(name = "line1", length = 200, nullable = false)
    private String line1;
    @Column(name = "line2", length = 200, nullable = true)
    private String line2;
    @Column(name = "city", length = 100, nullable = false)
    private String city;
    @Column(name = "region", length = 100, nullable = true)
    private String region;
    @Column(name = "postal_code", length = 20, nullable = false)
    private String postalCode;
    @Column(name = "country_code", length = 2, nullable = false)
    private String countryCode;
    @Column(name = "phone", length = 32, nullable = true)
    private String phone;

    protected Address() { }

    public Address(UUID customerId, UUID actor) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.createdBy = actor;
        this.updatedBy = actor;
    }

    public void replace(String label, String recipientName, String line1, String line2,
            String city, String region, String postalCode, String countryCode, String phone, UUID actor) {
        this.label = clean(label);
        this.recipientName = clean(recipientName);
        this.line1 = clean(line1);
        this.line2 = clean(line2);
        this.city = clean(city);
        this.region = clean(region);
        this.postalCode = clean(postalCode);
        this.countryCode = clean(countryCode);
        this.phone = clean(phone);
        this.updatedBy = actor;
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public UUID getCustomerId() { return customerId; }
    public String getLabel() { return label; }
    public String getRecipientName() { return recipientName; }
    public String getLine1() { return line1; }
    public String getLine2() { return line2; }
    public String getCity() { return city; }
    public String getRegion() { return region; }
    public String getPostalCode() { return postalCode; }
    public String getCountryCode() { return countryCode; }
    public String getPhone() { return phone; }
}
