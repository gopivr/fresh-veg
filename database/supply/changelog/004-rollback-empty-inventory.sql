LOCK TABLE supply.inventory, supply.inventory_batches, supply.inventory_reservations, supply.inventory_transactions IN ACCESS EXCLUSIVE MODE;
DO $$ BEGIN
IF EXISTS(SELECT 1 FROM supply.inventory) OR EXISTS(SELECT 1 FROM supply.inventory_batches) OR EXISTS(SELECT 1 FROM supply.inventory_reservations) OR EXISTS(SELECT 1 FROM supply.inventory_transactions) THEN
RAISE EXCEPTION 'Inventory rollback refused: business data exists' USING ERRCODE='55000'; END IF;
END $$;
DROP TABLE supply.inventory_transactions;
DROP TABLE supply.inventory_reservations;
DROP TABLE supply.inventory_batches;
DROP TABLE supply.inventory;
