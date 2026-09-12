package com.fresveg.commerce.infrastructure.outbox;

import com.fresveg.commerce.application.outbox.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "commerce.outbox", name = "publisher", havingValue = "noop", matchIfMissing = true)
public class NoopOutboxEventPublisher implements OutboxEventPublisher {
    @Override
    public void publish(OutboxEvent event) {
        // Intentionally acknowledges events without an external broker for local/dev and isolated tests.
    }
}
