package com.fresveg.supply.application;

import com.fresveg.supply.api.dto.InventoryContracts.*;
import com.fresveg.supply.domain.*;
import com.fresveg.supply.infrastructure.persistence.*;
import com.fresveg.supply.infrastructure.security.*;
import com.fresveg.supply.application.observability.SupplyMetrics;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InventoryService {
 private static final BigDecimal ZERO=BigDecimal.ZERO;
 private final InventoryRepository inventories;
 private final InventoryBatchRepository batches;
 private final InventoryReservationRepository reservations;
 private final InventoryTransactionRepository ledger;
 private final VendorListingRepository listings;
 private final VendorLocationRepository locations;
 private final SupplyAuthorization vendors;
 private final InventoryServiceAuthorization services;
 private final InventoryMapper mapper;
 private final SupplyCursor cursors;
 private final Clock clock;
 private final SupplyMetrics metrics;
 private final EntityManager entities;
 public InventoryService(InventoryRepository inventories,InventoryBatchRepository batches,InventoryReservationRepository reservations,
 InventoryTransactionRepository ledger,VendorListingRepository listings,VendorLocationRepository locations,SupplyAuthorization vendors,
 InventoryServiceAuthorization services,InventoryMapper mapper,SupplyCursor cursors,Clock clock,SupplyMetrics metrics,EntityManager entities) {
  this.inventories=inventories;this.batches=batches;this.reservations=reservations;this.ledger=ledger;this.listings=listings;this.locations=locations;
  this.vendors=vendors;this.services=services;this.mapper=mapper;this.cursors=cursors;this.clock=clock;this.metrics=metrics;this.entities=entities;
 }
 @PreAuthorize("isAuthenticated()")
 public InventoryResponse initialize(InitializeInventoryRequest request) {
  var listing=listings.findById(request.listingId()).orElseThrow(InventoryService::missing);
  UUID actor=owned(listing,false);vendors.requireVendor(listing.getVendorId(),true);
  var existing=inventories.findByListingId(listing.getId());
  if(existing.isPresent()) return mapper.inventory(existing.get());
  var i=new Inventory();i.initialize(actor);i.setListingId(listing.getId());i.setQuantityOnHand(ZERO);i.setReservedQuantity(ZERO);
  return mapper.inventory(inventories.saveAndFlush(i));
 }
 @PreAuthorize("isAuthenticated()") @Transactional(readOnly=true)
 public SupplyService.Page<InventoryResponse> list(UUID vendor,int size,String cursor) {
  vendors.requireVendor(vendor,false);String filter=cursors.fingerprint(List.of("inventory",vendor));UUID after=position(size,cursor,filter);
  return page(inventories.page(vendor,after,PageRequest.of(0,size+1)),size,filter,mapper::inventory);
 }
 @PreAuthorize("isAuthenticated()") @Transactional(readOnly=true)
 public SupplyService.Page<BatchResponse> batches(UUID id,int size,String cursor) {
  owner(id,false);String filter=cursors.fingerprint(List.of("batches",id));UUID after=position(size,cursor,filter);
  return page(batches.page(id,after,PageRequest.of(0,size+1)),size,filter,mapper::batch);
 }
 @PreAuthorize("isAuthenticated()") @Transactional(readOnly=true)
 public SupplyService.Page<TransactionResponse> transactions(UUID id,int size,String cursor) {
  owner(id,false);String filter=cursors.fingerprint(List.of("ledger",id));UUID after=position(size,cursor,filter);
  return page(ledger.page(id,after,PageRequest.of(0,size+1)),size,filter,mapper::transaction);
 }
 @PreAuthorize("isAuthenticated()")
 public InventoryResponse receive(UUID id,ReceiveBatchRequest request) {
  UUID actor=owner(id,true);var i=lock(id);version(i,request.version());
  LocalDate today=LocalDate.now(clock);
  if(request.receivedDate().isAfter(today) || request.harvestDate()!=null && request.harvestDate().isAfter(request.receivedDate())
    || request.bestBeforeDate()!=null && request.bestBeforeDate().isBefore(request.receivedDate())
    || request.expiryDate()!=null && (!request.expiryDate().isAfter(today) || request.expiryDate().isBefore(request.receivedDate()))
    || request.expiryDate()!=null && request.bestBeforeDate()!=null && request.bestBeforeDate().isAfter(request.expiryDate())) throw invalid("Invalid batch dates; expiry is exclusive at UTC midnight.");
  if(request.certificationData().size()>64 || request.certificationData().entrySet().stream().anyMatch(e->e.getKey()==null || e.getValue()==null || e.getKey().length()>160 || e.getValue().length()>240)) throw invalid("Certification metadata exceeds bounds.");
  var b=new InventoryBatch();b.initialize(actor);b.setInventoryId(id);b.setBatchNumber(request.batchNumber());b.setHarvestDate(request.harvestDate());b.setReceivedDate(request.receivedDate());
  b.setBestBeforeDate(request.bestBeforeDate());b.setExpiryDate(request.expiryDate());b.setOrigin(request.origin());b.setGrade(request.grade());b.setCertificationData(request.certificationData());
  b.setQuantityReceived(request.quantity());b.setQuantityRemaining(request.quantity());b.setReservedQuantity(ZERO);b.setStatus("ACTIVE");batches.save(b);
  move(i,b,null,"RECEIPT",request.quantity(),ZERO,"Batch received",actor,false);
  inventories.flush();return mapper.inventory(i);
 }
 @PreAuthorize("isAuthenticated()")
 public InventoryResponse adjust(UUID id,AdjustInventoryRequest request) {
  UUID actor=owner(id,true);var i=lock(id);version(i,request.version());
  var b=batches.findById(request.batchId()).filter(x->x.getInventoryId().equals(id)).orElseThrow(InventoryService::missing);
  BigDecimal delta=request.quantityDelta();
  if(delta.signum()==0) throw invalid("Adjustment must change quantity.");
  BigDecimal remaining=b.getQuantityRemaining().add(delta);
  if(remaining.compareTo(b.getReservedQuantity())<0 || remaining.compareTo(b.getQuantityReceived())>0) throw conflict("Adjustment must preserve reserved stock and cannot exceed original receipt.");
  move(i,b,null,"ADJUSTMENT",delta,ZERO,request.reason(),actor,true);inventories.flush();return mapper.inventory(i);
 }
 @PreAuthorize("@inventoryServiceAuthorization.allowed()")
 public ReservationResponse reserve(ReserveInventoryRequest request) {
  return reserveEligible(request,request.expiresAt());
 }
 private ReservationResponse reserveEligible(ReserveInventoryRequest request,Instant requiredUntil) {
  String client=services.requireClient();var i=lock(request.inventoryId());Instant now=clock.instant();expireLocked(i,now);
  var existing=reservations.findByClientIdAndExternalReference(client,request.externalReference());
  if(existing.isPresent()) {
   var r=existing.get();
   if(!r.getInventoryId().equals(i.getId()) || r.getQuantity().compareTo(request.quantity())!=0 || !r.getExpiresAt().equals(request.expiresAt())) throw reservationConflict("External reference was used for a different reservation intent.");
   for(var allocation:r.getAllocations().keySet()) {
    var batch=batches.findById(UUID.fromString(allocation)).orElseThrow();
    if(batch.getExpiryDate()!=null && batch.getExpiryDate().atStartOfDay(ZoneOffset.UTC).toInstant().isBefore(requiredUntil))throw reservationConflict("Existing reservation does not cover the requested delivery horizon.");
   }
   inventories.flush();return mapper.reservation(r);
  }
  if(!request.expiresAt().isAfter(now) || request.expiresAt().isAfter(now.plusSeconds(3600)) || request.expiresAt().getNano()%1000!=0) throw invalid("Reservation expiry must be within one hour and use microsecond precision.");
  var listing=listings.findById(i.getListingId()).orElseThrow(InventoryService::missing);
  if(listing.getStatus()!=ListingStatus.ACTIVE || locations.findById(listing.getLocationId()).orElseThrow().getStatus()!=LocationStatus.ACTIVE) throw reservationConflict("Listing or location is inactive.");
  var candidates=new ArrayList<>(batches.findByInventoryIdOrderByExpiryDateAscIdAsc(i.getId()));
  candidates.sort(Comparator.comparing(InventoryBatch::getExpiryDate,Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(InventoryBatch::getId));
  BigDecimal needed=request.quantity();Map<String,String> allocations=new LinkedHashMap<>();
  for(var b:candidates) {
   if(b.getExpiryDate()!=null && b.getExpiryDate().atStartOfDay(ZoneOffset.UTC).toInstant().isBefore(requiredUntil)) continue;
   BigDecimal take=b.getQuantityRemaining().subtract(b.getReservedQuantity()).min(needed);
   if(take.signum()>0) { allocations.put(b.getId().toString(),take.toPlainString());needed=needed.subtract(take); }
   if(needed.signum()==0) break;
  }
  if(needed.signum()>0) {metrics.inventoryReservationConflict();throw new SupplyException(HttpStatus.CONFLICT,"INV-409-001","Insufficient eligible unreserved inventory.");}
  var r=new InventoryReservation();r.initialize(null);r.setInventoryId(i.getId());r.setClientId(client);r.setExternalReference(request.externalReference());r.setQuantity(request.quantity());r.setStatus("ACTIVE");r.setExpiresAt(request.expiresAt());r.setAllocations(allocations);reservations.save(r);
  for(var b:candidates) if(allocations.containsKey(b.getId().toString())) move(i,b,r,"RESERVE",ZERO,new BigDecimal(allocations.get(b.getId().toString())),"Inventory reserved",null,true);
  inventories.flush();return mapper.reservation(r);
 }
 @PreAuthorize("@inventoryServiceAuthorization.allowed()")
 public ReservationResponse finish(UUID id,boolean commit) {
  String client=services.requireClient();var r=reservations.findById(id).filter(x->x.getClientId().equals(client)).orElseThrow(InventoryService::missing);
  var i=lock(r.getInventoryId());entities.refresh(r);Instant now=clock.instant();
  if(r.getStatus().equals("ACTIVE") && !r.getExpiresAt().isAfter(now)) transition(i,r,"EXPIRED");
  else if(r.getStatus().equals("ACTIVE")) transition(i,r,commit?"COMMITTED":"RELEASED");
  else if(!r.getStatus().equals(commit?"COMMITTED":"RELEASED") && !r.getStatus().equals("EXPIRED")) throw conflict("Reservation has a different terminal state.");
  inventories.flush();return mapper.reservation(r);
 }
 @PreAuthorize("@inventoryServiceAuthorization.allowed()")
 public ReservationResponse reserveOrder(OrderReservationRequest request) {
  services.requireClient();Instant now=clock.instant();
  if(request.requiredUntil().isBefore(request.expiresAt()) || request.requiredUntil().isAfter(now.plusSeconds(2592000)) || request.requiredUntil().getNano()%1000!=0) throw invalid("Delivery horizon must cover the hold and be within thirty days.");
  var i=inventories.findByListingId(request.listingId()).orElseThrow(InventoryService::missing);
  return reserveEligible(new ReserveInventoryRequest(i.getId(),request.externalReference(),request.quantity(),request.expiresAt()),request.requiredUntil());
 }
 @PreAuthorize("@inventoryServiceAuthorization.allowed()") @Transactional(readOnly=true)
 public ReservationResponse reservation(String reference) {
  String client=services.requireClient();
  return mapper.reservation(reservations.findByClientIdAndExternalReference(client,reference).orElseThrow(InventoryService::missing));
 }
 // Invoked by the scheduler through this Spring transactional proxy, one inventory per transaction.
 public void expireInventory(UUID id) { var i=lock(id);expireLocked(i,clock.instant()); }
 private void expireLocked(Inventory i,Instant now) {
  for(var r:reservations.findByInventoryIdAndStatusAndExpiresAtLessThanEqual(i.getId(),"ACTIVE",now)) transition(i,r,"EXPIRED");
 }
 private void transition(Inventory i,InventoryReservation r,String target) {
  String kind=switch(target) { case "COMMITTED"->"COMMIT";case "RELEASED"->"RELEASE";default->"EXPIRE"; };
  for(var allocation:r.getAllocations().entrySet()) {
   var b=batches.findById(UUID.fromString(allocation.getKey())).orElseThrow();BigDecimal q=new BigDecimal(allocation.getValue());
   move(i,b,r,kind,target.equals("COMMITTED")?q.negate():ZERO,q.negate(),"Reservation "+target.toLowerCase(Locale.ROOT),null,true);
  }
  r.setStatus(target);r.touch(null);
 }
 private void move(Inventory i,InventoryBatch b,InventoryReservation r,String kind,BigDecimal delta,BigDecimal reserved,String reason,UUID actor,boolean updateBatch) {
  i.setQuantityOnHand(i.getQuantityOnHand().add(delta));i.setReservedQuantity(i.getReservedQuantity().add(reserved));i.touch(actor);
  if(updateBatch) { b.setQuantityRemaining(b.getQuantityRemaining().add(delta));b.setReservedQuantity(b.getReservedQuantity().add(reserved));b.setStatus(b.getQuantityRemaining().signum()==0?"DEPLETED":"ACTIVE");b.touch(actor); }
  var t=new InventoryTransaction();t.initialize(actor);t.setInventoryId(i.getId());t.setBatchId(b.getId());t.setReservationId(r==null?null:r.getId());t.setKind(kind);t.setQuantityDelta(delta);t.setReservedDelta(reserved);t.setReason(reason);ledger.save(t);
 }
 private UUID owner(UUID id,boolean write) { var i=inventories.findById(id).orElseThrow(InventoryService::missing);return owned(listings.findById(i.getListingId()).orElseThrow(),write); }
 private UUID owned(VendorListing l,boolean write) { var m=vendors.membership(l.getVendorId());if(!m.member()) throw missing();return vendors.requireVendor(l.getVendorId(),write); }
 private Inventory lock(UUID id) { var i=inventories.locked(id).orElseThrow(InventoryService::missing);entities.refresh(i);return i; }
 private static void version(Inventory i,long version) { if(i.getVersion()!=version) throw conflict("Inventory version changed; reload before updating."); }
 private UUID position(int size,String cursor,String filter) { if(size<1 || size>100) throw invalid("pageSize must be 1–100.");var p=cursors.decode(cursor,filter);return p==null?null:p.id(); }
 private <T extends AuditedEntity,R> SupplyService.Page<R> page(List<T> rows,int size,String filter,Function<T,R> map) { boolean more=rows.size()>size;var visible=rows.subList(0,Math.min(size,rows.size()));return new SupplyService.Page<>(visible.stream().map(map).toList(),more?cursors.encode(filter,"",visible.getLast().getId()):null,more); }
 private static SupplyException missing() { return new SupplyException(HttpStatus.NOT_FOUND,"INV-404-001","Owned resource not found."); }
 private static SupplyException invalid(String message) { return new SupplyException(HttpStatus.BAD_REQUEST,"INV-400-001",message); }
 private SupplyException reservationConflict(String message) { metrics.inventoryReservationConflict();return conflict(message); }
 private static SupplyException conflict(String message) { return new SupplyException(HttpStatus.CONFLICT,"INV-409-002",message); }
}
