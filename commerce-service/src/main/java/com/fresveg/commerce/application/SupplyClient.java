package com.fresveg.commerce.application;
import com.fresveg.commerce.api.dto.CartContracts.PriceResponse;
import java.math.BigDecimal;
import java.util.UUID;
public interface SupplyClient {
 Quote quote(UUID listingId,String currency,BigDecimal quantity);
 record Quote(String status,PriceResponse price) { }
}
