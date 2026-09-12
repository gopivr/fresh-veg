package com.fresveg.commerce.infrastructure.outbox;

import com.fresveg.commerce.application.outbox.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "commerce.outbox", name = "publisher", havingValue = "kafka")
public class KafkaOutboxEventPublisher implements OutboxEventPublisher {
    private final KafkaTemplate<String, String> kafka;
    private final String topic;
    private final Duration timeout;

    public KafkaOutboxEventPublisher(KafkaTemplate<String, String> kafka,
            @Value("${commerce.outbox.kafka.topic:fresveg.events.v1}") String topic,
            @Value("${commerce.outbox.kafka.timeout-ms:5000}") long timeoutMs) {
        this.kafka = kafka;
        this.topic = topic;
        this.timeout = Duration.ofMillis(Math.max(1, timeoutMs));
    }

    @Override
    public void publish(OutboxEvent event) {
        try {
            kafka.send(topic, event.aggregateId().toString(), event.payload()).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception error) {
            throw new IllegalStateException("Kafka publication failed", error);
        }
    }
}
