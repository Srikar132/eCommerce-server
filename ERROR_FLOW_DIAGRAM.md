# 🎯 Error Handling Flow - Quick Reference

## 📊 Error Handling Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                          Client Request                              │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     Spring Security Filter                           │
│  • JWT Validation                                                    │
│  • Bearer Token Check                                                │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                   ┌─────────┴──────────┐
                   │                    │
          ❌ Auth Error          ✅ Valid Token
                   │                    │
                   ▼                    ▼
        ┌──────────────────┐  ┌─────────────────┐
        │ 401 UNAUTHORIZED │  │   Controller    │
        └──────────────────┘  └────────┬────────┘
                                       │
                             ┌─────────┴─────────┐
                             │                   │
                    Endpoint Exists?            ❌
                             │                   │
                            ✅                   ▼
                             │        ┌──────────────────────┐
                             │        │ NoHandlerFoundException│
                             │        │  404 ENDPOINT_NOT_FOUND│
                             │        └──────────────────────┘
                             ▼
                   ┌─────────────────┐
                   │ Method Allowed? │
                   └────────┬────────┘
                            │
                   ┌────────┴────────┐
                   │                 │
                  ✅                ❌
                   │                 │
                   │                 ▼
                   │      ┌───────────────────────────┐
                   │      │HttpRequestMethodNotSupported│
                   │      │  405 METHOD_NOT_ALLOWED      │
                   │      └───────────────────────────┘
                   ▼
        ┌──────────────────────┐
        │  Service Layer       │
        └──────────┬───────────┘
                   │
         ┌─────────┴─────────┐
         │                   │
    Exception?              ✅
         │                   │
        ✅                   ▼
         │        ┌──────────────────┐
         │        │  Success Response│
         │        │  200 OK          │
         │        └──────────────────┘
         ▼
┌────────────────────────────────────────┐
│   GlobalExceptionHandler               │
│                                        │
│  • ResourceNotFoundException → 404    │
│  • BadRequestException → 400          │
│  • ValidationException → 400          │
│  • UnauthorizedException → 401        │
│  • AccessDeniedException → 403        │
│  • Exception → 500                    │
└────────────────┬───────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────┐
│      ApiResponse<T> (Consistent)         │
│  {                                       │
│    "success": false,                     │
│    "message": "Error message",           │
│    "errorCode": "ERROR_CODE",            │
│    "timestamp": "..."                    │
│  }                                       │
└──────────────────────────────────────────┘
```

---

## 🎯 Error Code Flow Chart

```
Request → Is endpoint valid?
           │
           ├─ NO → 404 ENDPOINT_NOT_FOUND
           │
           └─ YES → Is method supported?
                     │
                     ├─ NO → 405 METHOD_NOT_ALLOWED
                     │
                     └─ YES → Is authenticated?
                               │
                               ├─ NO → 401 UNAUTHORIZED
                               │
                               └─ YES → Has permission?
                                         │
                                         ├─ NO → 403 FORBIDDEN
                                         │
                                         └─ YES → Is input valid?
                                                   │
                                                   ├─ NO → 400 BAD_REQUEST
                                                   │
                                                   └─ YES → Does resource exist?
                                                             │
                                                             ├─ NO → 404 RESOURCE_NOT_FOUND
                                                             │
                                                             └─ YES → Process Request
                                                                       │
                                                                       ├─ Error → 500 INTERNAL_SERVER_ERROR
                                                                       │
                                                                       └─ Success → 200 OK
```

---

## 📊 HTTP Status Code Decision Tree

```
┌─────────────────────────────────────────────────────────────┐
│                    HTTP STATUS CODES                         │
└─────────────────────────────────────────────────────────────┘

Success (2xx)
├─ 200 OK ────────────── GET, PUT, DELETE successful
└─ 201 Created ──────── POST successful

Client Errors (4xx)
├─ 400 Bad Request ──── Invalid input, validation failed
├─ 401 Unauthorized ─── Authentication required/failed
├─ 403 Forbidden ────── Insufficient permissions
├─ 404 Not Found ────── Resource or endpoint doesn't exist
└─ 405 Method Not Allowed ─ Wrong HTTP method used

