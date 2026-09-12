package com.fresveg.commerce.api.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
public final class CartContracts {
 private CartContracts() { }
 public record CreateCartRequest(@NotNull @Pattern(regexp="[A-Z]{3}") String currency) { }
 public record AddCartItemRequest(@NotNull @PositiveOrZero Long version,@NotNull UUID listingId,@NotNull @Positive @Digits(integer=12,fraction=6) BigDecimal quantity) { }
 public record UpdateCartItemRequest(@NotNull @PositiveOrZero Long version,@NotNull @Positive @Digits(integer=12,fraction=6) BigDecimal quantity) { }
 public record CartResponse(UUID cartId,String currency,long version,List<ItemResponse> items,Pagination pagination) { }
 public record ItemResponse(UUID itemId,UUID listingId,BigDecimal quantity,String pricingStatus,PriceResponse currentPrice) { }
 public record PriceResponse(UUID priceId,UUID tierId,BigDecimal unitPrice,Instant fetchedAt) { }
 public record Pagination(String nextCursor,boolean hasNext) { }
 public record MutationResponse(UUID cartId,long version,UUID itemId) { }
}
