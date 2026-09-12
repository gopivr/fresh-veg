package com.fresveg.commerce.application.outbox;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxRepository {
    private final JdbcTemplate db;

    public OutboxRepository(JdbcTemplate db) {
        this.db = db;
    }

    public List<OutboxEvent> claimPending(int limit) {
        return db.query("""
                SELECT event_id, aggregate_type, aggregate_id, event_type, payload::text, created_at, retry_count
                FROM commerce.outbox_events
                WHERE status='PENDING'
                ORDER BY created_at,event_id
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """, (r, n) -> new OutboxEvent(r.getObject(1, UUID.class), r.getString(2),
                r.getObject(3, UUID.class), r.getString(4), r.getString(5), r.getTimestamp(6).toInstant(),
                r.getInt(7)), limit);
    }

    public void published(UUID eventId) {
        db.update("UPDATE commerce.outbox_events SET status='PUBLISHED', published_at=CURRENT_TIMESTAMP WHERE event_id=?",
                eventId);
    }

    public void failed(UUID eventId) {
        db.update("UPDATE commerce.outbox_events SET retry_count=retry_count+1 WHERE event_id=?", eventId);
    }

    public long pendingBacklog() {
        Long count = db.queryForObject("SELECT count(*) FROM commerce.outbox_events WHERE status='PENDING'", Long.class);
        return count == null ? 0L : count;
    }

    public void insertForTest(UUID eventId, UUID orderId, String eventType, String payload, Timestamp createdAt) {
        db.update("INSERT INTO commerce.outbox_events(event_id,aggregate_type,aggregate_id,event_type,payload,created_at) VALUES (?,'Order',?,?,?::jsonb,?)",
                eventId, orderId, eventType, payload, createdAt);
    }
}
