# Admin Category Tabs API - Quick Reference

## Endpoints

### 1. Get All Categories (with optional type filter)
```
GET /api/v1/admin/categories
GET /api/v1/admin/categories?type=MEN
GET /api/v1/admin/categories?type=WOMEN
GET /api/v1/admin/categories?type=KIDS
```

**Query Parameters:**
- `type` (optional): Filter by category type - `MEN`, `WOMEN`, or `KIDS`

**Response:**
```json
[
  {
    "id": "uuid",
    "name": "Men",
    "slug": "men",
    "categoryType": "MEN",
    "parentId": null,
    "isActive": true,
    "displayOrder": 0,
    "hierarchy": ["Men"],
    "subCategories": [],
    "createdAt": "2024-01-25T10:00:00"
  }
]
```

---

### 2. Get Category by ID
```
GET /api/v1/admin/categories/{id}
```

**Path Parameters:**
- `id`: Category UUID

**Response:** Single category object (same structure as above)

---

### 3. Get Root Categories
```
GET /api/v1/admin/categories/root
```

**Response:** List of root categories (parent_id = null)

---

### 4. Get Subcategories
```
GET /api/v1/admin/categories/{id}/subcategories
```

**Path Parameters:**
- `id`: Parent category UUID

**Response:** List of child categories

---

### 5. Create Category
```
POST /api/v1/admin/categories

{
  "name": "Men",
  "slug": "men",
  "categoryType": "MEN",
  "description": "Men's clothing",
  "imageUrl": "https://example.com/image.jpg",
  "parent": null,
  "isActive": true
}
```

**Requirements:**
- `name`: Required, max 100 chars
- `slug`: Required, unique, max 100 chars
- `categoryType`: Required for root categories, inherited for child categories
- `parent`: Optional category UUID (for child categories)
- `description`: Optional
- `imageUrl`: Optional
- `isActive`: Optional (default: true)

**Response:** Created category object with status 201

---

### 6. Update Category
```
PUT /api/v1/admin/categories/{id}

{
  "name": "Men's Fashion",
  "slug": "mens-fashion",
  "categoryType": "MEN",
  "description": "Updated description",
  "displayOrder": 0
}
```

**Response:** Updated category object with status 200

---

### 7. Partial Update Category
```
PATCH /api/v1/admin/categories/{id}

{
  "isActive": false
}
```

**Response:** Updated category object with status 200

---

### 8. Delete Category
```
DELETE /api/v1/admin/categories/{id}
```

**Requirements:**
- Category must not have any subcategories

**Response:** 204 No Content on success

---

### 9. Toggle Category Status
```
PUT /api/v1/admin/categories/{id}/status
```

**Response:** Updated category object with toggled `isActive` status

---

### 10. Reorder Categories
```
PUT /api/v1/admin/categories/reorder

[
  "uuid-1",
  "uuid-2",
  "uuid-3"
]
```

**Request Body:** Array of category UUIDs in desired display order

**Response:** 200 OK

---

## Response DTO Structure

```json
{
  "id": "string (UUID)",
  "name": "string",
  "slug": "string",
  "description": "string",
  "imageUrl": "string",
  "categoryType": "enum (MEN|WOMEN|KIDS)",
  "parentId": "string (UUID) or null",
  "parent": {
    "id": "string (UUID)",
    "name": "string",
    "slug": "string",
    "parentId": "string (UUID) or null"
  },
  "displayOrder": "integer",
  "isActive": "boolean",
  "hierarchy": ["array of category names from root to current"],
  "subCategories": [
    { /* CategoryDTO objects */ }
  ],
  "createdAt": "string (ISO 8601 datetime)",
  "productCount": "integer (null if not set)"
}
```

---

## CategoryType Enum Values

| Value | Description |
|-------|-------------|
| MEN | Men's category domain |
| WOMEN | Women's category domain |
| KIDS | Kids' category domain |

---

## Validation Rules

### Creating Categories
✅ **Root categories must have `categoryType`**
- Example: Creating "Men" requires `categoryType: "MEN"`

✅ **Child categories must match parent's type**
- Example: Subcategory of "Men" must have `categoryType: "MEN"` or inherit by omitting field

✅ **Slug must be unique**
- No two categories can have the same slug

