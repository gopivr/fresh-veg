package com.fresveg.commerce.application.order;
import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
class OrderAllocationTest {
 @Test void almostFullDiscountOnFiftyPennyLinesNeverMakesTheLastLineNegative() {
  var discount=new BigDecimal("0.49");var remaining=new BigDecimal("0.50");var penny=new BigDecimal("0.01");var sum=BigDecimal.ZERO;
  for(int i=0;i<50;i++) {var part=OrderService.allocate(discount,penny,remaining,2);assertThat(part).isBetween(BigDecimal.ZERO,penny);sum=sum.add(part);discount=discount.subtract(part);remaining=remaining.subtract(penny);}
  assertThat(sum).isEqualByComparingTo("0.49");assertThat(discount).isZero();
 }
 @Test void zeroRoundedSubtotalAndCurrencyMinorUnitsAreHandled() {
  assertThat(OrderService.allocate(new BigDecimal("2.50"),BigDecimal.ZERO,BigDecimal.ZERO,2)).isZero();
  assertThat(OrderService.allocate(new BigDecimal("10"),BigDecimal.ONE,new BigDecimal("3"),0)).isEqualByComparingTo("3");
  assertThat(OrderService.allocate(BigDecimal.ONE,BigDecimal.ONE,new BigDecimal("3"),3)).isEqualByComparingTo("0.333");
 }
}
