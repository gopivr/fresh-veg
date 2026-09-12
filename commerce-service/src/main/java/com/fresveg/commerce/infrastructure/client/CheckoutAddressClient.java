package com.fresveg.commerce.infrastructure.client;
import com.fresveg.commerce.api.dto.CheckoutContracts.Address;
import com.fresveg.commerce.application.checkout.CheckoutErrors;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
@Component
public class CheckoutAddressClient {
 private final OwnerHttp http;
 public CheckoutAddressClient(OwnerHttp http) { this.http=http; }
 public Address owned(UUID addressId,long deadline) {
  UUID cursor=null;var seen=new HashSet<UUID>();
  for(int page=0;page<100;page++) {
   if(System.nanoTime()>deadline)throw OwnerHttp.unavailable();
   var result=http.addresses(cursor);
   try {
    if(!result.path("data").isArray() || !result.path("pagination").path("hasNext").isBoolean())throw new IllegalArgumentException();
    for(var item:result.path("data")) {
     UUID id=UUID.fromString(item.path("addressId").asString());if(!addressId.equals(id))continue;
     if(!item.path("version").isIntegralNumber() || item.path("version").asLong()<0)throw new IllegalArgumentException();
     String country=required(item,"countryCode",2);if(!Set.of(Locale.getISOCountries()).contains(country))throw new IllegalArgumentException();
     return new Address(id,required(item,"recipientName",120),required(item,"line1",200),optional(item,"line2",200),required(item,"city",100),optional(item,"region",100),required(item,"postalCode",20),country,optional(item,"phone",32),item.path("version").asLong());
    }
    if(!result.path("pagination").path("hasNext").asBoolean())throw CheckoutErrors.missing();
    cursor=UUID.fromString(result.path("pagination").path("nextCursor").asString());if(!seen.add(cursor))throw new IllegalArgumentException();
   } catch(com.fresveg.commerce.application.CommerceException error) {throw error;}
   catch(Exception error) {throw OwnerHttp.unavailable();}
  }
  throw OwnerHttp.unavailable();
 }
 private static String required(JsonNode row,String field,int max) {var value=row.path(field);if(!value.isString() || value.asString().isBlank() || value.asString().length()>max)throw new IllegalArgumentException();return value.asString();}
 private static String optional(JsonNode row,String field,int max) {var value=row.path(field);if(value.isNull())return null;if(!value.isString() || value.asString().length()>max)throw new IllegalArgumentException();return value.asString();}
}
