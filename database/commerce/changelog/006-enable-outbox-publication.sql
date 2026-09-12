GRANT UPDATE(status,published_at,retry_count) ON commerce.outbox_events TO fresveg_commerce;
COMMENT ON TABLE commerce.outbox_events IS 'Transactional order event outbox. Events are inserted with business changes and later published by the Phase 12 publisher; runtime may only update publish status, timestamp and retry count.';
