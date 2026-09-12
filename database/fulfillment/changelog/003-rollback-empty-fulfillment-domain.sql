DO $$
BEGIN
 LOCK TABLE fulfillment.delivery_assignments, fulfillment.shipment_events, fulfillment.shipments, fulfillment.fulfillment_items, fulfillment.fulfillments, fulfillment.delivery_slots IN ACCESS EXCLUSIVE MODE;
 IF EXISTS (SELECT 1 FROM fulfillment.delivery_assignments) OR EXISTS (SELECT 1 FROM fulfillment.shipment_events) OR EXISTS (SELECT 1 FROM fulfillment.shipments) OR EXISTS (SELECT 1 FROM fulfillment.fulfillment_items) OR EXISTS (SELECT 1 FROM fulfillment.fulfillments) OR EXISTS (SELECT 1 FROM fulfillment.delivery_slots) THEN
  RAISE EXCEPTION 'Fulfillment rollback refused: business data exists';
 END IF;
 DROP TABLE fulfillment.delivery_assignments;
 DROP TABLE fulfillment.shipment_events;
 DROP TABLE fulfillment.shipments;
 DROP TABLE fulfillment.fulfillment_items;
 DROP TABLE fulfillment.fulfillments;
 DROP TABLE fulfillment.delivery_slots;
END $$;
