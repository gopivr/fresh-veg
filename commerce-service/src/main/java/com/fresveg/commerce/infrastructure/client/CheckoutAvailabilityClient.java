package com.fresveg.commerce.infrastructure.client;
import com.fresveg.commerce.application.checkout.CheckoutErrors;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
@Component
public class CheckoutAvailabilityClient {
 private final OwnerHttp http;
 public CheckoutAvailabilityClient(OwnerHttp http) {this.http=http;}
 public Instant require(UUID listing,BigDecimal quantity,Instant until) {
  var result=http.availability(listing,quantity,until);if(result==null)throw CheckoutErrors.conflict("Listing is no longer available.");
  try {
   if(!listing.toString().equals(result.path("listingId").asString()) || !result.path("quantity").isNumber() || result.path("quantity").decimalValue().compareTo(quantity)!=0 || !Instant.parse(result.path("requiredUntil").asString()).equals(until) || !result.path("available").isBoolean())throw new IllegalArgumentException();
   Instant checkedAt=Instant.parse(result.path("checkedAt").asString());
   if(!result.path("available").asBoolean())throw CheckoutErrors.conflict("Insufficient eligible unreserved inventory for the delivery window.");
   return checkedAt;
  }catch(com.fresveg.commerce.application.CommerceException error){throw error;}catch(Exception error){throw OwnerHttp.unavailable();}
 }
}
