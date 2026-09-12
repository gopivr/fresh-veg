CREATE INDEX IF NOT EXISTS idx_vendor_listings_active_offer_scan
    ON supply.vendor_listings(product_id, variant_id, uom_code, minimum_order_quantity, listing_id)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_listing_prices_active_offer_scan
    ON supply.vendor_listing_prices(listing_id, currency, min_quantity, valid_from, valid_to)
    WHERE status = 'ACTIVE';
