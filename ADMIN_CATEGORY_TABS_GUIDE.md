# Admin Category Tabs Implementation Guide

## Overview
This implementation adds category type filtering to the Admin API, enabling the Admin Dashboard to display categories in separate tabs: **All**, **Men**, **Women**, and **Kids**.

The solution is **100% backward-compatible** with existing user APIs and maintains the hierarchical category structure.

---

## Architecture

### 1. **CategoryType Enum**
**Location:** `src/main/java/com/nala/armoire/model/entity/CategoryType.java`

```java
public enum CategoryType {
    MEN,
    WOMEN,
    KIDS
}
```

---

### 2. **Category Entity Enhancement**
**Location:** `src/main/java/com/nala/armoire/model/entity/Category.java`

Added nullable field:
```java
@Enumerated(EnumType.STRING)
@Column(name = "category_type")
private CategoryType categoryType;
```

**Why nullable?**
- Maintains backward compatibility with existing categories
- Child categories inherit type from parents via `CategoryTypeResolver`
- Only root categories MUST have explicit type

---

### 3. **CategoryTypeResolver Utility**
**Location:** `src/main/java/com/nala/armoire/util/CategoryTypeResolver.java`

Provides fallback logic for category type resolution:

```java
// Resolves type with parent hierarchy fallback
CategoryType resolved = CategoryTypeResolver.resolveCategoryType(category);

// Checks if category belongs to a type (including inheritance)
boolean isMen = CategoryTypeResolver.belongsToType(category, CategoryType.MEN);

// Validates hierarchy consistency
CategoryTypeResolver.validateCategoryTypeHierarchy(child, parent);

// Ensures root categories have types
CategoryTypeResolver.validateRootCategoryHasType(rootCategory);
```

**Fallback Resolution Flow:**
1. If category has explicit `categoryType` → use it
2. Otherwise, walk up parent chain until finding a category with type
3. If no ancestor has type → return null

**Example:**
```
Men (categoryType = MEN)
  └─ Topwear (categoryType = null) → resolves to MEN (inherited)
      └─ T-Shirts (categoryType = null) → resolves to MEN (inherited)
```

---

### 4. **Repository Enhancements**
**Location:** `src/main/java/com/nala/armoire/repository/CategoryRepository.java`

New query methods:
```java
// Filter by category type
List<Category> findByCategoryTypeOrderByDisplayOrderAsc(CategoryType categoryType);

// Root categories by type
List<Category> findByParentIsNullAndCategoryTypeOrderByDisplayOrderAsc(CategoryType categoryType);

// Active categories by type
List<Category> findByCategoryTypeAndIsActiveTrueOrderByDisplayOrderAsc(CategoryType categoryType);
```

---

### 5. **AdminCategoryService Enhancements**
**Location:** `src/main/java/com/nala/armoire/service/AdminCategoryService.java`

#### New Overloaded Methods:
```java
// Get all categories with optional type filter
List<CategoryDTO> getAllCategories(CategoryType categoryType)

// Get root categories with optional type filter
List<CategoryDTO> getRootCategories(CategoryType categoryType)
```

#### Validation Logic:
```java
// When creating category:
- Root categories MUST have explicit categoryType
- Child categories auto-inherit parent's type if not specified
- Child's type MUST match parent's type (prevents cross-domain nesting)

// When updating category:
- Type changes are validated against parent's type
- New parent assignments validate type consistency

// When changing parent:
- Prevents WOMEN category from becoming child of MEN
- Validates entire hierarchy remains consistent
```

#### Example Validation:
```
✅ ALLOWED:
Men
├─ Topwear (inherits MEN)
└─ Bottomwear (inherits MEN)

Women
└─ Dresses (inherits WOMEN)

❌ BLOCKED:
Men
└─ Dresses (type mismatch - WOMEN under MEN)
```

---

### 6. **AdminCategoryController Query Param Support**
**Location:** `src/main/java/com/nala/armoire/controller/AdminCategoryController.java`

#### New Endpoint Behavior:
```
GET /api/v1/admin/categories
GET /api/v1/admin/categories?type=MEN
GET /api/v1/admin/categories?type=WOMEN
GET /api/v1/admin/categories?type=KIDS
```

#### Response Structure:
Same hierarchical response, but filtered by type.

**Example Response for ?type=MEN:**
```json
[
  {
    "id": "men-uuid",
    "name": "Men",
    "slug": "men",
    "categoryType": "MEN",
    "parentId": null,
    "isActive": true,
    "displayOrder": 0,
    "hierarchy": ["Men"],
    "subCategories": [
      {
        "id": "topwear-uuid",
        "name": "Topwear",
        "slug": "topwear",
        "categoryType": "MEN",
        "parentId": "men-uuid",
        "hierarchy": ["Men", "Topwear"]
      }
    ]
  }
]
```

