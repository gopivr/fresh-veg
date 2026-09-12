package com.fresveg.commerce.infrastructure.config;
import com.fresveg.commerce.application.checkout.*;
import com.fresveg.commerce.api.dto.CheckoutContracts.*;
import java.math.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
@Component
public class ConfiguredCheckoutPricing implements CheckoutPricingPolicy {
 private final Map<String,Rule> rules=new HashMap<>();
 public ConfiguredCheckoutPricing(@Value("${commerce.checkout.pricing-rules:[]}") String config,JsonMapper json) {
  try {
   if(config.length()>65536)throw new IllegalArgumentException();
   for(var rule:json.readValue(config,Rule[].class)) {
    if(rule.policyId()==null || rule.policyId().isBlank() || rule.policyId().length()>100 || !Set.of(Locale.getISOCountries()).contains(rule.countryCode()) || Currency.getInstance(rule.currency()).getDefaultFractionDigits()<0 || !rate(rule.discountRate()) || !rate(rule.taxRate()) || rule.taxDelivery()==null || rules.put(rule.countryCode()+":"+rule.currency(),rule)!=null)throw new IllegalArgumentException();
   }
  }catch(Exception error){throw new IllegalArgumentException("Invalid checkout pricing-rule configuration");}
 }
 public BigDecimal lineSubtotal(BigDecimal unitPrice,BigDecimal quantity,String currency) {return money(unitPrice.multiply(quantity),currency);}
 public Totals calculate(List<BigDecimal> lines,Address address,DeliveryQuote delivery,String currency) {
  var rule=rules.get(address.countryCode()+":"+currency);if(rule==null)throw CheckoutErrors.unavailable("No checkout pricing policy is configured for this destination and currency.");
  if(!currency.equals(delivery.currency()) || delivery.deliveryFee().signum()<0)throw CheckoutErrors.unavailable("Delivery pricing does not match cart currency.");
  BigDecimal subtotal=lines.stream().reduce(BigDecimal.ZERO,BigDecimal::add),fee=money(delivery.deliveryFee(),currency);
  BigDecimal discount=money(subtotal.multiply(rule.discountRate()),currency);
  BigDecimal taxable=subtotal.subtract(discount).add(rule.taxDelivery()?fee:BigDecimal.ZERO);
  BigDecimal tax=money(taxable.multiply(rule.taxRate()),currency);
  return new Totals(money(subtotal,currency),discount,tax,fee,money(subtotal.subtract(discount).add(tax).add(fee),currency),rule.policyId());
 }
 private static boolean rate(BigDecimal n) {return n!=null && n.signum()>=0 && n.compareTo(BigDecimal.ONE)<=0 && n.scale()<=6;}
 private static BigDecimal money(BigDecimal amount,String currency) {return amount.setScale(Currency.getInstance(currency).getDefaultFractionDigits(),RoundingMode.HALF_UP);}
 public record Rule(String policyId,String countryCode,String currency,BigDecimal discountRate,BigDecimal taxRate,Boolean taxDelivery) { }
}
