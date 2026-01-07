# Category Slug Inconsistency Issue

## Problem Summary

When calling the API with different category parameters, you're getting inconsistent results:

- `?category=tshirts` → Returns 3 products (but shouldn't match anything)
- `?category=men-tshirts` → Returns empty array (but should match 3 products)

## Root Cause

Your database has **inconsistent category slugs**. Elasticsearch currently has:

1. **5 products** with `categorySlug: "t-shirts"`
2. **3 products** with `categorySlug: "men-tshirts"`

### Example Product Data

For the 3 products you're seeing, their Elasticsearch document has:
```json
{
  "categorySlug": "men-tshirts",
  "categoryName": "T-Shirts",
  "allCategorySlugs": ["men-tshirts", "men-topwear", "men"]
}
```

### How the Filter Works

The API filters products using the `allCategorySlugs` field, which contains:
- The leaf category (e.g., "men-tshirts")
- All parent categories (e.g., "men-topwear", "men")

So these API calls would work:
- ✅ `?category=men-tshirts` - Direct category match
- ✅ `?category=men-topwear` - Parent category match
- ✅ `?category=men` - Grandparent category match
- ❌ `?category=tshirts` - No match (not in the hierarchy)
- ❌ `?category=t-shirts` - No match for these 3 products

## Solutions

### Option 1: Standardize to "men-tshirts" format
Update all T-shirt products to use `men-tshirts` as the category slug:

```sql
-- Update the 5 products using "t-shirts" to "men-tshirts"
UPDATE products 
SET category_id = (SELECT id FROM categories WHERE slug = 'men-tshirts')
WHERE category_id = (SELECT id FROM categories WHERE slug = 't-shirts');

-- Delete the old "t-shirts" category if no longer needed
DELETE FROM categories WHERE slug = 't-shirts';
```

### Option 2: Standardize to "t-shirts" format
Update all T-shirt products to use `t-shirts` as the category slug:

```sql
-- Update the 3 products using "men-tshirts" to "t-shirts"
UPDATE products 
SET category_id = (SELECT id FROM categories WHERE slug = 't-shirts')
WHERE category_id = (SELECT id FROM categories WHERE slug = 'men-tshirts');

-- Delete the old "men-tshirts" category if no longer needed
DELETE FROM categories WHERE slug = 'men-tshirts';
```

### After Database Update

**IMPORTANT:** After updating the database, you MUST re-sync Elasticsearch:

```bash
# Call your sync endpoint
curl -X POST http://localhost:8080/api/v1/products/sync-products
```

Or restart your application if it auto-syncs on startup.

## Recommended Approach

I recommend **Option 1** (using "men-tshirts") because:
1. It follows a clearer hierarchy: `men` → `men-topwear` → `men-tshirts`
2. It's more specific and avoids ambiguity
3. Your current facets show "Tshirts" as the label, which matches this structure

## Verification Steps

After fixing:

1. Check database consistency:
```sql
SELECT slug, COUNT(*) as product_count 
FROM products p
JOIN categories c ON p.category_id = c.id
GROUP BY slug
ORDER BY slug;
```

2. Re-sync Elasticsearch

3. Test API calls:
```
GET /api/v1/products?category=men-tshirts
GET /api/v1/products?category=men-topwear
GET /api/v1/products?category=men
```

All should return the T-shirt products appropriately based on the hierarchy.
