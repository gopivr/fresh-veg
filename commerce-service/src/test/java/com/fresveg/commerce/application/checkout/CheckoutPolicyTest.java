package com.fresveg.commerce.application.checkout;
import static org.assertj.core.api.Assertions.*;
import com.fresveg.commerce.api.dto.CheckoutContracts.*;
import com.fresveg.commerce.infrastructure.config.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
class CheckoutPolicyTest {
 private final JsonMapper json=new JsonMapper();
 private static Address address(String country) {return new Address(UUID.randomUUID(),"Recipient","Road",null,"Boston",null,"02110",country,null,0);}
 private static DeliveryQuote delivery(String currency,String fee) {return new DeliveryQuote(UUID.randomUUID(),Instant.now().plusSeconds(3600),Instant.now().plusSeconds(7200),currency,new BigDecimal(fee),"TEST");}
 private ConfiguredCheckoutPricing policy(String currency,String discount,String tax,boolean taxDelivery) {return new ConfiguredCheckoutPricing("[{\"policyId\":\"test-policy\",\"countryCode\":\"US\",\"currency\":\""+currency+"\",\"discountRate\":"+discount+",\"taxRate\":"+tax+",\"taxDelivery\":"+taxDelivery+"}]",json);}
 @Test void lineRoundingThenDiscountThenTaxReconcilesExactly() {
  var p=policy("USD","0.1","0.075",false);var line=p.lineSubtotal(new BigDecimal("0.335"),BigDecimal.ONE,"USD");assertThat(line).isEqualByComparingTo("0.34");
  var totals=p.calculate(List.of(line,line,line),address("US"),delivery("USD","2.50"),"USD");
  assertThat(totals.subtotal()).isEqualByComparingTo("1.02");assertThat(totals.discount()).isEqualByComparingTo("0.10");assertThat(totals.tax()).isEqualByComparingTo("0.07");assertThat(totals.grandTotal()).isEqualByComparingTo("3.49");
 }
 @Test void configuredDeliveryTaxAndCurrencyMinorUnitsAreRespected() {
  var p=policy("USD","0","0.1",true);assertThat(p.calculate(List.of(new BigDecimal("10.00")),address("US"),delivery("USD","2.50"),"USD").tax()).isEqualByComparingTo("1.25");
  assertThat(policy("JPY","0","0",false).lineSubtotal(new BigDecimal("2.5"),BigDecimal.ONE,"JPY")).isEqualByComparingTo("3");
  assertThat(policy("KWD","0","0",false).lineSubtotal(new BigDecimal("2.1235"),BigDecimal.ONE,"KWD")).isEqualByComparingTo("2.124");
 }
 @Test void missingOrInvalidPolicyDoesNotInventZeroTax() {
  assertThatThrownBy(()->new ConfiguredCheckoutPricing("[]",json).calculate(List.of(BigDecimal.ONE),address("US"),delivery("USD","0"),"USD")).hasMessageContaining("No checkout pricing policy");
  assertThatThrownBy(()->policy("USD","1.1","0",false)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->policy("USD","0","-0.1",false)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->policy("USD","0","0",false).calculate(List.of(BigDecimal.ONE),address("CA"),delivery("USD","0"),"USD")).hasMessageContaining("No checkout pricing policy");
 }
 @Test void configuredSlotRequiresAddressCurrencyAndFutureWindow() {
  Instant now=Instant.parse("2026-09-09T12:00:00Z");UUID id=UUID.randomUUID();
  String config="[{\"deliverySlotId\":\""+id+"\",\"startsAt\":\"2026-09-10T12:00:00Z\",\"endsAt\":\"2026-09-10T14:00:00Z\",\"countryCode\":\"US\",\"postalCodes\":[\"02110\"],\"currency\":\"USD\",\"deliveryFee\":2.50}]";
  var slots=new ConfiguredDeliverySlots(config,json,Clock.fixed(now,ZoneOffset.UTC));assertThat(slots.resolve(id,address("US"),"USD").deliveryFee()).isEqualByComparingTo("2.50");
  assertThatThrownBy(()->slots.resolve(id,address("CA"),"USD")).hasMessageContaining("unavailable");
  assertThatThrownBy(()->slots.resolve(id,address("US"),"EUR")).hasMessageContaining("unavailable");
  assertThatThrownBy(()->new ConfiguredDeliverySlots(config,json,Clock.fixed(now.plusSeconds(86400),ZoneOffset.UTC)).resolve(id,address("US"),"USD")).hasMessageContaining("unavailable");
  assertThatThrownBy(()->new ConfiguredDeliverySlots("[]",json,Clock.systemUTC()).resolve(id,address("US"),"USD")).hasMessageContaining("not configured");
 }
}
