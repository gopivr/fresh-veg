package com.fresveg.commerce.infrastructure.outbox;

import com.fresveg.commerce.application.outbox.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "commerce.outbox", name = "publisher", havingValue = "local")
public class LocalOutboxEventPublisher implements OutboxEventPublisher {
    private final List<OutboxEvent> published = new CopyOnWriteArrayList<>();
    private final Set<String> failedTypes;

    public LocalOutboxEventPublisher(@Value("${commerce.outbox.local.fail-event-types:}") String failEventTypes) {
        this.failedTypes = new HashSet<>();
        for (String value : failEventTypes.split(",")) {
            if (!value.isBlank()) {
                this.failedTypes.add(value.trim());
            }
        }
    }

    @Override
    public void publish(OutboxEvent event) {
        if (failedTypes.contains(event.eventType())) {
            throw new IllegalStateException("Local outbox failure for " + event.eventType());
        }
        published.add(event);
        LoggerFactory.getLogger(LocalOutboxEventPublisher.class).info("Published local outbox event {} {}", event.eventType(), event.eventId());
    }

    public List<OutboxEvent> published() {
        return List.copyOf(published);
    }
}
