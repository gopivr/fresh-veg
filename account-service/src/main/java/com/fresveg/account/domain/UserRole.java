package com.fresveg.account.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "user_roles", schema = "account")
@AttributeOverride(name = "id", column = @Column(name = "user_role_id"))
public class UserRole extends AuditedEntity {
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    protected UserRole() { }
    public UUID getUserId() { return userId; }
    public UUID getRoleId() { return roleId; }
}
