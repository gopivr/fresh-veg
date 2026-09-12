package com.fresveg.catalog.domain;
import jakarta.persistence.*;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity
@Table(name="products", schema="catalog")
@AttributeOverride(name="id", column=@Column(name="product_id"))
public class Product extends AuditedEntity {
    @Column(nullable=false,length=64)
    private String code;
    @Column(nullable=false,length=160)
    private String name;
    @Column(length=4000)
    private String description;
    @Column(nullable=false)
    private boolean organic;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16)
    private ProductStatus status;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb")
    private Map<String,Object> attributes;
    protected Product() { }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean getOrganic() { return organic; }
    public ProductStatus getStatus() { return status; }
    public Map<String,Object> getAttributes() { return attributes; }

    public Product(UUID actor) { id=UUID.randomUUID(); createdBy=actor; }
    public void replace(String code, String name, String description, boolean organic, ProductStatus status, Map<String,Object> attributes, UUID actor) {
        this.code=code; this.name=name.strip(); this.description=description==null?null:description.strip();
        this.organic=organic; this.status=status; this.attributes=new LinkedHashMap<>(attributes); touch(actor);
    }
    public void changeStatus(ProductStatus status, UUID actor) { this.status=status; touch(actor); }
    private void touch(UUID actor) { updatedBy=actor; updatedAt=Instant.now(); }

}
