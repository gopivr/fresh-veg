package com.fresveg.commerce.infrastructure.persistence;

import com.fresveg.commerce.api.dto.OrderContracts.*;
import com.fresveg.common.http.CorrelationIds;
import com.fresveg.commerce.application.order.*;
import com.fresveg.commerce.application.payment.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.MDC;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/** Explicit SQL keeps immutable snapshot writes separate from the limited lifecycle grants. */
@Repository
public class OrderRepository {
 private final JdbcTemplate db;private final JsonMapper json;
 public OrderRepository(JdbcTemplate db,JsonMapper json) {this.db=db;this.json=json.rebuild().enable(tools.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();}
 public record Intent(UUID id,String hash,String status,OrderPlan plan,OrderResponse response) { }
 public Intent intent(UUID customer,String key,boolean lock) {
  var rows=db.query("SELECT * FROM commerce.idempotency_records WHERE customer_id=? AND idempotency_key=?"+(lock?" FOR UPDATE":""),(r,n)->new Intent(r.getObject("record_id",UUID.class),r.getString("request_hash"),r.getString("status"),json.readValue(r.getString("plan"),OrderPlan.class),r.getString("response")==null?null:json.readValue(r.getString("response"),OrderResponse.class)),customer,key);
  return rows.isEmpty()?null:rows.getFirst();
 }
 public Intent intent(UUID id) {
  var rows=db.query("SELECT * FROM commerce.idempotency_records WHERE record_id=? FOR UPDATE SKIP LOCKED",(r,n)->new Intent(id,r.getString("request_hash"),r.getString("status"),json.readValue(r.getString("plan"),OrderPlan.class),r.getString("response")==null?null:json.readValue(r.getString("response"),OrderResponse.class)),id);
  return rows.isEmpty()?null:rows.getFirst();
 }
 public void prepare(UUID customer,String key,String hash,OrderPlan plan) {
  db.update("INSERT INTO commerce.idempotency_records(record_id,customer_id,idempotency_key,request_hash,status,plan,created_at,updated_at) VALUES (?,?,?,?,'PREPARED',?::jsonb,?,?) ON CONFLICT(customer_id,idempotency_key) DO NOTHING",UUID.randomUUID(),customer,key,hash,json.writeValueAsString(plan),time(plan.createdAt()),time(plan.createdAt()));
 }
 public void state(UUID id,String status) {db.update("UPDATE commerce.idempotency_records SET status=?,updated_at=CURRENT_TIMESTAMP WHERE record_id=?",status,id);}
 public void response(UUID id,OrderResponse response) {db.update("UPDATE commerce.idempotency_records SET response=?::jsonb,updated_at=CURRENT_TIMESTAMP WHERE record_id=?",json.writeValueAsString(response),id);}
 public void persist(Intent intent,Map<UUID,UUID> reservations) {
  var p=intent.plan();var q=p.preview();var response=p.response();
  // Serialize with the cart writer: edits either finish before this check or follow order creation.
  var versions=db.queryForList("SELECT version FROM commerce.carts WHERE cart_id=? AND customer_id=? FOR UPDATE",Long.class,p.request().cartId(),p.customerId());
  if(versions.isEmpty() || versions.getFirst()!=q.cartVersion())throw OrderErrors.conflict("Cart changed during order placement.");
  db.update("INSERT INTO commerce.orders(order_id,order_number,customer_id,cart_id,cart_version,status,currency,subtotal,discount_amount,tax_amount,delivery_fee,grand_total,shipping_address_snapshot,delivery_slot_id,delivery_slot_snapshot,pricing_policy_id,payment_method_id,reservation_expires_at,response_snapshot,created_at,created_by,updated_at,updated_by) VALUES (?,?,?,?,?,'PENDING_PAYMENT',?,?,?,?,?,?,?::jsonb,?,?::jsonb,?,?,?,?::jsonb,?,?,?,?)",
   p.orderId(),response.orderNumber(),p.customerId(),q.cartId(),q.cartVersion(),q.currency(),q.subtotal(),q.discount(),q.tax(),q.deliveryFee(),q.grandTotal(),json.writeValueAsString(q.deliveryAddress()),q.deliverySlot().deliverySlotId(),json.writeValueAsString(q.deliverySlot()),q.pricingPolicyId(),p.request().paymentMethodId(),time(p.expiresAt()),json.writeValueAsString(response),time(p.createdAt()),p.actorId(),time(p.createdAt()),p.actorId());
  for(var l:p.lines())db.update("INSERT INTO commerce.order_items(order_item_id,order_id,product_id,listing_id,vendor_id,product_name,vendor_name,vendor_sku,unit_code,quantity,unit_price,discount_amount,tax_amount,line_total,product_snapshot,reservation_id,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?)",l.orderItemId(),p.orderId(),l.productId(),l.listingId(),l.vendorId(),l.productName(),l.vendorName(),l.vendorSku(),l.unitCode(),l.quantity(),l.unitPrice(),l.discountAmount(),l.taxAmount(),l.lineTotal(),json.writeValueAsString(l.productSnapshot()),reservations.get(l.listingId()),time(p.createdAt()));
  history(p,"PENDING_PAYMENT","Order created",p.actorId());event(p,"OrderCreated",response);
  db.update("UPDATE commerce.idempotency_records SET status='SUCCEEDED',response=?::jsonb,order_id=?,updated_at=CURRENT_TIMESTAMP WHERE record_id=?",json.writeValueAsString(response),p.orderId(),intent.id());
 }
 public OrderResponse owned(UUID id,UUID customer,boolean lock) {
  var rows=db.query("SELECT response_snapshot,status,version FROM commerce.orders WHERE order_id=? AND customer_id=?"+(lock?" FOR UPDATE":""),(r,n)->json.readValue(r.getString(1),OrderResponse.class).state(r.getString(2),r.getLong(3)),id,customer);
  if(rows.isEmpty())throw OrderErrors.missing();return rows.getFirst();
 }
 public List<OrderResponse> page(UUID customer,UUID after,int size) {
  return db.query("SELECT response_snapshot,status,version FROM commerce.orders WHERE customer_id=? AND (?::uuid IS NULL OR order_id>?::uuid) ORDER BY order_id LIMIT ?",(r,n)->json.readValue(r.getString(1),OrderResponse.class).state(r.getString(2),r.getLong(3)),customer,after,after,size);
 }
 public VendorOrderResponse vendor(UUID order,UUID vendor,boolean lock) {
  var rows=db.query("SELECT response_snapshot,status,version FROM commerce.orders o WHERE order_id=? AND EXISTS(SELECT 1 FROM commerce.order_items i WHERE i.order_id=o.order_id AND i.vendor_id=?)"+(lock?" FOR UPDATE":""),(r,n)->json.readValue(r.getString(1),OrderResponse.class).state(r.getString(2),r.getLong(3)),order,vendor);
  if(rows.isEmpty())throw OrderErrors.missing();var o=rows.getFirst();
  String status=db.queryForObject("SELECT min(vendor_status) FROM commerce.order_items WHERE order_id=? AND vendor_id=?",String.class,order,vendor);
  return new VendorOrderResponse(o.orderId(),o.orderNumber(),o.status(),status,o.currency(),o.deliverySlot(),o.items().stream().filter(i->i.vendorId().equals(vendor)).toList(),o.version());
 }
 public List<UUID> vendorPage(UUID vendor,UUID after,int size) {return db.queryForList("SELECT order_id FROM commerce.orders o WHERE (?::uuid IS NULL OR order_id>?::uuid) AND EXISTS(SELECT 1 FROM commerce.order_items i WHERE i.order_id=o.order_id AND i.vendor_id=?) ORDER BY order_id LIMIT ?",UUID.class,after,after,vendor,size);}
 public void vendorDecision(OrderPlan p,UUID vendor,UUID actor,boolean accept) {
  db.update("UPDATE commerce.order_items SET vendor_status=? WHERE order_id=? AND vendor_id=?",accept?"ACCEPTED":"REJECTED",p.orderId(),vendor);
  transition(p,accept?"PENDING_PAYMENT":"CANCEL_PENDING",actor,accept?"Vendor accepted its items; payment remains pending":"Vendor rejected its items; entire order cancellation requested");
 }
 public OrderPlan plan(UUID order) {return db.queryForObject("SELECT plan FROM commerce.idempotency_records WHERE order_id=?",(r,n)->json.readValue(r.getString(1),OrderPlan.class),order);}
 public void transition(OrderPlan p,String target,UUID actor,String reason) {
  db.update("UPDATE commerce.orders SET status=?,version=version+1,updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE order_id=?",target,actor,p.orderId());history(p,target,reason,actor);
  if(target.equals("CANCELLED"))event(p,"OrderCancelled",owned(p.orderId(),p.customerId(),false));
  if(target.equals("CONFIRMED"))event(p,"OrderConfirmed",owned(p.orderId(),p.customerId(),false));
 }
 public UUID payment(OrderPlan p,String key,PaymentAuthorizationResult result) {
  UUID id=UUID.randomUUID();String status=result.authorized()?"AUTHORIZED":"FAILED";var now=Instant.now();
  db.update("INSERT INTO commerce.payment_attempts(payment_attempt_id,order_id,idempotency_key,payment_method_id,provider,provider_reference,status,currency,amount,failure_code,failure_message,requested_at,completed_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
   id,p.orderId(),key,p.request().paymentMethodId(),result.provider(),result.providerReference(),status,p.preview().currency(),p.preview().grandTotal(),result.failureCode(),result.failureMessage(),time(now),time(now));
  event(p,result.authorized()?"PaymentAuthorized":"PaymentFailed",owned(p.orderId(),p.customerId(),false));
  return id;
 }
 public PaymentCapture authorizedPayment(UUID order) {
  var rows=db.query("SELECT payment_attempt_id,provider_reference,currency,amount FROM commerce.payment_attempts WHERE order_id=? AND status='AUTHORIZED' ORDER BY completed_at DESC LIMIT 1",(r,n)->new PaymentCapture(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getBigDecimal(4)),order);
  return rows.isEmpty()?null:rows.getFirst();
 }
 public void refund(OrderPlan p,PaymentCapture capture,RefundResult result) {
  var now=Instant.now();
  db.update("INSERT INTO commerce.refunds(refund_id,order_id,payment_attempt_id,idempotency_key,provider,provider_reference,status,currency,amount,failure_code,failure_message,requested_at,completed_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
   UUID.randomUUID(),p.orderId(),capture.paymentAttemptId(),"refund-"+p.orderId(),result.provider(),result.providerReference(),result.completed()?"COMPLETED":"FAILED",capture.currency(),capture.amount(),result.failureCode(),result.failureMessage(),time(now),time(now));
  if(result.completed())event(p,"RefundCompleted",owned(p.orderId(),p.customerId(),false));
 }
 public record PaymentCapture(UUID paymentAttemptId,String providerReference,String currency,java.math.BigDecimal amount) { }
 public List<UUID> recovery() {return db.queryForList("SELECT record_id FROM commerce.idempotency_records WHERE status IN ('PREPARED','FAILED') AND updated_at<CURRENT_TIMESTAMP-INTERVAL '2 minutes' ORDER BY updated_at LIMIT 100",UUID.class);}
 public List<UUID> expired() {return db.queryForList("SELECT order_id FROM commerce.orders WHERE status='CANCEL_PENDING' OR (status='PENDING_PAYMENT' AND reservation_expires_at<=CURRENT_TIMESTAMP) ORDER BY reservation_expires_at LIMIT 100",UUID.class);}
 private void history(OrderPlan p,String s,String reason,UUID actor) {db.update("INSERT INTO commerce.order_status_history(history_id,order_id,status,reason,actor_id,created_at) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP)",UUID.randomUUID(),p.orderId(),s,reason,actor);}
 private void event(OrderPlan p,String type,OrderResponse response) {
  UUID id=UUID.randomUUID();var now=Instant.now();String correlation=Optional.ofNullable(MDC.get(CorrelationIds.CONTEXT_KEY)).orElse("system");
  var payload=Map.of("eventId",id,"eventType",type,"eventVersion",1,"aggregateId",p.orderId(),"occurredAt",now,"correlationId",correlation,"payload",Map.of("orderId",p.orderId(),"customerId",p.customerId(),"status",response.status(),"currency",response.currency(),"grandTotal",response.grandTotal()));
  db.update("INSERT INTO commerce.outbox_events(event_id,aggregate_type,aggregate_id,event_type,payload,created_at) VALUES (?,'Order',?,?,?::jsonb,?)",id,p.orderId(),type,json.writeValueAsString(payload),time(now));
 }
 private static Timestamp time(Instant i) {return Timestamp.from(i);}
}
