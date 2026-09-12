ALTER TABLE commerce.orders DROP CONSTRAINT orders_status_check;
ALTER TABLE commerce.orders ADD CONSTRAINT orders_status_check CHECK(status IN ('PENDING_PAYMENT','PAYMENT_AUTHORIZED','CONFIRMED','PAYMENT_FAILED','CANCEL_PENDING','CANCELLED'));
CREATE TABLE commerce.payment_attempts (
 payment_attempt_id UUID PRIMARY KEY, order_id UUID NOT NULL REFERENCES commerce.orders(order_id),
 idempotency_key VARCHAR(160) NOT NULL, payment_method_id UUID NOT NULL, provider VARCHAR(40) NOT NULL,
 provider_reference VARCHAR(120), status VARCHAR(24) NOT NULL CHECK(status IN ('AUTHORIZED','FAILED')),
 currency VARCHAR(3) NOT NULL CHECK(currency ~ '^[A-Z]{3}$'), amount NUMERIC(38,6) NOT NULL CHECK(amount>=0),
 failure_code VARCHAR(80), failure_message VARCHAR(240), requested_at TIMESTAMPTZ NOT NULL, completed_at TIMESTAMPTZ NOT NULL,
 UNIQUE(order_id,idempotency_key));
CREATE INDEX idx_payment_attempts_order ON commerce.payment_attempts(order_id,completed_at);
CREATE TABLE commerce.refunds (
 refund_id UUID PRIMARY KEY, order_id UUID NOT NULL REFERENCES commerce.orders(order_id),
 payment_attempt_id UUID NOT NULL REFERENCES commerce.payment_attempts(payment_attempt_id), idempotency_key VARCHAR(160) NOT NULL,
 provider VARCHAR(40) NOT NULL, provider_reference VARCHAR(120), status VARCHAR(24) NOT NULL CHECK(status IN ('COMPLETED','FAILED')),
 currency VARCHAR(3) NOT NULL CHECK(currency ~ '^[A-Z]{3}$'), amount NUMERIC(38,6) NOT NULL CHECK(amount>=0),
 failure_code VARCHAR(80), failure_message VARCHAR(240), requested_at TIMESTAMPTZ NOT NULL, completed_at TIMESTAMPTZ NOT NULL,
 UNIQUE(order_id,idempotency_key));
CREATE INDEX idx_refunds_order ON commerce.refunds(order_id,completed_at);
GRANT SELECT,INSERT ON commerce.payment_attempts,commerce.refunds TO fresveg_commerce;
COMMENT ON TABLE commerce.payment_attempts IS 'Provider-neutral payment authorization attempts. Raw card or bank data must never be stored.';
COMMENT ON TABLE commerce.refunds IS 'Provider-neutral refund results for confirmed orders. Raw payment credentials must never be stored.';
