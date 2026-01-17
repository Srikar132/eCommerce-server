-- Migration to fix duplicate carts and add unique constraint on session_id
-- Run this manually if you have existing duplicate carts

-- Step 1: View duplicate carts
SELECT session_id, COUNT(*) as cart_count, MIN(created_at) as first_created, MAX(created_at) as last_created
FROM carts
WHERE session_id IS NOT NULL
GROUP BY session_id
HAVING COUNT(*) > 1
ORDER BY cart_count DESC;

-- Step 2: Delete duplicate carts (keep the oldest one)
-- This will also cascade delete cart_items if configured with ON DELETE CASCADE
DELETE FROM carts
WHERE id IN (
    SELECT c1.id
    FROM carts c1
    INNER JOIN (
        SELECT session_id, MIN(created_at) as first_created
        FROM carts
        WHERE session_id IS NOT NULL
        GROUP BY session_id
        HAVING COUNT(*) > 1
    ) c2 ON c1.session_id = c2.session_id
    WHERE c1.created_at > c2.first_created
);

-- Step 3: Add unique constraint (if not already added by Hibernate)
-- Note: If constraint already exists, you can skip this step
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint 
        WHERE conname = 'uk_cart_session_id'
    ) THEN
        ALTER TABLE carts ADD CONSTRAINT uk_cart_session_id UNIQUE (session_id);
    END IF;
END $$;

-- Verify the fix
SELECT 
    session_id, 
    COUNT(*) as cart_count,
    STRING_AGG(id::text, ', ') as cart_ids
FROM carts
WHERE session_id IS NOT NULL
GROUP BY session_id
HAVING COUNT(*) > 1;
