-- =====================================================
-- Refresh Token Table Migration - Add Token Rotation Support
-- =====================================================
-- This migration adds the missing column for token rotation tracking
-- Run this script on your database to fix the schema validation error

-- Add replaced_by_token_id column if it doesn't exist
ALTER TABLE refresh_tokens 
ADD COLUMN IF NOT EXISTS replaced_by_token_id VARCHAR(255);

-- Create index for better performance (optional but recommended)
CREATE INDEX IF NOT EXISTS idx_replaced_by_token_id 
ON refresh_tokens(replaced_by_token_id);

-- Verify the column was added
SELECT column_name, data_type, is_nullable 
FROM information_schema.columns 
WHERE table_name = 'refresh_tokens' 
ORDER BY ordinal_position;

-- Expected columns:
-- id (uuid)
-- token_id (varchar)
-- user_id (uuid)
-- expires_at (timestamp)
-- created_at (timestamp)
-- revoked (boolean)
-- revoked_at (timestamp)
-- replaced_by_token_id (varchar) <- NEW COLUMN

-- =====================================================
-- ALTERNATIVE: If refresh_tokens table doesn't exist, create it
-- =====================================================

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_id VARCHAR(255) NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMP,
    replaced_by_token_id VARCHAR(255),
    
    CONSTRAINT fk_refresh_token_user 
        FOREIGN KEY (user_id) 
        REFERENCES users(id) 
        ON DELETE CASCADE
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_token_id ON refresh_tokens(token_id);
CREATE INDEX IF NOT EXISTS idx_user_id ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_expires_at ON refresh_tokens(expires_at);
CREATE INDEX IF NOT EXISTS idx_replaced_by_token_id ON refresh_tokens(replaced_by_token_id);

-- =====================================================
-- Cleanup: Remove expired and revoked tokens (optional)
-- =====================================================

-- Delete expired tokens
-- DELETE FROM refresh_tokens WHERE expires_at < NOW();

-- Delete revoked tokens older than 30 days
-- DELETE FROM refresh_tokens 
-- WHERE revoked = TRUE 
-- AND revoked_at < NOW() - INTERVAL '30 days';

COMMIT;
