package com.fresveg.commerce.domain;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
@Entity @Table(name="cart_items",schema="commerce") @AttributeOverride(name="id",column=@Column(name="item_id"))
public class CartItem extends AuditedEntity {
 @Column(name="cart_id",nullable=false,updatable=false) private UUID cartId;
 @Column(name="listing_id",nullable=false,updatable=false) private UUID listingId;
 @Column(nullable=false,precision=18,scale=6) private BigDecimal quantity;
 protected CartItem() { }
 public CartItem(UUID cart,UUID listing,BigDecimal quantity,UUID actor) { id=UUID.randomUUID();cartId=cart;listingId=listing;this.quantity=quantity;createdBy=actor;updatedBy=actor; }
 public UUID getCartId() { return cartId; }
 public UUID getListingId() { return listingId; }
 public BigDecimal getQuantity() { return quantity; }
 public void quantity(BigDecimal value,UUID actor) { quantity=value;updatedBy=actor; }
}
