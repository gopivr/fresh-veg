package com.fresveg.account.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "roles", schema = "account")
@AttributeOverride(name = "id", column = @Column(name = "role_id"))
public class Role extends AuditedEntity {
    @Column(name = "code", nullable = false, length = 40)
    private String code;
    @Column(name = "description", nullable = false, length = 200)
    private String description;

    protected Role() { }
    public String getCode() { return code; }
    public String getDescription() { return description; }
}
