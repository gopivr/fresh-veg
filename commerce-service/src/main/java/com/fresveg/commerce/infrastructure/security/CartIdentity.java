package com.fresveg.commerce.infrastructure.security;
import com.fresveg.commerce.infrastructure.client.OwnerHttp;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
@Component
public class CartIdentity {
 private final OwnerHttp http;
 public CartIdentity(OwnerHttp http) { this.http=http; }
 public Customer current() {
  var account=http.account();
  if(!account.path("status").isString()) throw OwnerHttp.unavailable();
  if(!"ACTIVE".equals(account.path("status").asString())) throw new AccessDeniedException("Active customer required");
  try { return new Customer(UUID.fromString(account.path("customerId").asString()),UUID.fromString(account.path("userId").asString())); }
  catch(Exception error) { throw OwnerHttp.unavailable(); }
 }
 public record Customer(UUID customerId,UUID userId) { }
}
