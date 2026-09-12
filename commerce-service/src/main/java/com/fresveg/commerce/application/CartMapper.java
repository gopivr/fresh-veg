package com.fresveg.commerce.application;
import com.fresveg.commerce.domain.CartItem;
import com.fresveg.commerce.api.dto.CartContracts.ItemResponse;
import org.springframework.stereotype.Component;
@Component public class CartMapper {
 public ItemResponse item(CartItem item,SupplyClient.Quote quote) { return new ItemResponse(item.getId(),item.getListingId(),item.getQuantity(),quote.status(),quote.price()); }
}
