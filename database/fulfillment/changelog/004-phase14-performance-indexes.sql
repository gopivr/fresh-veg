CREATE INDEX IF NOT EXISTS idx_delivery_slots_open_window
    ON fulfillment.delivery_slots(start_time, slot_id)
    WHERE status = 'OPEN';

CREATE INDEX IF NOT EXISTS idx_delivery_slots_open_area_window
    ON fulfillment.delivery_slots(service_area, start_time, slot_id)
    WHERE status = 'OPEN';
