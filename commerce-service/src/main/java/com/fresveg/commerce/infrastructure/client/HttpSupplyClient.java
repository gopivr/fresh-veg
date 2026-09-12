package com.fresveg.commerce.infrastructure.client;
import com.fresveg.commerce.application.SupplyClient;
import com.fresveg.commerce.api.dto.CartContracts.PriceResponse;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
@Component
public class HttpSupplyClient implements SupplyClient {
 private final OwnerHttp http;private final Clock clock;
 public HttpSupplyClient(OwnerHttp http,Clock clock) { this.http=http;this.clock=clock; }
 public Quote quote(UUID listing,String currency,BigDecimal quantity) {
  var data=http.listing(listing);if(data==null) return new Quote("UNAVAILABLE",null);
  try {
   if(!listing.toString().equals(data.path("listingId").asString()) || !"ACTIVE".equals(data.path("status").asString()) || !data.path("prices").isArray()) throw new IllegalArgumentException();
   BigDecimal minimum=positive(data.path("minimumOrderQuantity"));
   if(quantity.compareTo(minimum)<0) return new Quote("MINIMUM_NOT_MET",null);
   Instant now=clock.instant();PriceResponse resolved=null;
   for(var price:data.path("prices")) {
    if(!currency.equals(price.path("currency").asString())) continue;
    if(!"ACTIVE".equals(price.path("status").asString())) throw new IllegalArgumentException();
    Instant from=Instant.parse(price.path("validFrom").asString());var to=price.path("validTo");
    if(now.isBefore(from) || !to.isNull() && !now.isBefore(Instant.parse(to.asString()))) continue;
    if(resolved!=null || positive(price.path("minQuantity")).compareTo(minimum)!=0 || !price.path("tiers").isArray()) throw new IllegalArgumentException();
    UUID priceId=UUID.fromString(price.path("priceId").asString()),tierId=null;
    BigDecimal amount=positive(price.path("unitPrice")),threshold=minimum,previousAmount=amount;
    // Supply's contract orders tiers ascending; malformed/non-monotone owner data fails closed.
    for(var tier:price.path("tiers")) {
     BigDecimal next=positive(tier.path("minQuantity")),nextAmount=positive(tier.path("unitPrice"));
     if(next.compareTo(threshold)<=0 || nextAmount.compareTo(previousAmount)>0) throw new IllegalArgumentException();
     UUID id=UUID.fromString(tier.path("tierId").asString());
     if(quantity.compareTo(next)>=0) { amount=nextAmount;tierId=id; }
     threshold=next;previousAmount=nextAmount;
    }
    resolved=new PriceResponse(priceId,tierId,amount,now);
   }
   return new Quote(resolved==null?"NO_CURRENT_PRICE":"PRICED",resolved);
  } catch(Exception error) { throw OwnerHttp.unavailable(); }
 }
 private static BigDecimal positive(JsonNode value) { if(!value.isNumber()) throw new IllegalArgumentException();var n=value.decimalValue();if(n.signum()<=0 || n.scale()>6 || n.precision()-n.scale()>13) throw new IllegalArgumentException();return n; }
}