---

### 7. **CategoryDTO Enhancements**
**Location:** `src/main/java/com/nala/armoire/model/dto/response/CategoryDTO.java`

Already includes:
```java
private CategoryType categoryType;  // Resolved type (includes inherited)
private List<String> hierarchy;     // Path from root to current
private ParentCategoryInfo parent;  // Parent details
```

The `mapToDTO()` method now includes resolved category type:
```java
CategoryType resolvedType = CategoryTypeResolver.resolveCategoryType(category);
builder.categoryType(resolvedType);
```

---

## Database Schema

### SQL Migration
**File:** `category_type_migration.sql`

```sql
ALTER TABLE categories
ADD COLUMN IF NOT EXISTS category_type VARCHAR(50);
```

**Column Details:**
- Type: `VARCHAR(50)` (or `ENUM` in PostgreSQL)
- Nullable: YES (for backward compatibility)
- Values: `MEN`, `WOMEN`, `KIDS`

---

## Usage Examples

### Admin Dashboard Tabs

#### 1. **All Categories Tab**
```bash
GET /api/v1/admin/categories
```
Response: All categories regardless of type

#### 2. **Men Categories Tab**
```bash
GET /api/v1/admin/categories?type=MEN
```
Response: Men root category + all descendants (Topwear, Bottomwear, etc.)

#### 3. **Women Categories Tab**
```bash
GET /api/v1/admin/categories?type=WOMEN
```
Response: Women root category + all descendants

#### 4. **Kids Categories Tab**
```bash
GET /api/v1/admin/categories?type=KIDS
```
Response: Kids root category + all descendants

---

### Creating Categories

#### Create Root Category (Men)
```bash
POST /api/v1/admin/categories

{
  "name": "Men",
  "slug": "men",
  "categoryType": "MEN",
  "description": "Men's clothing",
  "imageUrl": "https://...",
  "isActive": true
}
```

#### Create Child Category (auto-inherits type)
```bash
POST /api/v1/admin/categories

{
  "name": "Topwear",
  "slug": "topwear",
  "parent": { "id": "men-uuid" },
  // categoryType NOT required - will inherit MEN from parent
  "displayOrder": 1,
  "isActive": true
}
```

#### Create Child with Explicit Type (must match parent)
```bash
POST /api/v1/admin/categories

{
  "name": "T-Shirts",
  "slug": "t-shirts",
  "categoryType": "MEN",  // Must match parent (Men)
  "parent": { "id": "topwear-uuid" },
  "displayOrder": 0,
  "isActive": true
}
```

---

### Error Scenarios

#### ❌ Missing Type on Root Category
```
Error: "Root categories must have an explicit category type 
        (MEN, WOMEN, or KIDS)"
```

#### ❌ Type Mismatch with Parent
```
Error: "Category type mismatch: child category type 'WOMEN' does not 
        match parent type 'MEN'. Cross-domain nesting is not allowed."
```

#### ❌ Invalid Hierarchy Structure
```
Error: "Category hierarchy cannot exceed 3 levels 
        (e.g., Men > Topwear > T-Shirts)"
```

---

## Backward Compatibility

### Existing Categories Without Type
Categories created before this feature work seamlessly:

1. **Existing Root Categories:**
   - Should be assigned a `categoryType` via admin dashboard
   - Until assigned, filter queries will skip them (type resolution returns null)

2. **Existing Child Categories:**
   - Will automatically resolve parent's type
   - No database update needed

3. **User APIs (CategoryController):**
   - **COMPLETELY UNCHANGED**
   - Continue working exactly as before
   - Can still fetch categories by slug, parent, active status
   - Hierarchy structure remains identical

---

## Database Considerations

### JPA Auto-DDL (Recommended for Development)
If using Spring Boot JPA with auto-ddl enabled:
```properties
spring.jpa.hibernate.ddl-auto=update
```

JPA will automatically create the `category_type` column on startup.

### Manual Migration (Production)
Run the provided SQL script:
```bash
psql -h <host> -U <username> -d <database> -f category_type_migration.sql
```

### Docker Setup
If using Docker:
1. Update the migration script volume in `docker-compose.yml`
2. Run the migration before restarting containers
3. Or rely on JPA auto-ddl (update mode)

---

## Validation Rules Summary

| Scenario | Rule | Enforced By |
|----------|------|-------------|
| Create Root Category | Must have `categoryType` | `createCategory()` |
| Create Child Category | Type must match parent | `validateCategoryTypeHierarchy()` |
| Update Category Type | New type must match parent | `updateCategory()` |
| Change Parent | Child and parent types must match | `validateCategoryTypeHierarchy()` |
| Hierarchy Depth | Max 3 levels (Root > L1 > L2) | `calculateCategoryDepth()` |
| Circular References | Category cannot be own parent | `updateCategory()` |
| Circular Hierarchy | Child cannot be parent's ancestor | `isDescendant()` |

