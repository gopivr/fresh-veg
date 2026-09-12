DO $$
BEGIN
 LOCK TABLE commerce.refunds, commerce.payment_attempts, commerce.orders IN ACCESS EXCLUSIVE MODE;
 IF EXISTS (SELECT 1 FROM commerce.refunds) OR EXISTS (SELECT 1 FROM commerce.payment_attempts) THEN
  RAISE EXCEPTION 'Refusing to rollback commerce payment tables containing data';
 END IF;
 IF EXISTS (SELECT 1 FROM commerce.orders WHERE status IN ('PAYMENT_AUTHORIZED','CONFIRMED','PAYMENT_FAILED')) THEN
  RAISE EXCEPTION 'Refusing to rollback commerce payment lifecycle data';
 END IF;
 DROP TABLE commerce.refunds;
 DROP TABLE commerce.payment_attempts;
 ALTER TABLE commerce.orders DROP CONSTRAINT orders_status_check;
 ALTER TABLE commerce.orders ADD CONSTRAINT orders_status_check CHECK(status IN ('PENDING_PAYMENT','CANCEL_PENDING','CANCELLED'));
END $$;
