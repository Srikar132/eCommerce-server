-- ============================================
-- Phone OTP Authentication Database Migration
-- ============================================
-- Run this BEFORE restarting your Docker containers

-- Step 1: Update existing users to set phone_verified = false
UPDATE users 
SET phone_verified = false 
WHERE phone_verified IS NULL;

-- Step 2: Verify the migration
SELECT 
    id, 
    phone, 
    country_code,
    phone_verified, 
    email,
    user_name,
    created_at
FROM users;

-- Expected result: All users should have phone_verified = false (not NULL)

-- ============================================
-- Optional: Clean slate for development
-- ============================================
-- Uncomment ONLY if you want to delete all users and start fresh

-- TRUNCATE TABLE refresh_tokens CASCADE;
-- TRUNCATE TABLE users CASCADE;

-- ============================================
-- How to run this script:
-- ============================================
-- Option 1: Use pgAdmin or any PostgreSQL client
-- Option 2: Use psql command:
--   psql -h <neon-host> -U <username> -d <database> -f database_migration.sql
-- Option 3: Copy-paste the UPDATE statement into your DB client
