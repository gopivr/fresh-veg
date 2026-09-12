LOCK TABLE commerce.carts, commerce.cart_items IN ACCESS EXCLUSIVE MODE;
DO $$ BEGIN
IF EXISTS(SELECT 1 FROM commerce.carts) OR EXISTS(SELECT 1 FROM commerce.cart_items) THEN
RAISE EXCEPTION 'Cart rollback refused: business data exists' USING ERRCODE='55000'; END IF;
END $$;
DROP TABLE commerce.cart_items;
DROP TABLE commerce.carts;
