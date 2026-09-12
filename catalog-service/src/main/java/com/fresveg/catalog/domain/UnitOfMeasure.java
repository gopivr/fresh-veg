package com.fresveg.catalog.domain;
import jakarta.persistence.*;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity
@Table(name="units_of_measure", schema="catalog")
@AttributeOverride(name="id", column=@Column(name="unit_id"))
public class UnitOfMeasure extends AuditedEntity {
    @Column(nullable=false,length=16)
    private String code;
    @Column(nullable=false,length=80)
    private String name;
    @Column(nullable=false,length=16)
    private String dimension;
    protected UnitOfMeasure() { }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDimension() { return dimension; }

}
