# 🚨 Error Handling Guide - Consistent API Responses

## 📋 Overview

All API responses (both success and error) use the **same consistent format** via `ApiResponse<T>` class.

### Response Structure
```json
{
  "success": true/false,
  "message": "Human-readable message",
  "data": { ... },           // Only for success responses
  "errorCode": "CODE",        // Only for error responses
  "errors": { ... },          // Only for validation errors
  "timestamp": "2026-01-28T10:30:00"
}
```

---

## 🎯 HTTP Status Codes

| Status | Code | When Used | Example |
|--------|------|-----------|---------|
| ✅ | 200 OK | Successful operation | Get user, List products |
| ✅ | 201 Created | Resource created | Register user, Create order |
| ❌ | 400 Bad Request | Invalid input/validation | Missing fields, invalid format |
| ❌ | 401 Unauthorized | Authentication failed | Invalid token, expired token |
| ❌ | 403 Forbidden | No permission | Access denied, account disabled |
| ❌ | 404 Not Found | Resource/endpoint not found | User not found, **invalid URL** |
| ❌ | 405 Method Not Allowed | Wrong HTTP method | POST to GET-only endpoint |
| ❌ | 500 Internal Server Error | Unexpected errors | Database errors, exceptions |

---

## ✅ Success Response Format

### Example: Get User
```json
{
  "success": true,
  "message": "User fetched successfully",
  "data": {
    "id": "uuid",
    "phone": "+919876543210",
    "email": "user@example.com",
    "username": "johndoe",
    "role": "CUSTOMER"
  },
  "timestamp": "2026-01-28T10:30:00.000+00:00"
}
```

### Example: Login Success
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "user": {
      "id": "uuid",
      "phone": "+919876543210"
    },
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 900
  },
  "timestamp": "2026-01-28T10:30:00.000+00:00"
}
```

---

## ❌ Error Response Formats

### 1. 404 - Endpoint Not Found 🆕

**Scenario:** User calls non-existent endpoint like `/api/v1/invalid-endpoint`

**Response:**
```json
{
  "success": false,
  "message": "The endpoint you're trying to access does not exist. Please check the URL and try again.",
  "errorCode": "ENDPOINT_NOT_FOUND",
  "timestamp": "2026-01-28T10:30:00.000+00:00"
}
```

**HTTP Status:** `404 NOT FOUND`

**Fix Applied:**
```properties
# application.properties
spring.mvc.throw-exception-if-no-handler-found=true
spring.web.resources.add-mappings=false
```

---

### 2. 405 - Method Not Allowed 🆕

**Scenario:** User sends `DELETE` to endpoint that only supports `GET, POST`

**Response:**
```json
{
  "success": false,
  "message": "Method DELETE is not supported for this endpoint. Supported methods: GET, POST",
  "errorCode": "METHOD_NOT_ALLOWED",
  "timestamp": "2026-01-28T10:30:00.000+00:00"
}
```

**HTTP Status:** `405 METHOD NOT ALLOWED`

---

### 3. 404 - Resource Not Found

**Example: Bad Request**
```json
{
  "success": false,
  "message": "Invalid phone number format",
  "errorCode": "BAD_REQUEST",
  "timestamp": "2026-01-28T10:30:00.000+00:00"
}
```

**Example: Unauthorized**
```json
{
  "success": false,
  "message": "Invalid email or password. Please check your credentials and try again.",
  "errorCode": "INVALID_CREDENTIALS",
  "timestamp": "2026-01-28T10:30:00.000+00:00"
}
```

**Example: Forbidden**
```json
{
  "success": false,
  "message": "Access denied. You don't have permission to perform this action.",
  "errorCode": "ACCESS_DENIED",
  "timestamp": "2026-01-28T10:30:00.000+00:00"
}
```

**Example: Not Found**
```json
{
  "success": false,
  "message": "Product not found with id: 123",
  "errorCode": "RESOURCE_NOT_FOUND",
  "timestamp": "2026-01-28T10:30:00.000+00:00"
}
```

**Example: Internal Server Error**
```json
{
  "success": false,
  "message": "Something went wrong. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR",
  "timestamp": "2026-01-28T10:30:00.000+00:00"
}
```

### 2. Validation Error (with field details)

**Example: Form Validation**
```json
{
  "success": false,
  "message": "Validation failed. Please check your input.",
  "errorCode": "VALIDATION_ERROR",
  "errors": {
    "email": "Email must be valid",
    "phone": "Phone number is required",
    "password": "Password must be at least 8 characters"
  },
  "timestamp": "2026-01-28T10:30:00.000+00:00"
}
```

---

## 🎯 Error Codes Reference

| HTTP Status | Error Code | Description | Example Message |
|------------|------------|-------------|-----------------|
| 400 | BAD_REQUEST | Invalid request parameters | "Invalid phone number format" |
| 400 | VALIDATION_ERROR | Request validation failed | "Validation failed. Please check your input." |
| 401 | INVALID_CREDENTIALS | Wrong email/password | "Invalid email or password..." |
| 401 | AUTHENTICATION_FAILED | Auth token invalid/expired | "Authentication failed. Please try again." |
| 401 | UNAUTHORIZED | Not authenticated | Custom message from exception |
| 403 | ACCOUNT_DISABLED | Account disabled by admin | "Your account has been disabled..." |
| 403 | ACCOUNT_LOCKED | Account locked (too many attempts) | "Your account has been locked..." |
| 403 | ACCESS_DENIED | No permission for resource | "Access denied. You don't have permission..." |
| 404 | RESOURCE_NOT_FOUND | Resource doesn't exist | "Product not found with id: 123" |
| 500 | INTERNAL_SERVER_ERROR | Unexpected server error | "Something went wrong..." |

---

## 💻 Frontend Integration

### Handling Responses

```javascript
import api from './api';

