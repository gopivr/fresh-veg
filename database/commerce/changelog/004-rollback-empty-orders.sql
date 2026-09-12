DO $$ BEGIN
 LOCK TABLE commerce.orders,commerce.order_items,commerce.order_status_history,commerce.idempotency_records,commerce.outbox_events IN ACCESS EXCLUSIVE MODE;
 IF EXISTS(SELECT 1 FROM commerce.orders) OR EXISTS(SELECT 1 FROM commerce.order_items) OR EXISTS(SELECT 1 FROM commerce.order_status_history) OR EXISTS(SELECT 1 FROM commerce.idempotency_records) OR EXISTS(SELECT 1 FROM commerce.outbox_events) THEN
  RAISE EXCEPTION 'Order rollback refused: durable business or recovery data exists';
 END IF;
 DROP TABLE commerce.outbox_events,commerce.idempotency_records,commerce.order_status_history,commerce.order_items,commerce.orders;
END $$;
