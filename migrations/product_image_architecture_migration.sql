-- ========================================
-- PRODUCT IMAGE ARCHITECTURE MIGRATION
-- From: Variant-owned images (embedded)
-- To: Product-level images with M:N variant relationships
-- ========================================

-- STEP 1: Create new variant_images junction table
-- ========================================
CREATE TABLE IF NOT EXISTS variant_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_image_id UUID NOT NULL,
    product_variant_id UUID NOT NULL,
    display_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign keys
    CONSTRAINT fk_variant_images_image FOREIGN KEY (product_image_id) 
        REFERENCES product_images(id) ON DELETE CASCADE,
    CONSTRAINT fk_variant_images_variant FOREIGN KEY (product_variant_id) 
        REFERENCES product_variants(id) ON DELETE CASCADE,
    
    -- Ensure unique image-variant pairs
    CONSTRAINT uq_variant_images_pair UNIQUE(product_image_id, product_variant_id)
);

-- STEP 2: Add indexes for performance
-- ========================================
CREATE INDEX idx_variant_images_variant ON variant_images(product_variant_id);
CREATE INDEX idx_variant_images_image ON variant_images(product_image_id);
CREATE INDEX idx_variant_images_order ON variant_images(product_variant_id, display_order);

-- STEP 3: Update product_images table structure
-- ========================================

-- Add new columns if they don't exist
ALTER TABLE product_images 
ADD COLUMN IF NOT EXISTS image_type VARCHAR(50) DEFAULT 'PREVIEW_BASE';

-- Update existing NULL values
UPDATE product_images 
SET image_type = 'PREVIEW_BASE' 
WHERE image_type IS NULL;

-- Add index on product_id if not exists
CREATE INDEX IF NOT EXISTS idx_product_images_product 
ON product_images(product_id);

-- Add composite index for primary images
CREATE INDEX IF NOT EXISTS idx_product_images_primary 
ON product_images(product_id, is_primary);

-- STEP 4: Data Migration (if you have existing data)
-- ========================================

-- NOTE: This section assumes you're migrating from a system where images
-- were stored directly on variants. Adjust based on your actual schema.

-- Example: If you had variant_images as an array column or separate table
-- You would need to:
-- 1. Create ProductImage records for each unique image
-- 2. Create VariantImage junction records linking them

-- Example migration (CUSTOMIZE BASED ON YOUR SCHEMA):
/*
-- Create unique images from variant data
INSERT INTO product_images (id, product_id, image_url, alt_text, display_order, is_primary, image_type)
SELECT 
    gen_random_uuid(),
    pv.product_id,
    vi.image_url,
    vi.alt_text,
    vi.display_order,
    vi.is_primary,
    COALESCE(vi.image_role, 'PREVIEW_BASE')
FROM variant_images_old vi
JOIN product_variants pv ON vi.variant_id = pv.id
ON CONFLICT DO NOTHING;

-- Create junction table entries
INSERT INTO variant_images (id, product_image_id, product_variant_id, display_order)
SELECT 
    gen_random_uuid(),
    pi.id,
    pv.id,
    0
FROM product_images pi
JOIN product_variants pv ON pi.product_id = pv.product_id
-- Add additional conditions based on your logic
;
*/

-- STEP 5: Verify data integrity
-- ========================================

-- Check for orphaned images (images not linked to any variant)
SELECT pi.id, pi.image_url, p.name as product_name
FROM product_images pi
LEFT JOIN variant_images vi ON pi.id = vi.product_image_id
JOIN products p ON pi.product_id = p.id
WHERE vi.id IS NULL;

-- Check for variants without images
SELECT pv.id, pv.sku, p.name as product_name
FROM product_variants pv
LEFT JOIN variant_images vi ON pv.id = vi.product_variant_id
JOIN products p ON pv.product_id = p.id
WHERE vi.id IS NULL;

-- Count images per product
SELECT 
    p.id,
    p.name,
    COUNT(DISTINCT pi.id) as total_images,
    COUNT(DISTINCT vi.product_variant_id) as variants_with_images
FROM products p
LEFT JOIN product_images pi ON p.id = pi.product_id
LEFT JOIN variant_images vi ON pi.id = vi.product_image_id
GROUP BY p.id, p.name
ORDER BY total_images DESC;

-- STEP 6: Clean up (ONLY AFTER VERIFYING MIGRATION)
-- ========================================

-- Drop old image tables if they exist
-- DROP TABLE IF EXISTS old_variant_images CASCADE;

-- Remove old columns from variants if they exist
-- ALTER TABLE product_variants DROP COLUMN IF EXISTS images_json;

-- STEP 7: Grant permissions (if needed)
-- ========================================

-- GRANT SELECT, INSERT, UPDATE, DELETE ON variant_images TO your_app_user;
-- GRANT USAGE, SELECT ON SEQUENCE variant_images_id_seq TO your_app_user;

-- ========================================
-- ROLLBACK SCRIPT (Keep for safety)
-- ========================================

/*
-- To rollback this migration:

-- 1. Drop junction table
DROP TABLE IF EXISTS variant_images CASCADE;

-- 2. Remove new columns
ALTER TABLE product_images DROP COLUMN IF EXISTS image_type;

-- 3. Drop new indexes
DROP INDEX IF EXISTS idx_variant_images_variant;
DROP INDEX IF EXISTS idx_variant_images_image;
DROP INDEX IF EXISTS idx_variant_images_order;
DROP INDEX IF EXISTS idx_product_images_primary;

-- 4. Restore old structure (if you backed it up)
-- ... restore from backup ...
*/

-- ========================================
-- PERFORMANCE TUNING (Optional)
-- ========================================

-- Analyze tables for query optimization
ANALYZE product_images;
ANALYZE variant_images;
ANALYZE product_variants;
ANALYZE products;

-- Update statistics
VACUUM ANALYZE product_images;
VACUUM ANALYZE variant_images;

-- ========================================
-- TESTING QUERIES
-- ========================================

-- Test: Get all images for a variant
SELECT 
    pi.id,
    pi.image_url,
    pi.is_primary,
    pi.image_type,
    vi.display_order
FROM variant_images vi
JOIN product_images pi ON vi.product_image_id = pi.id
WHERE vi.product_variant_id = 'YOUR_VARIANT_ID'
ORDER BY vi.display_order, pi.is_primary DESC;

-- Test: Get all variants using an image
SELECT 
    pv.id,
    pv.sku,
    pv.size,
    pv.color,
    vi.display_order
FROM variant_images vi
JOIN product_variants pv ON vi.product_variant_id = pv.id
WHERE vi.product_image_id = 'YOUR_IMAGE_ID';

-- Test: Get primary image for each product
SELECT DISTINCT ON (p.id)
    p.id,
    p.name,
    pi.image_url as primary_image
FROM products p
JOIN product_images pi ON p.id = pi.product_id
WHERE pi.is_primary = true
ORDER BY p.id, pi.created_at;

-- ========================================
-- MIGRATION COMPLETE ✅
-- ========================================

-- Log the migration
INSERT INTO schema_migrations (version, description, executed_at)
VALUES ('2026_01_30_001', 'Product Image Architecture Migration - M:N Variant Relationships', CURRENT_TIMESTAMP);

-- Success message
SELECT '✅ Product Image Architecture Migration Complete!' as status;
