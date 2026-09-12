LOCK TABLE supply.vendor_locations, supply.vendor_listings, supply.vendor_listing_prices, supply.price_tiers IN ACCESS EXCLUSIVE MODE;
DO $rollback$
BEGIN
    IF EXISTS (SELECT 1 FROM supply.vendor_locations) OR EXISTS (SELECT 1 FROM supply.vendor_listings)
       OR EXISTS (SELECT 1 FROM supply.vendor_listing_prices) OR EXISTS (SELECT 1 FROM supply.price_tiers) THEN
        RAISE EXCEPTION 'Supply rollback refused: business data exists' USING ERRCODE = '55000';
    END IF;
END
$rollback$;
DROP TABLE supply.price_tiers;
DROP TABLE supply.vendor_listing_prices;
DROP TABLE supply.vendor_listings;
DROP TABLE supply.vendor_locations;
