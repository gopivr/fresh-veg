package com.fresveg.fulfillment.application;
import com.fresveg.common.cache.*;
import com.fresveg.fulfillment.api.dto.FulfillmentContracts.*;
import com.fresveg.fulfillment.infrastructure.persistence.FulfillmentRepository;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @Transactional
public class FulfillmentService {
 private final FulfillmentRepository repo;private final FulfillmentServiceAuthorization services;private final Clock clock;private final TtlCache cache;private final Duration cacheTtl;
 public FulfillmentService(FulfillmentRepository repo,FulfillmentServiceAuthorization services,Clock clock,TtlCache cache,CacheProperties cacheProperties){this.repo=repo;this.services=services;this.clock=clock;this.cache=cache;this.cacheTtl=cacheProperties.getTtl();}
 @Transactional(readOnly=true) public List<DeliverySlotResponse> slots(String area,Instant from,Instant to,int size){if(size<1||size>100)throw invalid("pageSize must be 1-100.");var start=from==null?clock.instant():from;var end=to==null?start.plus(Duration.ofDays(14)):to;if(!end.isAfter(start)||end.isAfter(start.plus(Duration.ofDays(31))))throw invalid("Invalid slot window.");String key="fulfillment:slots:"+Objects.toString(area,"")+":"+start+":"+end+":"+size;return cache.get(key,cacheTtl,()->repo.slots(area,start,end,size));}
 @PreAuthorize("@fulfillmentServiceAuthorization.allowed()") public FulfillmentResponse create(CreateFulfillmentRequest r){if(!r.requestedEnd().isAfter(r.requestedStart()))throw invalid("Fulfillment delivery window is invalid.");var result=repo.one(repo.create(r,services.actor()),false);cache.evictByPrefix("fulfillment:slots:");return result;}
 @PreAuthorize("isAuthenticated()") @Transactional(readOnly=true) public FulfillmentResponse read(UUID id){return repo.one(id,false);}
 @PreAuthorize("isAuthenticated()") @Transactional(readOnly=true) public TrackingResponse tracking(UUID id){return repo.tracking(id);}
 @PreAuthorize("@fulfillmentServiceAuthorization.allowed()") public FulfillmentResponse transition(UUID id,TransitionRequest r){return repo.transition(id,r.status(),r.version(),r.description(),services.actor());}
 @PreAuthorize("@fulfillmentServiceAuthorization.allowed()") public DeliverySlotResponse seed(DeliverySlotSeedRequest r){if(!r.endTime().isAfter(r.startTime()))throw invalid("Delivery slot window is invalid.");var result=repo.slot(repo.seedSlot(r));cache.evictByPrefix("fulfillment:slots:");return result;}
 private static FulfillmentException invalid(String m){return new FulfillmentException(HttpStatus.BAD_REQUEST,"FUL-400-001",m);}
}
