-- ============================================
-- Category Type Support Migration
-- ============================================
-- Run this BEFORE restarting your application
-- 
-- This migration adds support for category type filtering:
-- - Adds category_type column to categories table
-- - Enables Admin Dashboard filtering (All/Men/Women/Kids tabs)
-- - Maintains backward compatibility (column is nullable)

-- Step 1: Add category_type column if it doesn't exist
ALTER TABLE categories
ADD COLUMN IF NOT EXISTS category_type VARCHAR(50);

-- Step 2: Create enum type for CategoryType (if using PostgreSQL)
-- Uncomment if you're using PostgreSQL
-- CREATE TYPE category_type_enum AS ENUM ('MEN', 'WOMEN', 'KIDS');
-- ALTER TABLE categories ALTER COLUMN category_type TYPE category_type_enum USING category_type::category_type_enum;

-- Step 3: Verify the migration
SELECT 
    id, 
    name,
    slug,
    parent_id,
    category_type,
    is_active,
    created_at
FROM categories
ORDER BY display_order ASC;

-- Expected result: All categories should now have a category_type column
-- (initially NULL for existing categories - will be resolved at runtime via parent hierarchy)

-- Step 4: Optional - Set category types for root categories
-- Uncomment and modify the UUIDs and types based on your actual root categories
-- UPDATE categories 
-- SET category_type = 'MEN'
-- WHERE slug = 'men' AND parent_id IS NULL;
--
-- UPDATE categories 
-- SET category_type = 'WOMEN'
-- WHERE slug = 'women' AND parent_id IS NULL;
--
-- UPDATE categories 
-- SET category_type = 'KIDS'
-- WHERE slug = 'kids' AND parent_id IS NULL;

-- ============================================
-- How to run this script:
-- ============================================
-- Option 1: Use pgAdmin or any PostgreSQL client
-- Option 2: Use psql command:
--   psql -h <neon-host> -U <username> -d <database> -f category_type_migration.sql
-- Option 3: Copy-paste the statements into your DB client
-- Option 4: Use Spring Boot JPA - JPA will auto-create the column on first run
--           (ensure spring.jpa.hibernate.ddl-auto=update in application.properties)

