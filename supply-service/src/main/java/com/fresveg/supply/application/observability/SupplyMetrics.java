package com.fresveg.supply.application.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class SupplyMetrics {
    private final Counter inventoryReservationConflicts;

    public SupplyMetrics(MeterRegistry registry) {
        this.inventoryReservationConflicts = Counter.builder("fresveg.inventory.reservation.conflicts")
                .description("Inventory reservation conflicts caused by insufficient stock or conflicting reservation intent")
                .register(registry);
    }

    public void inventoryReservationConflict() {
        inventoryReservationConflicts.increment();
    }
}
