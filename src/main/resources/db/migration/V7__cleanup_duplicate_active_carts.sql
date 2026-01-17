-- Cleanup duplicate active carts
-- Keep only the most recently updated cart for each user, deactivate others

-- First, identify and deactivate duplicate carts (keep the most recent one)
UPDATE carts c1
SET is_active = false
WHERE c1.is_active = true
  AND c1.user_id IS NOT NULL
  AND EXISTS (
    SELECT 1 
    FROM carts c2 
    WHERE c2.user_id = c1.user_id 
      AND c2.is_active = true
      AND c2.updated_at > c1.updated_at
  );

-- Log the cleanup (optional - comment out if not needed)
-- SELECT user_id, COUNT(*) as total_carts, 
--        SUM(CASE WHEN is_active THEN 1 ELSE 0 END) as active_carts
-- FROM carts
-- WHERE user_id IS NOT NULL
-- GROUP BY user_id
-- HAVING COUNT(*) > 1;