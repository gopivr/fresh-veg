package com.fresveg.commerce.infrastructure.security;
import com.fresveg.commerce.infrastructure.client.OwnerHttp;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
@Component
public class OrderVendorIdentity {
 private final OwnerHttp http;private final CartIdentity identities;
 public OrderVendorIdentity(OwnerHttp http,CartIdentity identities) {this.http=http;this.identities=identities;}
 public UUID require(UUID vendor,boolean write) {
  UUID actor=identities.current().userId(),cursor=null;var seen=new HashSet<UUID>();long deadline=System.nanoTime()+java.time.Duration.ofSeconds(20).toNanos();
  for(int page=0;page<100;page++) {
   if(System.nanoTime()>deadline)throw OwnerHttp.unavailable();var result=http.memberships(cursor);
   if(!result.path("data").isArray())throw OwnerHttp.unavailable();
   for(var m:result.path("data"))if(vendor.toString().equals(m.path("vendorId").asString())) {
    String role=m.path("role").asString();if(role.equals("VENDOR_ADMIN") || !write && role.equals("VENDOR_STAFF"))return actor;
    throw new AccessDeniedException("Vendor administrator required");
   }
   var pagination=result.path("pagination");if(!pagination.path("hasNext").isBoolean())throw OwnerHttp.unavailable();if(!pagination.path("hasNext").asBoolean())throw new AccessDeniedException("Active vendor membership required");
   try{cursor=UUID.fromString(pagination.path("nextCursor").asString());if(!seen.add(cursor))throw new IllegalStateException();}catch(Exception e){throw OwnerHttp.unavailable();}
  }
  throw OwnerHttp.unavailable();
 }
}
