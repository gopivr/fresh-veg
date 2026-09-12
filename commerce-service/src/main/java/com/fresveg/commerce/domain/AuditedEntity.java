package com.fresveg.commerce.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
public abstract class AuditedEntity {
    @Id
    protected UUID id;
    @Column(name = "created_at", nullable = false, updatable = false)
    protected Instant createdAt;
    @Column(name = "created_by", updatable = false)
    protected UUID createdBy;
    @Column(name = "updated_at", nullable = false)
    protected Instant updatedAt;
    @Column(name = "updated_by")
    protected UUID updatedBy;
    @Version
    @Column(nullable = false)
    protected Long version;

    @PrePersist
    protected void created() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void updated() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public long getVersion() { return version == null ? 0 : version; }
}
