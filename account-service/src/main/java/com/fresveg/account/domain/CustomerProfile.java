package com.fresveg.account.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "customer_profiles", schema = "account")
@AttributeOverride(name = "id", column = @Column(name = "customer_id"))
public class CustomerProfile extends AuditedEntity {
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "full_name", nullable = true, length = 160)
    private String fullName;
    @Column(name = "contact_phone", nullable = true, length = 32)
    private String contactPhone;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    protected CustomerProfile() { }
    public UUID getUserId() { return userId; }
    public String getFullName() { return fullName; }
    public String getContactPhone() { return contactPhone; }
    public AccountStatus getStatus() { return status; }
}
