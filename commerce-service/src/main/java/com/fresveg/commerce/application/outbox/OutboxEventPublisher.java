package com.fresveg.commerce.application.outbox;

public interface OutboxEventPublisher {
    void publish(OutboxEvent event);
}
