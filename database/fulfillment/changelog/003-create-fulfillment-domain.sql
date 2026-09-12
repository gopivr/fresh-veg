CREATE TABLE fulfillment.delivery_slots (
 slot_id UUID PRIMARY KEY, service_area VARCHAR(80) NOT NULL, start_time TIMESTAMPTZ NOT NULL, end_time TIMESTAMPTZ NOT NULL,
 capacity INTEGER NOT NULL, reserved_capacity INTEGER NOT NULL DEFAULT 0, status VARCHAR(20) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT ck_delivery_slots_window CHECK(end_time>start_time), CONSTRAINT ck_delivery_slots_capacity CHECK(capacity>=0 AND reserved_capacity>=0 AND reserved_capacity<=capacity),
 CONSTRAINT ck_delivery_slots_status CHECK(status IN ('OPEN','CLOSED','CANCELLED'))
);
CREATE INDEX idx_delivery_slots_area_time ON fulfillment.delivery_slots(service_area,start_time,slot_id);
CREATE TABLE fulfillment.fulfillments (
 fulfillment_id UUID PRIMARY KEY, order_id UUID NOT NULL, customer_id UUID NOT NULL, delivery_slot_id UUID NOT NULL REFERENCES fulfillment.delivery_slots(slot_id),
 status VARCHAR(24) NOT NULL, service_area VARCHAR(80) NOT NULL, delivery_address JSONB NOT NULL, requested_start TIMESTAMPTZ NOT NULL, requested_end TIMESTAMPTZ NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, created_by UUID, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_by UUID, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uk_fulfillments_order UNIQUE(order_id), CONSTRAINT ck_fulfillments_status CHECK(status IN ('CREATED','PACKING','READY_FOR_DELIVERY','OUT_FOR_DELIVERY','DELIVERED','CANCELLED'))
);
CREATE INDEX idx_fulfillments_customer ON fulfillment.fulfillments(customer_id,fulfillment_id);
CREATE TABLE fulfillment.fulfillment_items (
 fulfillment_item_id UUID PRIMARY KEY, fulfillment_id UUID NOT NULL REFERENCES fulfillment.fulfillments(fulfillment_id), order_item_id UUID NOT NULL,
 product_id UUID NOT NULL, listing_id UUID NOT NULL, vendor_id UUID NOT NULL, product_name VARCHAR(240) NOT NULL, quantity NUMERIC(18,6) NOT NULL,
 unit_code VARCHAR(32) NOT NULL, status VARCHAR(24) NOT NULL DEFAULT 'CREATED', created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_fulfillment_items_order_item UNIQUE(order_item_id), CONSTRAINT ck_fulfillment_items_quantity CHECK(quantity>0),
 CONSTRAINT ck_fulfillment_items_status CHECK(status IN ('CREATED','PACKED','SUBSTITUTED','CANCELLED'))
);
CREATE INDEX idx_fulfillment_items_fulfillment ON fulfillment.fulfillment_items(fulfillment_id);
CREATE TABLE fulfillment.shipments (
 shipment_id UUID PRIMARY KEY, fulfillment_id UUID NOT NULL REFERENCES fulfillment.fulfillments(fulfillment_id), carrier VARCHAR(80), tracking_reference VARCHAR(120),
 status VARCHAR(24) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT ck_shipments_status CHECK(status IN ('PENDING','ASSIGNED','OUT_FOR_DELIVERY','DELIVERED','FAILED','CANCELLED'))
);
CREATE INDEX idx_shipments_fulfillment ON fulfillment.shipments(fulfillment_id);
CREATE TABLE fulfillment.shipment_events (
 event_id UUID PRIMARY KEY, shipment_id UUID NOT NULL REFERENCES fulfillment.shipments(shipment_id), status VARCHAR(24) NOT NULL, description VARCHAR(240) NOT NULL,
 occurred_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_shipment_events_shipment_time ON fulfillment.shipment_events(shipment_id,occurred_at,event_id);
CREATE TABLE fulfillment.delivery_assignments (
 assignment_id UUID PRIMARY KEY, shipment_id UUID NOT NULL REFERENCES fulfillment.shipments(shipment_id), assignee_id UUID, vehicle_reference VARCHAR(80),
 status VARCHAR(24) NOT NULL, assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, completed_at TIMESTAMPTZ,
 CONSTRAINT ck_delivery_assignments_status CHECK(status IN ('ASSIGNED','COMPLETED','CANCELLED'))
);
CREATE INDEX idx_delivery_assignments_shipment ON fulfillment.delivery_assignments(shipment_id);
GRANT SELECT ON fulfillment.delivery_slots TO fresveg_fulfillment;
GRANT SELECT,INSERT,UPDATE(status,reserved_capacity,updated_at,version) ON fulfillment.delivery_slots TO fresveg_fulfillment;
GRANT SELECT,INSERT,UPDATE(status,updated_at,updated_by,version) ON fulfillment.fulfillments TO fresveg_fulfillment;
GRANT SELECT,INSERT,UPDATE(status,updated_at,version) ON fulfillment.shipments TO fresveg_fulfillment;
GRANT SELECT,INSERT,UPDATE(status) ON fulfillment.fulfillment_items TO fresveg_fulfillment;
GRANT SELECT,INSERT ON fulfillment.shipment_events,fulfillment.delivery_assignments TO fresveg_fulfillment;
