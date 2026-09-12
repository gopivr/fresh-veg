package com.fresveg.supply.infrastructure.config;
import com.fresveg.supply.application.InventoryService;
import com.fresveg.supply.infrastructure.persistence.InventoryReservationRepository;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
import org.slf4j.LoggerFactory;
@Configuration(proxyBeanMethods=false) @EnableScheduling
@ConditionalOnProperty(name="supply.inventory.expiration-enabled",havingValue="true",matchIfMissing=true)
public class InventoryExpiration {
 private final InventoryService service;private final InventoryReservationRepository reservations;private final Clock clock;
 public InventoryExpiration(InventoryService service,InventoryReservationRepository reservations,Clock clock) { this.service=service;this.reservations=reservations;this.clock=clock; }
 @Scheduled(fixedDelayString="${supply.inventory.expiration-delay-ms:30000}",initialDelayString="${supply.inventory.expiration-delay-ms:30000}")
 public void expire() { for(var id:reservations.expiredInventories(clock.instant())) { try { service.expireInventory(id); } catch(RuntimeException error) { LoggerFactory.getLogger(getClass()).error("Inventory expiration failed ({})",error.getClass().getSimpleName()); } } }
}
