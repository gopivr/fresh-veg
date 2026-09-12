package com.fresveg.commerce.application.checkout;
import com.fresveg.commerce.api.dto.CheckoutContracts.*;
import java.util.UUID;
public interface DeliverySlotProvider {
 DeliveryQuote resolve(UUID slotId,Address address,String currency);
}
