package com.fresveg.fulfillment.infrastructure.persistence;
import com.fresveg.fulfillment.api.dto.FulfillmentContracts.*;
import com.fresveg.fulfillment.application.FulfillmentException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;
@Repository
public class FulfillmentRepository {
 private final JdbcTemplate db;private final JsonMapper json;
 public FulfillmentRepository(JdbcTemplate db,JsonMapper json){this.db=db;this.json=json;}
 public List<DeliverySlotResponse> slots(String area,Instant from,Instant to,int size){
  return db.query("SELECT slot_id,service_area,start_time,end_time,capacity,reserved_capacity,status,version FROM fulfillment.delivery_slots WHERE status='OPEN' AND (?::text IS NULL OR service_area=?) AND start_time>=? AND start_time<? ORDER BY start_time,slot_id LIMIT ?",(r,n)->new DeliverySlotResponse(r.getObject(1,UUID.class),r.getString(2),r.getTimestamp(3).toInstant(),r.getTimestamp(4).toInstant(),r.getInt(5),r.getInt(6),r.getString(7),r.getLong(8)),area,area,Timestamp.from(from),Timestamp.from(to),size);
 }
 public UUID seedSlot(DeliverySlotSeedRequest r){
  UUID id=UUID.randomUUID();db.update("INSERT INTO fulfillment.delivery_slots(slot_id,service_area,start_time,end_time,capacity,reserved_capacity,status) VALUES (?,?,?,?,?,0,?)",id,r.serviceArea(),Timestamp.from(r.startTime()),Timestamp.from(r.endTime()),r.capacity(),r.status()==null?"OPEN":r.status());return id;
 }
 public DeliverySlotResponse slot(UUID id){
  var rows=db.query("SELECT slot_id,service_area,start_time,end_time,capacity,reserved_capacity,status,version FROM fulfillment.delivery_slots WHERE slot_id=?",(r,n)->new DeliverySlotResponse(r.getObject(1,UUID.class),r.getString(2),r.getTimestamp(3).toInstant(),r.getTimestamp(4).toInstant(),r.getInt(5),r.getInt(6),r.getString(7),r.getLong(8)),id);
  if(rows.isEmpty())throw missing();return rows.getFirst();
 }
 public UUID create(CreateFulfillmentRequest r,UUID actor){
  var existing=db.queryForList("SELECT fulfillment_id FROM fulfillment.fulfillments WHERE order_id=?",UUID.class,r.orderId());if(!existing.isEmpty())return existing.getFirst();
  var slot=db.query("SELECT capacity,reserved_capacity,status,start_time,end_time,service_area FROM fulfillment.delivery_slots WHERE slot_id=? FOR UPDATE",(x,n)->List.of(x.getInt(1),x.getInt(2),x.getString(3),x.getTimestamp(4).toInstant(),x.getTimestamp(5).toInstant(),x.getString(6)),r.deliverySlotId());
  if(slot.isEmpty())throw missing();var s=slot.getFirst();if(!s.get(2).equals("OPEN"))throw conflict("Delivery slot is not open.");if((int)s.get(1)>=(int)s.get(0))throw conflict("Delivery slot capacity is full.");
  if(!r.serviceArea().equals(s.get(5)) || !r.requestedStart().equals(s.get(3)) || !r.requestedEnd().equals(s.get(4)))throw conflict("Fulfillment request does not match the delivery slot.");
  UUID id=UUID.randomUUID();db.update("UPDATE fulfillment.delivery_slots SET reserved_capacity=reserved_capacity+1,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE slot_id=?",r.deliverySlotId());
  db.update("INSERT INTO fulfillment.fulfillments(fulfillment_id,order_id,customer_id,delivery_slot_id,status,service_area,delivery_address,requested_start,requested_end,created_by,updated_by) VALUES (?,?,?,?, 'CREATED', ?, ?::jsonb, ?, ?, ?, ?)",id,r.orderId(),r.customerId(),r.deliverySlotId(),r.serviceArea(),json.writeValueAsString(r.deliveryAddress()),Timestamp.from(r.requestedStart()),Timestamp.from(r.requestedEnd()),actor,actor);
  for(var item:r.items())db.update("INSERT INTO fulfillment.fulfillment_items(fulfillment_item_id,fulfillment_id,order_item_id,product_id,listing_id,vendor_id,product_name,quantity,unit_code) VALUES (?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),id,item.orderItemId(),item.productId(),item.listingId(),item.vendorId(),item.productName(),item.quantity(),item.unitCode());
  UUID shipment=UUID.randomUUID();db.update("INSERT INTO fulfillment.shipments(shipment_id,fulfillment_id,status) VALUES (?,?,'PENDING')",shipment,id);event(shipment,"PENDING","Fulfillment created");return id;
 }
 public FulfillmentResponse one(UUID id,boolean lock){
  var rows=db.query("SELECT fulfillment_id,order_id,customer_id,delivery_slot_id,status,service_area,delivery_address,requested_start,requested_end,version FROM fulfillment.fulfillments WHERE fulfillment_id=?"+(lock?" FOR UPDATE":""),(r,n)->new FulfillmentResponse(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getObject(4,UUID.class),r.getString(5),r.getString(6),json.readValue(r.getString(7),Map.class),r.getTimestamp(8).toInstant(),r.getTimestamp(9).toInstant(),items(id),shipments(id),r.getLong(10)),id);
  if(rows.isEmpty())throw missing();return rows.getFirst();
 }
 public FulfillmentResponse transition(UUID id,String target,long version,String description,UUID actor){
  var current=one(id,true);if(current.version()!=version)throw conflict("Fulfillment version changed; reload before updating.");
  if(!allowed(current.status(),target))throw conflict("Invalid fulfillment transition.");
  db.update("UPDATE fulfillment.fulfillments SET status=?,updated_at=CURRENT_TIMESTAMP,updated_by=?,version=version+1 WHERE fulfillment_id=?",target,actor,id);
  String shipment=switch(target){case "OUT_FOR_DELIVERY"->"OUT_FOR_DELIVERY";case "DELIVERED"->"DELIVERED";case "CANCELLED"->"CANCELLED";default->null;};
  if(shipment!=null){for(UUID sid:db.queryForList("SELECT shipment_id FROM fulfillment.shipments WHERE fulfillment_id=?",UUID.class,id)){db.update("UPDATE fulfillment.shipments SET status=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE shipment_id=?",shipment,sid);event(sid,shipment,description==null?target:description);}}
  return one(id,false);
 }
 public TrackingResponse tracking(UUID id){var f=one(id,false);return new TrackingResponse(f.fulfillmentId(),f.status(),f.shipments());}
 private List<FulfillmentItemResponse> items(UUID f){return db.query("SELECT fulfillment_item_id,order_item_id,product_id,listing_id,vendor_id,product_name,quantity,unit_code,status FROM fulfillment.fulfillment_items WHERE fulfillment_id=? ORDER BY fulfillment_item_id",(r,n)->new FulfillmentItemResponse(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getObject(4,UUID.class),r.getObject(5,UUID.class),r.getString(6),r.getBigDecimal(7),r.getString(8),r.getString(9)),f);}
 private List<ShipmentResponse> shipments(UUID f){return db.query("SELECT shipment_id,status,carrier,tracking_reference,version FROM fulfillment.shipments WHERE fulfillment_id=? ORDER BY shipment_id",(r,n)->{UUID id=r.getObject(1,UUID.class);return new ShipmentResponse(id,r.getString(2),r.getString(3),r.getString(4),events(id),r.getLong(5));},f);}
 private List<ShipmentEventResponse> events(UUID s){return db.query("SELECT event_id,shipment_id,status,description,occurred_at FROM fulfillment.shipment_events WHERE shipment_id=? ORDER BY occurred_at,event_id",(r,n)->new ShipmentEventResponse(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getString(4),r.getTimestamp(5).toInstant()),s);}
 private void event(UUID shipment,String status,String description){db.update("INSERT INTO fulfillment.shipment_events(event_id,shipment_id,status,description,occurred_at) VALUES (?,?,?,?,CURRENT_TIMESTAMP)",UUID.randomUUID(),shipment,status,description);}
 private static boolean allowed(String from,String to){return switch(from){case "CREATED"->Set.of("PACKING","CANCELLED").contains(to);case "PACKING"->Set.of("READY_FOR_DELIVERY","CANCELLED").contains(to);case "READY_FOR_DELIVERY"->Set.of("OUT_FOR_DELIVERY","CANCELLED").contains(to);case "OUT_FOR_DELIVERY"->to.equals("DELIVERED");default->from.equals(to);};}
 private static FulfillmentException missing(){return new FulfillmentException(HttpStatus.NOT_FOUND,"FUL-404-001","Fulfillment resource not found.");}
 private static FulfillmentException conflict(String m){return new FulfillmentException(HttpStatus.CONFLICT,"FUL-409-001",m);}
}
