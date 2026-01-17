# Cart Sync Duplicate Issue - Fix Documentation

## Problem Summary

When users logged in and synced their local cart, the system was creating **duplicate carts**:
- One cart with the synced items ✅
- Another empty cart ❌

This caused the error:
```
org.springframework.dao.IncorrectResultSizeDataAccessException: 
Query did not return a unique result: 2 results were returned
```

## Root Causes

### 1. **Stale Cart Reference in Sync Loop**
```java
// BEFORE (BROKEN):
request.getItems().forEach(localItem -> {
    addItem(cart, addRequest);  // ❌ Each call saves and returns NEW cart
    // But we keep using the OLD cart reference!
});
```

**Issue**: `addItem()` saves the cart after each operation and returns a new response, but the loop continued using the stale `cart` reference. This caused Hibernate to lose track of the cart entity, potentially creating duplicates.

### 2. **Multiple Saves During Sync**
Each `addItem()` call triggered:
- Entity save
- Database flush
- Potential session refresh

This created race conditions where multiple carts could be created for the same user.

### 3. **No Concurrency Protection in Sync**
The frontend could trigger multiple sync attempts before the first one completed.

### 4. **No Database Constraint**
There was no database-level constraint preventing multiple active carts per user.

## Solutions Implemented

### 1. Backend: Atomic Bulk Sync ✅

**File**: `CartService.java`

```java
public CartResponse syncLocalCart(User user, SyncLocalCartRequest request) {
    // Use the same lock as getOrCreateCart
    Object lock = userLocks.computeIfAbsent(user.getId(), k -> new Object());
    
    synchronized (lock) {
        Cart cart = getOrCreateCart(user);
        
        // Add ALL items WITHOUT saving in between
        for (SyncLocalCartRequest.LocalCartItemRequest localItem : request.getItems()) {
            UUID customizationId = resolveCustomizationId(user, localItem);
            AddToCartRequest addRequest = buildAddRequest(localItem, customizationId);
            addItemWithoutSave(cart, addRequest); // ✅ No save!
        }
        
        // Save ONCE after all items are added
        cart.recalculateTotals();
        Cart savedCart = cartRepository.save(cart);
        
        return cartMapper.toCartResponse(savedCart);
    }
}
```

**New Helper Method**:
```java
private void addItemWithoutSave(Cart cart, AddToCartRequest request) {
    // Same logic as addItem() but WITHOUT calling save()
    // Caller must save the cart after all operations
}
```

**Benefits**:
- ✅ Single database save operation
- ✅ No stale references
- ✅ Thread-safe with synchronized block
- ✅ Proper transaction boundary

### 2. Frontend: Prevent Concurrent Syncs ✅

**File**: `cart-provider.tsx`

```typescript
const hasSyncedRef = useRef(false);
const isSyncingRef = useRef(false); // ✅ NEW: Prevent concurrent syncs

const syncLocalCartToBackend = async () => {
    // Prevent concurrent syncs
    if (!isAuthenticated || !user || hasSyncedRef.current || isSyncingRef.current) {
        return; // ✅ Exit early if sync in progress
    }
    
    isSyncingRef.current = true; // ✅ Lock
    
    try {
        await syncCartMutation.mutateAsync(itemsToSync);
        localCartManager.clear();
        hasSyncedRef.current = true;
        toast.success("Your cart has been synced!");
    } finally {
        isSyncingRef.current = false; // ✅ Always unlock
    }
};
```

**Benefits**:
- ✅ Prevents multiple concurrent sync requests
- ✅ Proper lock/unlock with try-finally
- ✅ Resets lock on logout

### 3. Database: Cleanup Duplicates ✅

**File**: `V7__cleanup_duplicate_active_carts.sql`

```sql
-- Keep only the most recently updated cart, deactivate others
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
```

### 4. Database: Prevent Future Duplicates ✅

**File**: `V8__add_unique_active_cart_constraint.sql`

```sql
-- Unique partial index: Only one active cart per user
CREATE UNIQUE INDEX IF NOT EXISTS idx_carts_user_active 
ON carts(user_id) 
WHERE is_active = true AND user_id IS NOT NULL;
```

**Benefits**:
- ✅ Database-level enforcement
- ✅ Only applies to active carts (partial index)
- ✅ Allows multiple inactive carts (for history)

## Testing Steps

1. **Restart Spring Boot Application**
   - Flyway will automatically run V7 and V8 migrations
   - Duplicate carts will be cleaned up
   - Unique constraint will be added

2. **Test Cart Sync**
   - Add items to cart as guest
   - Login to existing account
   - Verify all items appear in single cart
   - Check database: `SELECT * FROM carts WHERE user_id = ? AND is_active = true`
   - Should return exactly 1 cart

3. **Test Concurrent Protection**
   - Add items to cart as guest
   - Login and immediately refresh page
   - Verify no duplicate carts created

## Expected Behavior After Fix

✅ **Single Active Cart**: Each user has exactly one active cart
✅ **Atomic Sync**: All items synced in single transaction
✅ **No Race Conditions**: Synchronized lock prevents concurrent issues
✅ **Database Constraint**: Impossible to create duplicate active carts
✅ **Clean Data**: Existing duplicates automatically cleaned up

## Monitoring

Check logs for successful sync:
```
[CartSync] Syncing 3 items to backend
Local cart synced - userId: xxx, itemsAdded: 3, totalItems: 3
```

No more errors like:
```
Query did not return a unique result: 2 results were returned ❌
```

## Files Modified

- ✅ `CartService.java` - Atomic bulk sync with new helper method
- ✅ `cart-provider.tsx` - Concurrent sync protection
- ✅ `V7__cleanup_duplicate_active_carts.sql` - Data cleanup
- ✅ `V8__add_unique_active_cart_constraint.sql` - Constraint

## Related Issues

This fix also prevents the race condition documented in:
- `docs/CART_RACE_CONDITION_FIX.md`

The synchronized block ensures thread-safety during sync operations.
