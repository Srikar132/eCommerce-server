# Cart Race Condition Fix

## Problem
Multiple carts were being created with the same (or different) session IDs due to a race condition in the `CartService.getOrCreateCartForSession()` method.

### Root Cause
The method had a classic **check-then-act** race condition:
1. Two requests arrive simultaneously
2. Both check `cartRepository.findBySessionIdWithItems(sessionId)` and find no cart
3. Both enter the `orElseGet()` lambda
4. Both create and save new carts
5. Result: Multiple carts in database

### Logs Showing the Issue
```
2026-01-13T15:26:25.285 Creating new cart for guest session: sessionId=E01888A82F6ABA6A3D178E6F2B65386B
2026-01-13T15:26:25.285 Creating new cart for guest session: sessionId=46FF30094DB5B06F27ABBE46E10F2B43
```

## Solution Implemented

### 1. Database-Level Protection
Added unique constraint to `session_id` column in the `Cart` entity:
```java
@Column(name = "session_id", length = 255, unique = true)
private String sessionId;
```

### 2. Application-Level Synchronization
Added synchronized locking using `ConcurrentHashMap` to prevent concurrent cart creation:

```java
// Lock maps for preventing race conditions
private final ConcurrentHashMap<String, Object> sessionLocks = new ConcurrentHashMap<>();
private final ConcurrentHashMap<UUID, Object> userLocks = new ConcurrentHashMap<>();

private Cart getOrCreateCartForSession(String sessionId) {
    Object lock = sessionLocks.computeIfAbsent(sessionId, k -> new Object());
    synchronized (lock) {
        return cartRepository.findBySessionIdWithItems(sessionId)
            .orElseGet(() -> {
                // Create cart
            });
    }
}
```

### Why Both Approaches?
- **Database constraint**: Provides data integrity at the database level, prevents duplicates even if application logic fails
- **Application synchronization**: Prevents unnecessary database queries and provides better error handling

## Manual Database Cleanup (If Needed)

If you have existing duplicate carts in the database, run this SQL to clean them up before restarting the application:

```sql
-- Step 1: Find duplicate carts (keep the oldest one for each sessionId)
SELECT session_id, COUNT(*) as count
FROM carts
WHERE session_id IS NOT NULL
GROUP BY session_id
HAVING COUNT(*) > 1;

-- Step 2: Delete duplicates (keep the earliest created cart)
DELETE FROM carts c1
WHERE c1.session_id IS NOT NULL
AND EXISTS (
    SELECT 1 FROM carts c2
    WHERE c2.session_id = c1.session_id
    AND c2.created_at < c1.created_at
);

-- Step 3: Add unique constraint (Hibernate will do this automatically on startup)
ALTER TABLE carts ADD CONSTRAINT uk_cart_session_id UNIQUE (session_id);
```

## Testing
After applying the fix:
1. Restart the Spring Boot application
2. Hibernate will automatically add the unique constraint
3. Test concurrent cart creation by making simultaneous API calls
4. Verify only one cart is created per session ID

## Prevention
- Always use synchronized blocks or transactions for check-then-act operations
- Add database constraints for critical business rules (like unique session IDs)
- Consider using `@Transactional` with proper isolation levels for critical operations
