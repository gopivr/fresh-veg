package com.fresveg.catalog.domain;
import jakarta.persistence.*;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity
@Table(name="product_images", schema="catalog")
@AttributeOverride(name="id", column=@Column(name="image_id"))
public class ProductImage extends AuditedEntity {
    @Column(name="product_id",nullable=false)
    private UUID productId;
    @Column(nullable=false,length=2048)
    private String url;
    @Column(name="alt_text",nullable=false,length=240)
    private String altText;
    @Column(nullable=false)
    private int position;
    protected ProductImage() { }
    public UUID getProductId() { return productId; }
    public String getUrl() { return url; }
    public String getAltText() { return altText; }
    public int getPosition() { return position; }

    public ProductImage(UUID productId,String url,String altText,int position,UUID actor) { id=UUID.randomUUID(); this.productId=productId; this.url=url; this.altText=altText.strip(); this.position=position; createdBy=actor; updatedBy=actor; }

}
