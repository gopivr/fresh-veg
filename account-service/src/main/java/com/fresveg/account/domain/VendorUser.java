package com.fresveg.account.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "vendor_users", schema = "account")
@AttributeOverride(name = "id", column = @Column(name = "vendor_user_id"))
public class VendorUser extends AuditedEntity {
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(name = "membership_role", nullable = false, length = 20)
    private MembershipRole membershipRole;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MembershipStatus status;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    protected VendorUser() { }
    public UUID getUserId() { return userId; }
    public MembershipRole getMembershipRole() { return membershipRole; }
    public MembershipStatus getStatus() { return status; }
    public Vendor getVendor() { return vendor; }
}
