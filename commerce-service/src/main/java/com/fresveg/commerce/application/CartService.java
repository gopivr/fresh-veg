package com.fresveg.commerce.application;
import com.fresveg.commerce.api.dto.CartContracts.*;
import com.fresveg.commerce.domain.*;
import com.fresveg.commerce.infrastructure.persistence.*;
import com.fresveg.commerce.infrastructure.security.CartIdentity;
import com.fresveg.commerce.infrastructure.client.OwnerHttp;
import java.util.*;
import java.math.BigDecimal;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service @Transactional @PreAuthorize("isAuthenticated()")
public class CartService {
 private final CartRepository carts;private final CartItemRepository items;private final CartIdentity identity;
 private final SupplyClient supply;private final CartMapper mapper;private final CartCursor cursors;
 public CartService(CartRepository carts,CartItemRepository items,CartIdentity identity,SupplyClient supply,CartMapper mapper,CartCursor cursors) {
  this.carts=carts;this.items=items;this.identity=identity;this.supply=supply;this.mapper=mapper;this.cursors=cursors;
 }
 public CartResponse create(CreateCartRequest request) {
  try { if(Currency.getInstance(request.currency()).getDefaultFractionDigits()<0) throw new IllegalArgumentException(); }
  catch(Exception error) { throw invalid("Supported ISO-4217 currency required."); }
  var customer=identity.current();var cart=carts.saveAndFlush(new Cart(customer.customerId(),customer.userId(),request.currency()));
  return new CartResponse(cart.getId(),cart.getCurrency(),cart.getVersion(),List.of(),new Pagination(null,false));
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public CartResponse read(UUID id,int pageSize,String cursor) {
  var customer=identity.current();var cart=owned(id,customer.customerId());
  if(pageSize<1 || pageSize>10) throw invalid("pageSize must be between 1 and 10.");
  String filter=cursors.fingerprint(List.of(id,customer.customerId(),cart.getVersion()));var position=cursors.decode(cursor,filter);
  var rows=items.page(id,position==null?null:position.id(),PageRequest.of(0,pageSize+1));boolean more=rows.size()>pageSize;
  var visible=rows.subList(0,Math.min(rows.size(),pageSize));var result=new ArrayList<ItemResponse>();long deadline=System.nanoTime()+java.time.Duration.ofSeconds(20).toNanos();
  for(var item:visible) { if(System.nanoTime()>deadline) throw OwnerHttp.unavailable();result.add(mapper.item(item,supply.quote(item.getListingId(),cart.getCurrency(),item.getQuantity()))); }
  return new CartResponse(id,cart.getCurrency(),cart.getVersion(),result,new Pagination(more?cursors.encode(filter,"",visible.getLast().getId()):null,more));
 }
 public MutationResponse add(UUID id,AddCartItemRequest request) {
  var customer=identity.current();var cart=owned(id,customer.customerId());version(cart,request.version());
  if(items.countByCartId(id)>=50) throw conflict("Cart supports at most 50 distinct listings.");
  validListing(request.listingId(),cart.getCurrency(),request.quantity());
  cart.touch(customer.userId());carts.flush();
  var item=items.saveAndFlush(new CartItem(id,request.listingId(),request.quantity(),customer.userId()));return new MutationResponse(id,cart.getVersion(),item.getId());
 }
 public MutationResponse update(UUID id,UUID itemId,UpdateCartItemRequest request) {
  var customer=identity.current();var cart=owned(id,customer.customerId());var item=items.findByIdAndCartId(itemId,id).orElseThrow(CartService::missing);version(cart,request.version());
  validListing(item.getListingId(),cart.getCurrency(),request.quantity());cart.touch(customer.userId());carts.flush();item.quantity(request.quantity(),customer.userId());items.flush();
  return new MutationResponse(id,cart.getVersion(),itemId);
 }
 public MutationResponse remove(UUID id,UUID itemId,long version) {
  var customer=identity.current();var cart=owned(id,customer.customerId());var item=items.findByIdAndCartId(itemId,id).orElseThrow(CartService::missing);version(cart,version);
  cart.touch(customer.userId());carts.flush();items.delete(item);items.flush();return new MutationResponse(id,cart.getVersion(),itemId);
 }
 private void validListing(UUID listing,String currency,BigDecimal quantity) { var quote=supply.quote(listing,currency,quantity);if(!quote.status().equals("PRICED")) throw conflict("Listing cannot currently be added at this currency and quantity."); }
 private Cart owned(UUID id,UUID customer) { return carts.findByIdAndCustomerId(id,customer).orElseThrow(CartService::missing); }
 private static void version(Cart c,long expected) { if(c.getVersion()!=expected) throw conflict("Cart changed; reload before editing."); }
 private static CommerceException missing() { return new CommerceException(HttpStatus.NOT_FOUND,"CRT-404-001","Owned resource not found."); }
 private static CommerceException invalid(String message) { return new CommerceException(HttpStatus.BAD_REQUEST,"CRT-400-001",message); }
 private static CommerceException conflict(String message) { return new CommerceException(HttpStatus.CONFLICT,"CRT-409-001",message); }
}
