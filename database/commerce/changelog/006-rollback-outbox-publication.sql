REVOKE UPDATE(status,published_at,retry_count) ON commerce.outbox_events FROM fresveg_commerce;
COMMENT ON TABLE commerce.outbox_events IS 'Order event inserted atomically with order state. Publication belongs to Phase 12.';
