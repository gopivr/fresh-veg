package com.fresveg.account.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "vendors", schema = "account")
@AttributeOverride(name = "id", column = @Column(name = "vendor_id"))
public class Vendor extends AuditedEntity {
    @Column(name = "vendor_code", nullable = false, length = 64)
    private String vendorCode;
    @Column(name = "name", nullable = false, length = 160)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    protected Vendor() { }
    public String getVendorCode() { return vendorCode; }
    public String getName() { return name; }
    public AccountStatus getStatus() { return status; }
}
