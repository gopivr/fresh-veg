package com.fresveg.commerce.application.checkout;
import com.fresveg.commerce.api.dto.CheckoutContracts.*;
import java.math.BigDecimal;
import java.util.List;
public interface CheckoutPricingPolicy {
 BigDecimal lineSubtotal(BigDecimal unitPrice,BigDecimal quantity,String currency);
 Totals calculate(List<BigDecimal> lineSubtotals,Address address,DeliveryQuote delivery,String currency);
}
