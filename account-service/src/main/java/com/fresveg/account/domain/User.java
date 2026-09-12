package com.fresveg.account.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "users", schema = "account")
@AttributeOverride(name = "id", column = @Column(name = "user_id"))
public class User extends AuditedEntity {
    @Column(name = "oidc_issuer", nullable = false, length = 512)
    private String oidcIssuer;
    @Column(name = "oidc_subject", nullable = false, length = 255)
    private String oidcSubject;
    @Column(name = "display_name", nullable = true, length = 160)
    private String displayName;
    @Column(name = "email", nullable = true, length = 320)
    private String email;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    protected User() { }
    public String getOidcIssuer() { return oidcIssuer; }
    public String getOidcSubject() { return oidcSubject; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public AccountStatus getStatus() { return status; }
}
