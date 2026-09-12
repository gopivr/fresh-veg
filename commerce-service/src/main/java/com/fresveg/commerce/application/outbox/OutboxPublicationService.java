package com.fresveg.commerce.application.outbox;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OutboxPublicationService {
    private final OutboxRepository events;
    private final OutboxEventPublisher publisher;
    private final TransactionTemplate tx;
    private final int batchSize;
    private final boolean scheduled;

    public OutboxPublicationService(OutboxRepository events, OutboxEventPublisher publisher,
            PlatformTransactionManager manager,
            @Value("${commerce.outbox.batch-size:25}") int batchSize,
            @Value("${commerce.outbox.scheduler-enabled:true}") boolean scheduled) {
        this.events = events;
        this.publisher = publisher;
        this.tx = new TransactionTemplate(manager);
        this.tx.setTimeout(30);
        this.batchSize = Math.max(1, Math.min(batchSize, 100));
        this.scheduled = scheduled;
    }

    @Scheduled(fixedDelayString = "${commerce.outbox.poll-delay-ms:5000}", initialDelayString = "${commerce.outbox.initial-delay-ms:5000}")
    public void scheduledPublish() {
        if (scheduled) {
            publishPending();
        }
    }

    public int publishPending() {
        return tx.execute(status -> {
            int count = 0;
            for (var event : events.claimPending(batchSize)) {
                try {
                    publisher.publish(event);
                    events.published(event.eventId());
                    count++;
                } catch (RuntimeException error) {
                    events.failed(event.eventId());
                    LoggerFactory.getLogger(OutboxPublicationService.class).warn("Outbox publication deferred for {} ({})",
                            event.eventId(), error.getClass().getSimpleName());
                }
            }
            return count;
        });
    }
}
