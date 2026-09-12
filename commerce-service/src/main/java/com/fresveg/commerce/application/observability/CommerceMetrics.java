package com.fresveg.commerce.application.observability;

import com.fresveg.commerce.application.outbox.OutboxRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class CommerceMetrics {
    private final Counter orderCreated;
    private final Counter checkoutFailures;
    private final Counter paymentFailures;

    public CommerceMetrics(MeterRegistry registry, OutboxRepository outbox) {
        this.orderCreated = Counter.builder("fresveg.orders.created")
                .description("Orders durably created by Commerce")
                .register(registry);
        this.checkoutFailures = Counter.builder("fresveg.checkout.failures")
                .description("Checkout preview or order checkout failures")
                .register(registry);
        this.paymentFailures = Counter.builder("fresveg.payment.failures")
                .description("Failed payment authorizations or refunds")
                .register(registry);
        Gauge.builder("fresveg.outbox.backlog", outbox, OutboxRepository::pendingBacklog)
                .description("Pending Commerce outbox events waiting for publication")
                .register(registry);
    }

    public void orderCreated() {
        orderCreated.increment();
    }

    public void checkoutFailure() {
        checkoutFailures.increment();
    }

    public void paymentFailure() {
        paymentFailures.increment();
    }
}