Server Errors (5xx)
└─ 500 Internal Server Error ─ Unexpected server error
```

---

## 🔍 Error Code Reference

### Authentication Errors (401)
```
INVALID_CREDENTIALS ──────── Wrong email/password
AUTHENTICATION_FAILED ────── General auth error
UNAUTHORIZED ──────────────── Token missing/invalid
```

### Authorization Errors (403)
```
ACCESS_DENIED ────────────── No permission
ACCOUNT_DISABLED ─────────── Account disabled
ACCOUNT_LOCKED ───────────── Account locked
```

### Not Found Errors (404)
```
ENDPOINT_NOT_FOUND ───────── URL doesn't exist
RESOURCE_NOT_FOUND ───────── Resource not in database
```

### Validation Errors (400)
```
BAD_REQUEST ──────────────── Invalid input
VALIDATION_ERROR ─────────── Field validation failed
```

### Method Errors (405)
```
METHOD_NOT_ALLOWED ───────── Wrong HTTP method
```

### Server Errors (500)
```
INTERNAL_SERVER_ERROR ────── Unexpected error
```

---

## 💡 Quick Examples

### Example 1: Non-Existent Endpoint
```
GET /api/v1/non-existent
↓
404 ENDPOINT_NOT_FOUND
```

### Example 2: Wrong Method
```
DELETE /api/v1/auth/me (only GET allowed)
↓
405 METHOD_NOT_ALLOWED
Supported: GET
```

### Example 3: Missing Resource
```
GET /api/v1/users/invalid-id
↓
404 RESOURCE_NOT_FOUND
```

### Example 4: Invalid Input
```
POST /api/v1/auth/send-otp
Body: { "phone": "invalid" }
↓
400 VALIDATION_ERROR
```

### Example 5: Authentication Failed
```
GET /api/v1/auth/me
Authorization: Bearer expired-token
↓
401 UNAUTHORIZED
```

---

## 🎨 Frontend Error Handling Pattern

```javascript
async function apiCall(endpoint, options) {
  try {
    const response = await fetch(endpoint, options);
    const data = await response.json();
    
    if (!data.success) {
      // Handle error based on code
      switch (data.errorCode) {
        case 'ENDPOINT_NOT_FOUND':
        case 'RESOURCE_NOT_FOUND':
          show404Page();
          break;
          
        case 'UNAUTHORIZED':
        case 'INVALID_CREDENTIALS':
          redirectToLogin();
          break;
          
        case 'ACCESS_DENIED':
        case 'ACCOUNT_DISABLED':
          showAccessDeniedMessage();
          break;
          
        case 'VALIDATION_ERROR':
          showValidationErrors(data.errors);
          break;
          
        case 'METHOD_NOT_ALLOWED':
          console.error('Wrong HTTP method:', data.message);
          break;
          
        default:
          showGenericError(data.message);
      }
    }
    
    return data;
  } catch (error) {
    console.error('Network error:', error);
    showNetworkError();
  }
}
```

---

## 📋 Testing Commands

### Test 404 - Non-Existent Endpoint
```bash
curl http://localhost:8080/api/v1/invalid-endpoint
# Expected: 404 ENDPOINT_NOT_FOUND
```

### Test 405 - Wrong Method
```bash
curl -X DELETE http://localhost:8080/api/v1/auth/me
# Expected: 405 METHOD_NOT_ALLOWED
```

### Test 404 - Missing Resource
```bash
curl -H "Authorization: Bearer <token>" \
  http://localhost:8080/api/v1/users/invalid-id
# Expected: 404 RESOURCE_NOT_FOUND
```

### Test 400 - Validation Error
```bash
curl -X POST http://localhost:8080/api/v1/auth/send-otp \
  -H "Content-Type: application/json" \
  -d '{"phone": ""}'
# Expected: 400 VALIDATION_ERROR
```

---

**Last Updated:** January 28, 2026  
**Quick Reference for:** Error Handling Flow & HTTP Status Codes
