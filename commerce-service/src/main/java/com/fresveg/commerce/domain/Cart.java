package com.fresveg.commerce.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="carts",schema="commerce") @AttributeOverride(name="id",column=@Column(name="cart_id"))
public class Cart extends AuditedEntity {
 @Column(name="customer_id",nullable=false,updatable=false) private UUID customerId;
 @Column(nullable=false,length=3,updatable=false) private String currency;
 protected Cart() { }
 public Cart(UUID customer,UUID actor,String currency) { id=UUID.randomUUID();customerId=customer;createdBy=actor;updatedBy=actor;this.currency=currency; }
 public UUID getCustomerId() { return customerId; }
 public String getCurrency() { return currency; }
 public void touch(UUID actor) { updatedBy=actor;updatedAt=Instant.now(); }
}
