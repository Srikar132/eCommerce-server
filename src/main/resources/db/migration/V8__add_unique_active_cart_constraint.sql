-- Add unique constraint to prevent multiple active carts per user
-- This is a partial unique index that only applies to active carts

-- PostgreSQL syntax (conditional unique index)
CREATE UNIQUE INDEX IF NOT EXISTS idx_carts_user_active 
ON carts(user_id) 
WHERE is_active = true AND user_id IS NOT NULL;