-- Guard and drops run atomically under the changeset transaction.
LOCK TABLE catalog.units_of_measure, catalog.categories, catalog.products, catalog.product_variants,
    catalog.product_categories, catalog.product_images IN ACCESS EXCLUSIVE MODE;
DO $rollback$
BEGIN
    IF EXISTS (SELECT 1 FROM catalog.products) OR EXISTS (SELECT 1 FROM catalog.categories)
       OR EXISTS (SELECT 1 FROM catalog.product_variants) OR EXISTS (SELECT 1 FROM catalog.product_categories)
       OR EXISTS (SELECT 1 FROM catalog.product_images) OR (SELECT count(*) FROM catalog.units_of_measure) <> 5 THEN
        RAISE EXCEPTION 'Catalog rollback refused: business data exists' USING ERRCODE = '55000';
    END IF;
END
$rollback$;
DROP TABLE catalog.product_images;
DROP TABLE catalog.product_categories;
DROP TABLE catalog.product_variants;
DROP TABLE catalog.products;
DROP TABLE catalog.categories;
DROP TABLE catalog.units_of_measure;