✅ **Hierarchy cannot exceed 3 levels**
- Max: Root > L1 > L2
- Example: Men > Topwear > T-Shirts (valid), but can't add another level below T-Shirts

### Error Responses

**400 Bad Request - Root category without type:**
```json
{
  "timestamp": "2024-01-25T10:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Root categories must have an explicit category type (MEN, WOMEN, or KIDS)"
}
```

**400 Bad Request - Type mismatch:**
```json
{
  "timestamp": "2024-01-25T10:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Category type mismatch: child category type 'WOMEN' does not match parent type 'MEN'. Cross-domain nesting is not allowed."
}
```

**404 Not Found:**
```json
{
  "timestamp": "2024-01-25T10:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Category not found"
}
```

**409 Conflict - Circular reference:**
```json
{
  "timestamp": "2024-01-25T10:00:00",
  "status": 409,
  "error": "Conflict",
  "message": "Cannot set a descendant category as parent - this would create a circular reference"
}
```

---

## Example Workflows

### Admin Dashboard - Men Tab
```bash
GET /api/v1/admin/categories?type=MEN
```

Returns:
```json
[
  {
    "id": "men-id",
    "name": "Men",
    "categoryType": "MEN",
    "parentId": null,
    "subCategories": [
      {
        "id": "topwear-id",
        "name": "Topwear",
        "categoryType": "MEN",
        "parentId": "men-id",
        "subCategories": [
          {
            "id": "shirts-id",
            "name": "Shirts",
            "categoryType": "MEN",
            "parentId": "topwear-id"
          }
        ]
      }
    ]
  }
]
```

### Admin Dashboard - Create New Subcategory
```bash
POST /api/v1/admin/categories

{
  "name": "Jackets",
  "slug": "jackets",
  "parent": {
    "id": "topwear-id"
  },
  "displayOrder": 5,
  "isActive": true
}
```

Response: Created category with `categoryType: "MEN"` (inherited from parent)

### Admin Dashboard - Bulk Reorder
```bash
PUT /api/v1/admin/categories/reorder

[
  "topwear-id",
  "bottomwear-id",
  "accessories-id",
  "footwear-id"
]
```

Updates display order sequentially (0, 1, 2, 3)

---

## Authentication & Authorization

- **Required Role:** `ADMIN`
- **Authentication:** JWT Bearer token
- **Header:** `Authorization: Bearer <token>`

All endpoints return **401 Unauthorized** if no valid JWT is provided.
All endpoints return **403 Forbidden** if user doesn't have ADMIN role.

---

## Testing with cURL

### Get All Categories
```bash
curl -X GET "http://localhost:8080/api/v1/admin/categories" \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json"
```

### Get Men Categories
```bash
curl -X GET "http://localhost:8080/api/v1/admin/categories?type=MEN" \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json"
```

### Create Category
```bash
curl -X POST "http://localhost:8080/api/v1/admin/categories" \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Topwear",
    "slug": "topwear",
    "parent": {
      "id": "men-uuid"
    },
    "isActive": true
  }'
```

### Update Category
```bash
curl -X PUT "http://localhost:8080/api/v1/admin/categories/{id}" \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Updated Name",
    "displayOrder": 2
  }'
```

### Delete Category
```bash
curl -X DELETE "http://localhost:8080/api/v1/admin/categories/{id}" \
  -H "Authorization: Bearer <token>"
```

---

## HTTP Status Codes

| Code | Meaning |
|------|---------|
| 200 | OK - Request successful |
| 201 | Created - Resource created |
| 204 | No Content - Deletion successful |
| 400 | Bad Request - Invalid input or validation error |
| 401 | Unauthorized - Missing or invalid JWT |
| 403 | Forbidden - User lacks ADMIN role |
| 404 | Not Found - Resource doesn't exist |
| 409 | Conflict - Circular reference or business logic violation |
| 500 | Internal Server Error - Unexpected error |

---

## Notes

- All timestamps are in ISO 8601 format (UTC)
- All UUIDs are 36-character strings (UUID v4)
- JSON responses exclude null fields (via `@JsonInclude(JsonInclude.Include.NON_NULL)`)
- Category type filtering respects hierarchy inheritance
- Display order is zero-indexed (0, 1, 2, ...)
- Slug is URL-safe and unique across all categories

