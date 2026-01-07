# eCommerce API - Postman Collection

Complete Postman collection for testing the eCommerce API with comprehensive test scripts and environment management.

## 📦 Files Included

1. **eCommerce-API.postman_collection.json** - Main API collection with all endpoints
2. **eCommerce-API.postman_environment.json** - Local development environment
3. **eCommerce-API-Production.postman_environment.json** - Production environment

## 🚀 Quick Start

### 1. Import into Postman

1. Open Postman
2. Click **Import** button (top left)
3. Drag and drop all JSON files or browse to select them
4. Click **Import**

### 2. Select Environment

1. Click the environment dropdown (top right)
2. Select **eCommerce API - Local Environment** for local testing
3. Or select **eCommerce API - Production Environment** for production

### 3. Configure Environment Variables

Before testing, update these variables in your environment:

- `baseUrl` - Your API base URL (default: http://localhost:8080)
- `userEmail` - Test user email for login

## 📚 API Endpoints Overview

### 🔐 Authentication (9 endpoints)
- **POST** `/api/v1/auth/register` - Register new user
- **POST** `/api/v1/auth/login` - Login user
- **GET** `/api/v1/auth/me` - Get current user
- **POST** `/api/v1/auth/refresh` - Refresh access token
- **POST** `/api/v1/auth/logout` - Logout user
- **GET** `/api/v1/auth/verify-email` - Verify email
- **POST** `/api/v1/auth/forgot-password` - Request password reset
- **POST** `/api/v1/auth/reset-password` - Reset password
- **POST** `/api/v1/auth/resend-verification` - Resend verification email

### 👤 User Profile (6 endpoints)
- **GET** `/api/v1/users/profile` - Get user profile
- **PUT** `/api/v1/users/profile` - Update profile
- **GET** `/api/v1/users/addresses` - Get all addresses
- **POST** `/api/v1/users/addresses` - Add new address
- **PUT** `/api/v1/users/addresses/{id}` - Update address
- **DELETE** `/api/v1/users/addresses/{id}` - Delete address

### 🛍️ Products (8 endpoints)
- **POST** `/api/v1/products/sync-products` - Sync to Elasticsearch
- **GET** `/api/v1/products` - Search & filter products
- **GET** `/api/v1/products/autocomplete` - Search suggestions
- **GET** `/api/v1/products/{slug}` - Get product by slug
- **GET** `/api/v1/products/{id}/variants` - Get product variants
- **GET** `/api/v1/products/{id}/reviews` - Get product reviews
- **POST** `/api/v1/products/{id}/review` - Add product review
- **GET** `/api/v1/products/{id}/compatible-designs` - Get compatible designs

### 📂 Categories (4 endpoints)
- **GET** `/api/v1/categories` - Get categories (various options)
- **GET** `/api/v1/categories?minimal=true` - Root categories minimal
- **GET** `/api/v1/categories?slug={slug}&includeChildren=true` - Category with children
- **GET** `/api/v1/categories?recursive=true` - Full hierarchy

### 🛒 Cart (7 endpoints)
- **GET** `/api/v1/cart` - Get cart
- **POST** `/api/v1/cart/items` - Add item to cart
- **PUT** `/api/v1/cart/items/{id}` - Update cart item
- **DELETE** `/api/v1/cart/items/{id}` - Delete cart item
- **DELETE** `/api/v1/cart` - Clear cart
- **GET** `/api/v1/cart/summary` - Get cart summary
- **POST** `/api/v1/cart/merge` - Merge guest cart

### 🎨 Customization (8 endpoints)
- **POST** `/api/v1/customization/validate` - Validate configuration
- **POST** `/api/v1/customization/save` - Save customization
- **GET** `/api/v1/customization/{id}` - Get by ID
- **GET** `/api/v1/customization/product/{productId}` - Get product customizations
- **GET** `/api/v1/customization/product/{productId}/latest` - Get latest
- **GET** `/api/v1/customization/my-designs` - Get all user designs
- **GET** `/api/v1/customization/guest/product/{productId}` - Get guest customizations
- **DELETE** `/api/v1/customization/{id}` - Delete customization

### 🎭 Designs (4 endpoints)
- **GET** `/api/v1/designs` - Get all designs (paginated)
- **GET** `/api/v1/designs/{id}` - Get design by ID
- **GET** `/api/v1/designs/search` - Search designs
- **POST** `/api/v1/designs/filter` - Filter designs

### 🏷️ Design Categories (2 endpoints)
- **GET** `/api/v1/design-categories` - Get all categories
- **GET** `/api/v1/design-categories/{slug}/designs` - Get designs by category

### 🔍 Search (1 endpoint)
- **GET** `/api/v1/search` - Search products

## 🔄 Automated Features

### Token Management
The collection automatically:
- Extracts tokens from HTTP-only cookies after login/register
- Stores tokens in environment variables
- Uses Bearer token authentication for protected endpoints
- Refreshes tokens when needed

### Test Scripts
Each request includes automated tests:
- Response time validation (< 5000ms)
- Status code validation
- Data extraction and storage in environment variables
- Success message validation

### Variable Auto-Population
The collection automatically stores:
- `accessToken` - From login/register responses
- `refreshToken` - From login/register responses  
- `userId` - From user responses
- `productId`, `productSlug` - From product listings
- `cartItemId` - From cart operations
- `customizationId` - From customization saves
- `designId` - From design listings
- And more...

## 📝 Usage Examples

### Basic Workflow

1. **Register/Login**
   ```
   POST /api/v1/auth/register
   POST /api/v1/auth/login
   ```
   → Tokens automatically saved

2. **Browse Products**
   ```
   GET /api/v1/products
   GET /api/v1/categories
   ```
   → Product IDs automatically saved

3. **Add to Cart**
   ```
   POST /api/v1/cart/items
   GET /api/v1/cart
   ```
   → Cart items tracked automatically

4. **Customize Product**
   ```
   POST /api/v1/customization/validate
   POST /api/v1/customization/save
   ```
   → Customization ID saved

### Advanced Filtering

**Product Search with Filters:**
```
GET /api/v1/products?category=men-tshirts&brand=nike&minPrice=500&maxPrice=2000&productSize=M,L&color=black,white&customizable=true
```

**Category Hierarchy:**
```
GET /api/v1/categories?slug=men&includeChildren=true&includeProductCount=true
```

**Design Filtering:**
```
POST /api/v1/designs/filter
Body: {
  "categories": ["animals", "nature"],
  "tags": ["minimalist"],
  "colors": ["#000000"],
  "minPrice": 0,
  "maxPrice": 100
}
```

## 🔧 Environment Variables

### Auto-Managed Variables
These are automatically set by test scripts:
- `accessToken` - JWT access token
- `refreshToken` - JWT refresh token
- `userId` - Current user ID
- `productId` - Last viewed product
- `cartItemId` - Last cart item
- `customizationId` - Last customization

### Manual Configuration
Update these in your environment:
- `baseUrl` - API base URL
- `userEmail` - Default test email
- `productSlug` - Sample product slug
- `categorySlug` - Sample category slug

## 🎯 Testing Tips

### 1. Sequential Testing
Run folders in order:
1. Authentication → Get tokens
2. User Profile → Setup user data
3. Products → Browse catalog
4. Cart → Add items
5. Customization → Create designs

### 2. Monitor Console
- Check Postman console for debug logs
- View extracted variables
- Monitor API responses

### 3. Run Collection
Use Postman Collection Runner:
1. Click on collection name
2. Click **Run** button
3. Select environment
4. Click **Run eCommerce API**

### 4. Automated Tests
All requests include tests:
- ✅ Status codes
- ✅ Response times
- ✅ Data validation
- ✅ Variable extraction

## 🔒 Authentication Flow

### Cookie-Based Authentication
The API uses HTTP-only cookies for security:

1. **Login/Register** → Sets `accessToken` and `refreshToken` cookies
2. **Protected Requests** → Cookies sent automatically
3. **Token Refresh** → `/auth/refresh` updates both cookies
4. **Logout** → Clears all auth cookies

The collection extracts tokens from cookies and stores them in environment variables for monitoring.

## 📊 Response Structure

### Success Response
```json
{
  "data": { ... },
  "message": "Success message",
  "success": true
}
```

### Paginated Response
```json
{
  "data": {
    "content": [...],
    "page": 0,
    "size": 20,
    "totalElements": 100,
    "totalPages": 5,
    "first": true,
    "last": false,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

### Error Response
```json
{
  "message": "Error message",
  "error": "ERROR_CODE",
  "status": 400
}
```

## 🐛 Troubleshooting

### No Token After Login
- Check Postman console for cookie extraction
- Verify cookies are enabled in Postman settings
- Ensure server is sending cookies correctly

### 401 Unauthorized
- Run login request first
- Check if `accessToken` is set in environment
- Try refresh token endpoint

### Environment Variables Not Set
- Check test scripts are enabled
- View Postman console for errors
- Manually set required variables

### CORS Errors
- Ensure backend CORS is configured
- Use Postman desktop app (not web)
- Check allowed origins in backend

## 🔗 API Documentation

Base URL (Local): `http://localhost:8080`

### Key Features:
- ✅ JWT Authentication with HTTP-only cookies
- ✅ Comprehensive product search with Elasticsearch
- ✅ Category hierarchy management
- ✅ Shopping cart (guest & authenticated)
- ✅ Product customization with layer system
- ✅ Design library with filtering
- ✅ User profile & address management
- ✅ Product reviews & ratings

## 📞 Support

For issues or questions:
1. Check server logs
2. Review Postman console
3. Verify environment configuration
4. Check API documentation

## 📜 License

This collection is part of the eCommerce project.

---

**Last Updated:** January 8, 2026
**API Version:** v1
**Postman Version:** 10.x+
