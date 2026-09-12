package com.fresveg.commerce.application.order;
import com.fresveg.commerce.api.dto.OrderContracts.*;
import com.fresveg.commerce.infrastructure.persistence.OrderRepository;
import com.fresveg.commerce.infrastructure.security.OrderVendorIdentity;
import java.time.Clock;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
@Service
public class VendorOrderService {
 private final OrderRepository orders;private final OrderVendorIdentity identity;private final OrderService lifecycle;private final Clock clock;private final TransactionTemplate tx;
 public VendorOrderService(OrderRepository orders,OrderVendorIdentity identity,OrderService lifecycle,Clock clock,PlatformTransactionManager manager) {this.orders=orders;this.identity=identity;this.lifecycle=lifecycle;this.clock=clock;tx=new TransactionTemplate(manager);}
 @PreAuthorize("isAuthenticated()")
 public VendorOrderResponse read(UUID vendor,UUID id) {identity.require(vendor,false);return tx.execute(s->orders.vendor(id,vendor,true));}
 @PreAuthorize("isAuthenticated()")
 public VendorOrderPage list(UUID vendor,int size,UUID cursor) {
  identity.require(vendor,false);if(size<1 || size>10)throw OrderErrors.invalid("pageSize must be 1–10.");if(cursor!=null)orders.vendor(cursor,vendor,false);
  return tx.execute(s->{var rows=orders.vendorPage(vendor,cursor,size+1);boolean more=rows.size()>size;var ids=rows.subList(0,Math.min(size,rows.size()));return new VendorOrderPage(ids.stream().map(id->orders.vendor(id,vendor,true)).toList(),more?ids.getLast():null,more);});
 }
 @PreAuthorize("isAuthenticated()")
 public VendorOrderResponse decide(UUID vendor,UUID id,VendorDecisionRequest request,boolean accept) {
  UUID actor=identity.require(vendor,true);
  tx.executeWithoutResult(s->{var order=orders.vendor(id,vendor,true);String target=accept?"ACCEPTED":"REJECTED";
   if(order.vendorStatus().equals(target))return;
   if(order.version()!=request.version() || !order.status().equals("PENDING_PAYMENT") || !order.vendorStatus().equals("PENDING"))throw OrderErrors.conflict("Order or vendor decision changed.");
   var plan=orders.plan(id);if(!clock.instant().isBefore(plan.expiresAt()))throw OrderErrors.conflict("Order reservation window expired.");
   orders.vendorDecision(plan,vendor,actor,accept);
  });
  if(!accept)lifecycle.finishCancellation(id);return tx.execute(s->orders.vendor(id,vendor,true));
 }
}
