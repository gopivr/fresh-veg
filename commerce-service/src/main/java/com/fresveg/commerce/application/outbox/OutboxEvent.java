package com.fresveg.commerce.application.outbox;

import java.time.Instant;
import java.util.UUID;

public record OutboxEvent(UUID eventId, String aggregateType, UUID aggregateId, String eventType,
        String payload, Instant createdAt, int retryCount) {
}
