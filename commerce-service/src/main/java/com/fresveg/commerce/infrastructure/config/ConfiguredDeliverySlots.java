package com.fresveg.commerce.infrastructure.config;
import com.fresveg.commerce.application.checkout.*;
import com.fresveg.commerce.api.dto.CheckoutContracts.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
@Component
public class ConfiguredDeliverySlots implements DeliverySlotProvider {
 private final Map<UUID,Slot> slots=new HashMap<>();private final Clock clock;
 public ConfiguredDeliverySlots(@Value("${commerce.checkout.delivery-slots:[]}") String config,JsonMapper json,Clock clock) {
  this.clock=clock;
  try {
   if(config.length()>65536)throw new IllegalArgumentException();
   for(var slot:json.readValue(config,Slot[].class)) {
    int scale=Currency.getInstance(slot.currency()).getDefaultFractionDigits();
    if(slot.deliverySlotId()==null || slot.startsAt()==null || slot.endsAt()==null || !slot.endsAt().isAfter(slot.startsAt()) || slot.startsAt().getNano()%1000!=0 || slot.endsAt().getNano()%1000!=0 || !Set.of(Locale.getISOCountries()).contains(slot.countryCode()) || slot.postalCodes()==null || slot.postalCodes().isEmpty() || slot.postalCodes().stream().anyMatch(p->p==null || p.isBlank() || p.length()>20) || scale<0 || slot.deliveryFee()==null || slot.deliveryFee().signum()<0 || slot.deliveryFee().stripTrailingZeros().scale()>scale || slots.put(slot.deliverySlotId(),slot)!=null)throw new IllegalArgumentException();
   }
  }catch(Exception error){throw new IllegalArgumentException("Invalid checkout delivery-slot configuration");}
 }
 public DeliveryQuote resolve(UUID id,Address address,String currency) {
  if(slots.isEmpty())throw CheckoutErrors.unavailable("Delivery-slot provider is not configured.");
  var slot=slots.get(id);Instant now=clock.instant();
  if(slot==null || !slot.currency().equals(currency) || !slot.countryCode().equals(address.countryCode()) || !slot.postalCodes().contains(address.postalCode()) || !slot.startsAt().isAfter(now) || slot.endsAt().isAfter(now.plus(Duration.ofDays(30))))throw CheckoutErrors.conflict("Selected delivery slot is unavailable for this address or currency.");
  return new DeliveryQuote(id,slot.startsAt(),slot.endsAt(),currency,slot.deliveryFee(),"CONFIGURED");
 }
 public record Slot(UUID deliverySlotId,Instant startsAt,Instant endsAt,String countryCode,List<String> postalCodes,String currency,BigDecimal deliveryFee) { }
}
