package com.fresveg.commerce.application.order;

import com.fresveg.commerce.api.dto.CheckoutContracts.*;
import com.fresveg.commerce.api.dto.OrderContracts.*;
import com.fresveg.commerce.application.checkout.CheckoutService;
import com.fresveg.commerce.application.payment.*;
import com.fresveg.commerce.application.observability.CommerceMetrics;
import com.fresveg.commerce.infrastructure.client.*;
import com.fresveg.commerce.infrastructure.persistence.OrderRepository;
import com.fresveg.commerce.infrastructure.security.CartIdentity;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class OrderService {
 private final OrderRepository orders;private final CartIdentity identity;private final CheckoutService checkout;
 private final OwnerHttp owners;private final OrderOwnerClient stock;private final PaymentGateway payments;private final CommerceMetrics metrics;private final JsonMapper json;private final Clock clock;private final TransactionTemplate tx;
 public OrderService(OrderRepository orders,CartIdentity identity,CheckoutService checkout,OwnerHttp owners,OrderOwnerClient stock,PaymentGateway payments,CommerceMetrics metrics,JsonMapper json,Clock clock,PlatformTransactionManager manager) {
  this.orders=orders;this.identity=identity;this.checkout=checkout;this.owners=owners;this.stock=stock;this.payments=payments;this.metrics=metrics;this.json=json;this.clock=clock;tx=new TransactionTemplate(manager);tx.setTimeout(120);
 }
 @PreAuthorize("isAuthenticated()")
 public OrderResponse create(String key,CreateOrderRequest request) {
  if(key==null || !key.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"))throw OrderErrors.invalid("Idempotency-Key must contain 1–128 safe ASCII characters.");
  if(request.paymentMethodId()==null)throw OrderErrors.invalid("paymentMethodId is required.");
  var customer=identity.current();String hash=hash(request);
  var prior=orders.intent(customer.customerId(),key,false);
  if(prior!=null)return replay(prior,hash);
  long deadline=System.nanoTime()+Duration.ofSeconds(90).toNanos();
  var preview=checkout.preview(new PreviewRequest(request.cartId(),request.deliveryAddressId(),request.deliverySlotId()));
  var lines=snapshots(preview,deadline);Instant now=clock.instant().truncatedTo(ChronoUnit.MICROS);
  var plan=new OrderPlan(UUID.randomUUID(),customer.customerId(),customer.userId(),request,preview,lines,now.plusSeconds(900).isBefore(preview.deliverySlot().startsAt())?now.plusSeconds(900):preview.deliverySlot().startsAt(),now);
  tx.executeWithoutResult(s->orders.prepare(customer.customerId(),key,hash,plan));
  try {
   tx.executeWithoutResult(s->{
    var intent=orders.intent(customer.customerId(),key,true);
    if(!intent.plan().orderId().equals(plan.orderId()) || !intent.status().equals("PREPARED"))return;
    var reservations=new LinkedHashMap<UUID,UUID>();
    for(var line:plan.lines()) {checkDeadline(deadline);reservations.put(line.listingId(),stock.reserve(plan,line));}
    checkDeadline(deadline);if(!clock.instant().isBefore(plan.expiresAt()))throw OrderErrors.conflict("Reservation window expired.");
    orders.persist(intent,reservations);
   });
   metrics.orderCreated();
   return authorizeAndConfirm(customer.customerId(),key,hash,plan);
  } catch(RuntimeException error) {
   // A commit acknowledgement can be ambiguous. Never compensate a durably successful intent.
   try {tx.executeWithoutResult(s->{var i=orders.intent(customer.customerId(),key,true);if(i!=null && i.plan().orderId().equals(plan.orderId()) && !i.status().equals("SUCCEEDED")){orders.state(i.id(),"FAILED");releaseAll(plan);}});}
   catch(RuntimeException cleanup) {org.slf4j.LoggerFactory.getLogger(OrderService.class).warn("Order compensation deferred ({})",cleanup.getClass().getSimpleName());}
   throw error;
  }
 }
 @PreAuthorize("isAuthenticated()")
 public OrderResponse read(UUID id) {return orders.owned(id,identity.current().customerId(),false);}
 @PreAuthorize("isAuthenticated()")
 public OrderPage list(int size,UUID cursor) {
  if(size<1 || size>10)throw OrderErrors.invalid("pageSize must be 1–10.");
  var customer=identity.current().customerId();if(cursor!=null)orders.owned(cursor,customer,false);
  var rows=orders.page(customer,cursor,size+1);boolean more=rows.size()>size;var visible=List.copyOf(rows.subList(0,Math.min(size,rows.size())));
  return new OrderPage(visible,more?visible.getLast().orderId():null,more);
 }
 @PreAuthorize("isAuthenticated()")
 public OrderResponse cancel(UUID id) {
  var customer=identity.current();
  tx.executeWithoutResult(s->{var order=orders.owned(id,customer.customerId(),true);if(order.status().equals("CANCELLED") || order.status().equals("CANCEL_PENDING"))return;
   if(order.status().equals("CONFIRMED")) {refundConfirmed(orders.plan(id),customer.userId());return;}
   if(!Set.of("PENDING_PAYMENT","PAYMENT_FAILED").contains(order.status()))throw OrderErrors.conflict("This order can no longer be cancelled.");
   orders.transition(orders.plan(id),"CANCEL_PENDING",customer.userId(),"Customer requested cancellation");});
  finishCancellation(id);return orders.owned(id,customer.customerId(),false);
 }
 private OrderResponse authorizeAndConfirm(UUID customer,String key,String hash,OrderPlan plan) {
  PaymentAuthorizationResult auth;
  try {auth=payments.authorize(new PaymentAuthorizationRequest(plan.orderId(),plan.customerId(),plan.request().paymentMethodId(),plan.preview().currency(),plan.preview().grandTotal(),"order-"+plan.orderId()));}
  catch(RuntimeException error) {metrics.paymentFailure();throw error;}
  if(!auth.authorized()) {
   metrics.paymentFailure();
   tx.executeWithoutResult(s->{orders.transition(plan,"PAYMENT_FAILED",plan.actorId(),"Payment authorization failed");orders.payment(plan,"order-"+plan.orderId(),auth);orders.response(orders.intent(customer,key,true).id(),orders.owned(plan.orderId(),customer,false));});
   releaseAll(plan,true);return orders.owned(plan.orderId(),customer,false);
  }
  tx.executeWithoutResult(s->{orders.transition(plan,"PAYMENT_AUTHORIZED",plan.actorId(),"Payment authorized");orders.payment(plan,"order-"+plan.orderId(),auth);});
  try {for(var line:plan.lines())stock.commit(plan,line);}
  catch(RuntimeException error) {
   var capture=orders.authorizedPayment(plan.orderId());
   if(capture!=null) {
    try {payments.refund(new RefundRequest(plan.orderId(),capture.paymentAttemptId(),capture.providerReference(),capture.currency(),capture.amount(),"refund-"+plan.orderId()));}
    catch(RuntimeException refundError) {metrics.paymentFailure();throw refundError;}
   }
   throw error;
  }
  tx.executeWithoutResult(s->{orders.transition(plan,"CONFIRMED",plan.actorId(),"Inventory committed after payment authorization");orders.response(orders.intent(customer,key,true).id(),orders.owned(plan.orderId(),customer,false));});
  return orders.owned(plan.orderId(),customer,false);
 }
 private void refundConfirmed(OrderPlan plan,UUID actor) {
  var capture=orders.authorizedPayment(plan.orderId());if(capture==null)throw OrderErrors.conflict("Payment authorization was not found.");
  RefundResult result;
  try {result=payments.refund(new RefundRequest(plan.orderId(),capture.paymentAttemptId(),capture.providerReference(),capture.currency(),capture.amount(),"refund-"+plan.orderId()));}
  catch(RuntimeException error) {metrics.paymentFailure();throw error;}
  orders.refund(plan,capture,result);if(!result.completed()) {metrics.paymentFailure();throw OrderErrors.conflict("Refund could not be completed.");}
  orders.transition(plan,"CANCELLED",actor,"Confirmed order refunded and cancelled");
 }
 public void recoverIntent(UUID id) {
  tx.executeWithoutResult(s->{var i=orders.intent(id);if(i==null || !Set.of("PREPARED","FAILED").contains(i.status()))return;
   boolean released=releaseAll(i.plan());
   // Continue checking through expiry for requests whose remote response was lost or delayed.
   orders.state(id,released && clock.instant().isAfter(i.plan().expiresAt().plusSeconds(120))?"COMPENSATED":"FAILED");});
 }
 public void finishCancellation(UUID id) {
  tx.executeWithoutResult(s->{var plan=orders.plan(id);var order=orders.owned(id,plan.customerId(),true);
   if(order.status().equals("CANCELLED"))return;
   if(order.status().equals("PENDING_PAYMENT") && !clock.instant().isBefore(plan.expiresAt())) {orders.transition(plan,"CANCEL_PENDING",plan.actorId(),"Payment reservation window expired");}
   else if(!order.status().equals("CANCEL_PENDING"))return;
   if(releaseAll(plan,true))orders.transition(plan,"CANCELLED",plan.actorId(),"All inventory holds released or expired");
  });
 }
 private boolean releaseAll(OrderPlan p) {return releaseAll(p,false);}
 private boolean releaseAll(OrderPlan p,boolean mustExist) {
  boolean complete=true;
  for(var line:p.lines())try{stock.release(p,line,mustExist);}catch(RuntimeException error){complete=false;org.slf4j.LoggerFactory.getLogger(OrderService.class).warn("Inventory release deferred ({})",error.getClass().getSimpleName());}
  return complete;
 }
 private List<OrderLine> snapshots(PreviewResponse p,long deadline) {
  var result=new ArrayList<OrderLine>();BigDecimal discount=p.discount(),tax=p.tax(),remaining=p.subtotal();int scale=Currency.getInstance(p.currency()).getDefaultFractionDigits();
  for(int n=0;n<p.items().size();n++) {
   checkDeadline(deadline);var line=p.items().get(n);var listing=owners.listing(line.listingId());if(listing==null)throw OrderErrors.conflict("Listing is unavailable.");
   UUID product=id(listing,"productId"),vendor=id(listing,"vendorId"),variant=id(listing,"variantId");
   if(!id(listing,"listingId").equals(line.listingId()) || !"ACTIVE".equals(text(listing,"status",24)))throw OwnerHttp.unavailable();
   String unit=text(listing,"uomCode",32);checkDeadline(deadline);var productData=stock.product(product);
   if(!productData.path("variants").isArray())throw OwnerHttp.unavailable();boolean valid=false;
   for(var v:productData.path("variants"))if(variant.toString().equals(v.path("variantId").asString()) && unit.equals(v.path("unitCode").asString()) && "ACTIVE".equals(v.path("status").asString()))valid=true;
   if(!valid)throw OrderErrors.conflict("Product variant changed.");
   checkDeadline(deadline);var vendorData=owners.vendor(vendor);if(!id(vendorData,"vendorId").equals(vendor))throw OwnerHttp.unavailable();
   boolean last=n==p.items().size()-1;
   BigDecimal d=last?discount:allocate(discount,line.lineSubtotal(),remaining,scale);
   BigDecimal t=last?tax:allocate(tax,line.lineSubtotal(),remaining,scale);discount=discount.subtract(d);tax=tax.subtract(t);remaining=remaining.subtract(line.lineSubtotal());
   var snapshot=json.createObjectNode();snapshot.set("product",productData);snapshot.set("listing",listing);snapshot.set("vendor",vendorData);snapshot.set("price",json.valueToTree(line));
   result.add(new OrderLine(UUID.randomUUID(),product,line.listingId(),vendor,text(productData,"name",240),text(vendorData,"name",160),text(listing,"vendorSku",64),unit,line.quantity(),line.unitPrice(),d,t,line.lineSubtotal().subtract(d).add(t),snapshot));
  }
  return List.copyOf(result);
 }
 static BigDecimal allocate(BigDecimal amount,BigDecimal weight,BigDecimal total,int scale) {return total.signum()==0?BigDecimal.ZERO.setScale(scale):amount.multiply(weight).divide(total,scale,RoundingMode.DOWN);}
 private static String text(JsonNode n,String field,int max) {var v=n.path(field);if(!v.isString() || v.asString().isBlank() || v.asString().length()>max)throw OwnerHttp.unavailable();return v.asString();}
 private static UUID id(JsonNode n,String field) {try{return UUID.fromString(text(n,field,36));}catch(Exception e){throw OwnerHttp.unavailable();}}
 private static void checkDeadline(long deadline) {if(System.nanoTime()>deadline)throw OwnerHttp.unavailable();}
 private String hash(CreateOrderRequest r) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(r).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private static OrderResponse replay(OrderRepository.Intent intent,String hash) {
  if(!intent.hash().equals(hash))throw OrderErrors.conflict("Idempotency-Key was used with a different request.");
  if(intent.status().equals("SUCCEEDED"))return intent.response();
  throw OrderErrors.conflict("This submission is processing or failed; use order reads to reconcile and a new key only for a new attempt.");
 }
}
