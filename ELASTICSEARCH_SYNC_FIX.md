# Elasticsearch Sync Fix - RESOLVED ✅

## Date: January 7, 2026

## Problem
API call `GET /api/v1/products?category=men-tshirts` was returning empty results even though 3 products existed in the database with that category.

## Root Cause
The Elasticsearch index was **out of sync** with the PostgreSQL database. Some products were indexed with old/incorrect category slugs:
- 5 products had `categorySlug: "t-shirts"` (doesn't exist in current DB schema)
- 3 products had `categorySlug: "men-tshirts"` (correct)

This inconsistency was causing the filter to fail for certain category queries.

## Solution Applied
1. **Deleted the entire Elasticsearch products index:**
   ```powershell
   DELETE http://localhost:9600/products
   ```

2. **Re-synced all products from PostgreSQL to Elasticsearch:**
   ```powershell
   POST http://localhost:8080/api/v1/products/sync-products
   ```

## Verification Results ✅

### 1. Elasticsearch Index Status
After re-sync, the index now contains only valid categories:
- `men-tshirts`: 3 products
- `men-casual-shirts`: 2 products

### 2. Category Hierarchy Verification
Products are properly indexed with the full category hierarchy in `allCategorySlugs`:
```json
{
  "categorySlug": "men-tshirts",
  "categoryName": "T-Shirts",
  "allCategorySlugs": ["men-tshirts", "men-topwear", "men"]
}
```

### 3. API Testing Results

| API Call | Expected | Actual | Status |
|----------|----------|--------|--------|
| `?category=men-tshirts` | 3 products | 3 products | ✅ PASS |
| `?category=men-topwear` | 5 products (tshirts + casual shirts) | 5 products | ✅ PASS |
| `?category=men` | 5 products (all men's products) | 5 products | ✅ PASS |

### 4. Facets Working Correctly
The facets response now shows:
```json
{
  "categories": [
    {
      "value": "men-tshirts",
      "label": "Tshirts",
      "count": 3,
      "selected": true
    }
  ]
}
```

## Products Retrieved
Successfully retrieving all 3 T-shirt products:
1. Classic Cotton Crew Neck T-Shirt (Nike)
2. Performance Dry-Fit Athletic T-Shirt (Adidas)
3. Graphic Print V-Neck T-Shirt (Puma)

## Category Hierarchy Confirmed
```
Men (men)
  └── Topwear (men-topwear)
      ├── T-Shirts (men-tshirts) - 3 products
      └── Casual Shirts (men-casual-shirts) - 2 products
```

## Prevention
To prevent this issue in the future:
1. Always run the sync endpoint after making database changes to categories or products
2. Consider adding automatic sync triggers when products/categories are updated
3. Add monitoring to detect when Elasticsearch is out of sync with PostgreSQL

## Commands for Future Reference

### Clear and Re-sync Elasticsearch
```powershell
# Delete index
Invoke-RestMethod -Uri "http://localhost:9600/products" -Method DELETE

# Re-sync all products
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/products/sync-products" -Method POST
```

### Check Index Status
```powershell
# Get all category slugs
$body = '{"size": 0, "aggs": {"categories": {"terms": {"field": "categorySlug", "size": 50}}}}'
Invoke-RestMethod -Uri "http://localhost:9600/products/_search" -Method POST -ContentType "application/json" -Body $body
```

---

**Issue Status:** RESOLVED ✅
**Time to Resolution:** ~5 minutes
**Impact:** Zero downtime - re-sync completed successfully