// Success handling
try {
  const response = await api.get('/api/v1/auth/me');
  
  if (response.data.success) {
    const user = response.data.data;
    console.log('User:', user);
  }
} catch (error) {
  // Error handling
  if (error.response) {
    const errorData = error.response.data;
    
    console.log('Error:', errorData.message);
    console.log('Error Code:', errorData.errorCode);
    
    // Handle validation errors
    if (errorData.errorCode === 'VALIDATION_ERROR' && errorData.errors) {
      Object.entries(errorData.errors).forEach(([field, message]) => {
        console.log(`${field}: ${message}`);
      });
    }
  }
}
```

### React Error Display Component

```javascript
const ErrorMessage = ({ error }) => {
  if (!error) return null;

  return (
    <div className="error-container">
      <div className="error-message">{error.message}</div>
      
      {error.errors && (
        <ul className="error-list">
          {Object.entries(error.errors).map(([field, message]) => (
            <li key={field}>
              <strong>{field}:</strong> {message}
            </li>
          ))}
        </ul>
      )}
      
      <small className="error-code">Error Code: {error.errorCode}</small>
    </div>
  );
};

// Usage
const [error, setError] = useState(null);

try {
  await api.post('/api/v1/auth/verify-otp', data);
} catch (err) {
  setError(err.response?.data);
}

return <ErrorMessage error={error} />;
```

---

## 🔧 Backend Implementation

### ApiResponse Class

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private String errorCode;
    private Object errors;
    private LocalDateTime timestamp;

    // Success response
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    // Simple error response
    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .timestamp(LocalDateTime.now())
                .build();
    }

    // Error response with validation details
    public static <T> ApiResponse<T> error(String message, String errorCode, Object errors) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .errors(errors)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
```

### Controller Usage

```java
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Product>> getProduct(@PathVariable String id) {
        Product product = productService.findById(id);
        return ResponseEntity.ok(
            ApiResponse.success(product, "Product fetched successfully")
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Product>> createProduct(@Valid @RequestBody ProductRequest request) {
        Product product = productService.create(request);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(product, "Product created successfully"));
    }
}
```

### Global Exception Handler

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleResourceNotFound(
        ResourceNotFoundException ex
    ) {
        log.warn("Resource not found: {}", ex.getMessage());
        
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage(), "RESOURCE_NOT_FOUND"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationErrors(
            MethodArgumentNotValidException ex
    ) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        "Validation failed. Please check your input.",
                        "VALIDATION_ERROR",
                        errors
                ));
    }
}
```

---

## ✨ Benefits of Consistent Error Handling

### 1. **Predictable Structure**
- Frontend knows exactly what to expect
- No need to handle different response formats
- Easier to parse and display errors

### 2. **Type Safety**
- `success` field clearly indicates result
- `errorCode` for programmatic handling
- `message` for user display

### 3. **Better DX (Developer Experience)**
- Single error handling function
- Reusable error display components
- Clear error categorization

### 4. **Security**
- Controlled error messages
- No sensitive data leakage
- Consistent security error responses

### 5. **Maintainability**
- Single source of truth (`ApiResponse`)
- Easy to update response format
- No duplicate error classes

---

## 🧪 Testing Error Responses

### Postman Tests

```javascript
// Test error response structure
pm.test("Error response has correct structure", function () {
    const response = pm.response.json();
    
    pm.expect(response).to.have.property('success');
    pm.expect(response.success).to.be.false;
    pm.expect(response).to.have.property('message');
    pm.expect(response).to.have.property('errorCode');
    pm.expect(response).to.have.property('timestamp');
});

// Test specific error code
pm.test("Returns correct error code", function () {
    const response = pm.response.json();
    pm.expect(response.errorCode).to.equal('VALIDATION_ERROR');
});

// Test validation errors
pm.test("Returns field validation errors", function () {
    const response = pm.response.json();
    pm.expect(response).to.have.property('errors');
    pm.expect(response.errors).to.be.an('object');
});
```

---

## 📝 Changelog

### v2.1 - Consistent Error Handling
- ✅ Unified all responses to use `ApiResponse<T>`
- ✅ Removed duplicate `ErrorResponse` classes
- ✅ Consistent structure for success and error
- ✅ Added validation error details support
- ✅ Standardized error codes
- ✅ Improved frontend integration

---

**Last Updated:** January 28, 2026  
**Version:** 2.1  
**Status:** ✅ Production Ready with Consistent Responses
