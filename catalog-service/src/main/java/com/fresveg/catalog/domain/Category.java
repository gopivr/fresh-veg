package com.fresveg.catalog.domain;
import jakarta.persistence.*;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity
@Table(name="categories", schema="catalog")
@AttributeOverride(name="id", column=@Column(name="category_id"))
public class Category extends AuditedEntity {
    @Column(nullable=false,length=64)
    private String code;
    @Column(nullable=false,length=160)
    private String name;
    @Column(name="parent_category_id")
    private UUID parentCategoryId;
    protected Category() { }
    public String getCode() { return code; }
    public String getName() { return name; }
    public UUID getParentCategoryId() { return parentCategoryId; }

    public Category(String code,String name,UUID parent,UUID actor) {
        id=UUID.randomUUID(); this.code=code; this.name=name.strip(); parentCategoryId=parent; createdBy=actor; updatedBy=actor;
    }

}