---

## Performance Optimizations

### Query Optimization
- Repository methods use `OrderByDisplayOrderAsc` for consistent ordering
- Lazy loading on parent relationship prevents N+1 queries
- Type filtering in service layer (after retrieval) is efficient for small category counts
- For large datasets, consider custom @Query methods in repository

### Caching Recommendation
If categories are frequently accessed:
```java
@Cacheable("categories")
public List<CategoryDTO> getAllCategories(CategoryType categoryType) { ... }
```

### Type Resolution Caching
Category type resolution walks parent chain. For deeply nested categories, consider caching:
```java
@Cacheable("categoryType")
public static CategoryType resolveCategoryType(Category category) { ... }
```

---

## Testing Checklist

- [ ] Create root category with explicit type
- [ ] Create child category (verify type inheritance)
- [ ] Attempt to create child with mismatched type (verify rejection)
- [ ] Query `?type=MEN` (verify filtering)
- [ ] Query `?type=WOMEN` (verify filtering)
- [ ] Update category type (verify validation)
- [ ] Change parent category (verify type consistency)
- [ ] Verify existing user APIs unchanged
- [ ] Verify backward compatibility with null types
- [ ] Test circular reference prevention
- [ ] Test hierarchy depth validation

---

## File Structure Summary

```
src/main/java/com/nala/armoire/
├── model/
│   ├── entity/
│   │   ├── Category.java (✏️ UPDATED - added categoryType field)
│   │   └── CategoryType.java (✅ EXISTING - enum)
│   └── dto/response/
│       └── CategoryDTO.java (✅ EXISTING - already has categoryType)
├── repository/
│   └── CategoryRepository.java (✏️ UPDATED - added type filter methods)
├── service/
│   └── AdminCategoryService.java (✏️ UPDATED - added filtering & validation)
├── controller/
│   └── AdminCategoryController.java (✏️ UPDATED - added query param support)
└── util/
    └── CategoryTypeResolver.java (✨ NEW - fallback resolution logic)

database_migration/
└── category_type_migration.sql (✨ NEW - SQL migration script)
```

---

## Migration Checklist

1. ✅ Update `Category` entity with `categoryType` field
2. ✅ Create `CategoryTypeResolver` utility
3. ✅ Add repository methods for type filtering
4. ✅ Enhance `AdminCategoryService` with validation & filtering
5. ✅ Update `AdminCategoryController` with query params
6. ✅ Create database migration script
7. 🔄 Run migration or rely on JPA auto-ddl
8. 🔄 Assign types to existing root categories (admin dashboard)
9. 🔄 Test all scenarios
10. 🔄 Deploy to production

---

## Next Steps

1. **Run Database Migration:**
   ```bash
   psql -h <host> -U <user> -d <db> -f category_type_migration.sql
   ```
   Or rely on JPA auto-ddl with `spring.jpa.hibernate.ddl-auto=update`

2. **Restart Application:**
   - JPA will create the column if not present
   - Application will start successfully

3. **Assign Types to Root Categories:**
   - Use Admin Dashboard to set `categoryType` for Men, Women, Kids categories
   - Or run SQL:
     ```sql
     UPDATE categories SET category_type = 'MEN' WHERE slug = 'men' AND parent_id IS NULL;
     UPDATE categories SET category_type = 'WOMEN' WHERE slug = 'women' AND parent_id IS NULL;
     UPDATE categories SET category_type = 'KIDS' WHERE slug = 'kids' AND parent_id IS NULL;
     ```

4. **Test Admin Tabs:**
   - Verify each tab shows correct categories
   - Confirm user APIs still work

5. **Enable Caching (Optional):**
   - Add `@Cacheable` annotations for performance

---

## Support & Troubleshooting

### Column Not Created?
- Check `spring.jpa.hibernate.ddl-auto` setting
- Run migration script manually
- Verify database permissions

### Categories Missing in Filter?
- Ensure root categories have explicit `categoryType`
- Check type values (MEN, WOMEN, KIDS - case sensitive)
- Verify `CategoryTypeResolver.resolveCategoryType()` logic

### Performance Issues?
- Consider caching with `@Cacheable`
- Use repository query methods instead of in-memory filtering
- Monitor database queries for N+1 issues

---

## Architecture Principles Followed

✅ **No Breaking Changes** - User APIs unchanged  
✅ **Backward Compatible** - Existing categories work as-is  
✅ **Clean Separation** - Admin-specific logic in service/controller  
✅ **Scalable** - Easy to add new category types  
✅ **Validated** - Prevents invalid hierarchies  
✅ **Efficient** - Type resolution via parent chain  
✅ **Well-Documented** - Comprehensive inline comments  

