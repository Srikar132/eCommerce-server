# 🔐 Cookie-Based JWT Authentication Guide

## 📋 Overview

This application has been migrated from **Bearer Token** authentication to **Cookie-Based JWT** authentication following industry best practices for enhanced security.

### Why Cookie-Based Auth?

✅ **More Secure**: HTTP-Only cookies prevent XSS attacks  
✅ **Automatic**: Browser handles sending cookies  
✅ **CSRF Protection**: SameSite attribute prevents CSRF  
✅ **Token Rotation**: Seamless refresh token rotation  
✅ **Clean Architecture**: Tokens never exposed to JavaScript  

---

## 🏗️ Architecture Changes

### Before (Bearer Token)
```
Client → POST /verify-otp → Server
Server → { accessToken: "xxx", refreshToken: "yyy" } → Client
Client stores tokens in localStorage/sessionStorage
Client → GET /api/resource + Header: Authorization: Bearer xxx → Server
```

### After (Cookie-Based)
```
Client → POST /verify-otp → Server
Server → Sets HTTP-Only Secure Cookies (access_token, refresh_token) → Client
Browser automatically sends cookies with each request
Client → GET /api/resource (cookies sent automatically) → Server
```

---

## 📁 File Changes Summary

### 1. **New Files Created**

#### `CookieUtil.java`
- Utility class for managing HTTP-Only secure cookies
- Handles cookie creation, retrieval, and deletion
- Configurable security settings (Secure, SameSite, Domain)

**Key Methods:**
- `setAccessTokenCookie()` - Create access token cookie (15 min)
- `setRefreshTokenCookie()` - Create refresh token cookie (7 days)
- `getAccessToken()` - Extract access token from request
- `getRefreshToken()` - Extract refresh token from request
- `clearAllAuthCookies()` - Delete all auth cookies (logout)

---

### 2. **Modified Files**

#### `AuthResponse.java`
**Changed:**
- ❌ Removed: `accessToken`, `refreshToken`, `tokenType`, `expiresIn`
- ✅ Added: `success` boolean field
- Tokens are NO LONGER sent in response body (security improvement)

#### `JwtAuthenticationFilter.java`
**Changed:**
- Now reads JWT from cookies instead of Authorization header
- Added `CookieUtil` dependency
- Fallback support for Authorization header (optional, can be removed)

**Flow:**
1. Extract token from `access_token` cookie
2. Validate token
3. Set authentication in SecurityContext

#### `AuthController.java`
**Changed All Endpoints:**

**POST `/api/v1/auth/verify-otp`**
- ✅ Sets `access_token` and `refresh_token` cookies
- Returns user info only (no tokens in body)

**POST `/api/v1/auth/refresh`**
- ❌ No longer accepts `RefreshTokenRequest` body
- ✅ Reads refresh token from cookie automatically
- ✅ Issues new cookies (token rotation)

**POST `/api/v1/auth/logout`**
- ✅ Clears all authentication cookies
- Revokes refresh tokens in database

**GET `/api/v1/auth/me`**
- ✅ Reads token from cookie automatically
- No changes to request format

#### `SecurityConfig.java`
**Changed:**
- Updated CORS configuration for cookie-based auth
- **CRITICAL**: `allowCredentials(true)` must be set
- **CRITICAL**: Cannot use wildcard origins with credentials
- Specified exact allowed origins

#### `application.properties`
**Added:**
```properties
# Cookie Configuration
cookie.secure=false          # Set to true in production (HTTPS)
cookie.domain=               # Empty for same-domain
cookie.same-site=Strict      # Strict, Lax, or None
```

---

### 3. **Deleted Files**
❌ `RefreshTokenRequest.java` - No longer needed (token from cookie)

---

## 🔒 Security Features

### Cookie Attributes

| Attribute | Value | Purpose |
|-----------|-------|---------|
| `HttpOnly` | `true` | Prevents JavaScript access (XSS protection) |
| `Secure` | `true` (prod) | HTTPS only |
| `SameSite` | `Strict` | Prevents CSRF attacks |
| `Path` | `/` (access), `/api/v1/auth/refresh` (refresh) | Limits cookie scope |
| `Domain` | Configurable | For subdomain sharing |
| `MaxAge` | 15 min (access), 7 days (refresh) | Auto-expiry |

### Token Rotation
- When refreshing, old refresh token is revoked
- New tokens are issued
- Token reuse is detected and all sessions terminated

---

## 🚀 API Usage

### 1. Login Flow

#### Request:
```http
POST /api/v1/auth/verify-otp
Content-Type: application/json

{
  "phone": "+919876543210",
  "otp": "123456"
}
```

#### Response:
```http
HTTP/1.1 200 OK
Set-Cookie: access_token=eyJhbGc...; HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=900
Set-Cookie: refresh_token=eyJhbGc...; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth/refresh; Max-Age=604800

{
  "user": {
    "id": "uuid",
    "phone": "+919876543210",
    "role": "USER"
  },
  "message": "Login successful",
  "success": true
}
```

---

### 2. Accessing Protected Routes

#### Request:
```http
GET /api/v1/auth/me
Cookie: access_token=eyJhbGc...
```

**Note**: Browser sends cookies automatically!

#### Response:
```json
{
  "user": {
    "id": "uuid",
    "phone": "+919876543210",
    "email": "user@example.com"
  },
  "message": "User fetched successfully",
  "success": true
}
```

---

### 3. Token Refresh

#### Request:
```http
POST /api/v1/auth/refresh
Cookie: refresh_token=eyJhbGc...
```

**Note**: No request body needed!

#### Response:
```http
HTTP/1.1 200 OK
Set-Cookie: access_token=NEW_TOKEN; HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=900
Set-Cookie: refresh_token=NEW_REFRESH; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth/refresh; Max-Age=604800

{
  "user": { ... },
  "message": "Token refreshed successfully",
  "success": true
}
```

---

### 4. Logout

#### Request:
```http
POST /api/v1/auth/logout
Cookie: access_token=eyJhbGc...
```

#### Response:
```http
HTTP/1.1 200 OK
Set-Cookie: access_token=; Max-Age=0; Path=/
Set-Cookie: refresh_token=; Max-Age=0; Path=/api/v1/auth/refresh

{
  "message": "Logged out successfully. All sessions have been terminated.",
  "success": true
}
```

---

## 🌐 Frontend Integration

### React/Next.js Example

#### 1. **Axios Configuration**
```javascript
import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080/api/v1',
  withCredentials: true, // CRITICAL: Send cookies cross-origin
  headers: {
    'Content-Type': 'application/json'
  }
});

export default api;
```

#### 2. **Login**
```javascript
const login = async (phone, otp) => {
  try {
    const response = await api.post('/auth/verify-otp', { phone, otp });
    // Cookies are set automatically by browser
    console.log('Logged in:', response.data.user);
    return response.data;
  } catch (error) {
    console.error('Login failed:', error);
    throw error;
  }
};
```

#### 3. **Make Authenticated Requests**
```javascript
const getCurrentUser = async () => {
  try {
    // Cookies sent automatically!
    const response = await api.get('/auth/me');
    return response.data.user;
  } catch (error) {
    console.error('Failed to get user:', error);
    throw error;
  }
};
```

#### 4. **Token Refresh (Automatic)**
```javascript
// Add response interceptor for automatic token refresh
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    // If access token expired, refresh it
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        // Call refresh endpoint (cookies sent automatically)
        await api.post('/auth/refresh');
        
        // Retry original request
        return api(originalRequest);
      } catch (refreshError) {
        // Refresh failed, redirect to login
        window.location.href = '/login';
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);
```

#### 5. **Logout**
```javascript
const logout = async () => {
  try {
    await api.post('/auth/logout');
    // Cookies cleared automatically by server
    window.location.href = '/login';
  } catch (error) {
    console.error('Logout failed:', error);
  }
};
```

---

### Fetch API Example

```javascript
// Login
const login = async (phone, otp) => {
  const response = await fetch('http://localhost:8080/api/v1/auth/verify-otp', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include', // CRITICAL: Send cookies
    body: JSON.stringify({ phone, otp })
  });
  
  return await response.json();
};

// Authenticated request
const getCurrentUser = async () => {
  const response = await fetch('http://localhost:8080/api/v1/auth/me', {
    credentials: 'include' // CRITICAL: Send cookies
  });
  
  return await response.json();
};
```

---

## ⚙️ Configuration

### Development Environment

**`application.properties`** (Local):
```properties
cookie.secure=false
cookie.domain=
cookie.same-site=Lax
```

**Frontend** (localhost:3000):
```javascript
const api = axios.create({
  baseURL: 'http://localhost:8080/api/v1',
  withCredentials: true
});
```

### Production Environment

**`application.properties`** (Production):
```properties
cookie.secure=true
cookie.domain=.yourdomain.com
cookie.same-site=Strict
```

**CORS Configuration**:
```java
configuration.setAllowedOrigins(Arrays.asList(
    "https://yourdomain.com",
    "https://www.yourdomain.com"
));
```

**Frontend** (yourdomain.com):
```javascript
const api = axios.create({
  baseURL: 'https://api.yourdomain.com/api/v1',
  withCredentials: true
});
```

---

## 🐛 Troubleshooting

### Issue: Cookies Not Being Set

**Symptoms:**
- Login successful but subsequent requests fail
- Browser doesn't show cookies in DevTools

**Solutions:**
1. ✅ Check `withCredentials: true` in frontend
2. ✅ Verify CORS `allowCredentials(true)` in backend
3. ✅ Ensure exact origin match (not wildcard)
4. ✅ For HTTPS sites, set `cookie.secure=true`

---

### Issue: CORS Errors

**Error:**
```
Access to XMLHttpRequest has been blocked by CORS policy: 
The value of the 'Access-Control-Allow-Credentials' header 
in the response is '' which must be 'true'
```

**Solution:**
```java
// SecurityConfig.java
configuration.setAllowCredentials(true);
configuration.setAllowedOrigins(Arrays.asList(
    "http://localhost:3000" // Exact origin, not wildcard
));
```

---

### Issue: Cookies Not Sent with Requests

**Symptoms:**
- Login works
- Subsequent requests show 401 Unauthorized
- Cookies exist in browser but not sent

**Solutions:**
1. ✅ Add `credentials: 'include'` (fetch) or `withCredentials: true` (axios)
2. ✅ Check cookie `Domain` matches request domain
3. ✅ Verify cookie `Path` includes request path
4. ✅ For cross-domain, ensure `SameSite=None` and `Secure=true`

---

### Issue: Token Refresh Not Working

**Error:**
```
Refresh token not found. Please login again.
```

**Solutions:**
1. ✅ Ensure refresh endpoint is `/api/v1/auth/refresh`
2. ✅ Check refresh token cookie path is `/api/v1/auth/refresh`
3. ✅ Verify `withCredentials: true` on refresh request
4. ✅ Check token hasn't expired (7 days)

---

## 🔐 Security Best Practices

### ✅ DO
- Use HTTPS in production (`cookie.secure=true`)
- Set `SameSite=Strict` for maximum protection
- Use specific origins in CORS (not wildcard)
- Implement token rotation on refresh
- Set appropriate cookie paths
- Log security events (token reuse, multiple failures)
- Use HTTP-Only cookies always

### ❌ DON'T
- Don't store tokens in localStorage (XSS vulnerable)
- Don't use `SameSite=None` unless required for cross-site
- Don't use wildcard origins with credentials
- Don't expose tokens in response body
- Don't disable `HttpOnly` flag
- Don't ignore token reuse detection

---

## 📊 Testing with Postman/cURL

### Postman Setup
1. Disable "Automatically follow redirects" if needed
2. Enable "Cookie Jar" in settings
3. Cookies are managed automatically between requests

### cURL Examples

**Login:**
```bash
curl -X POST http://localhost:8080/api/v1/auth/verify-otp \
  -H "Content-Type: application/json" \
  -d '{"phone":"+919876543210","otp":"123456"}' \
  -c cookies.txt
```

**Authenticated Request:**
```bash
curl http://localhost:8080/api/v1/auth/me \
  -b cookies.txt
```

**Refresh Token:**
```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -b cookies.txt \
  -c cookies.txt
```

**Logout:**
```bash
curl -X POST http://localhost:8080/api/v1/auth/logout \
  -b cookies.txt
```

---

## 📈 Migration Checklist

### Backend ✅
- [x] Created `CookieUtil.java`
- [x] Updated `AuthResponse.java` (removed token fields)
- [x] Updated `JwtAuthenticationFilter.java` (read from cookies)
- [x] Updated `AuthController.java` (set/clear cookies)
- [x] Updated `SecurityConfig.java` (CORS for credentials)
- [x] Updated `application.properties` (cookie config)
- [x] Removed `RefreshTokenRequest.java` (no longer needed)

### Frontend 🔄 (To Do)
- [ ] Add `withCredentials: true` to axios config
- [ ] Remove token storage from localStorage/sessionStorage
- [ ] Remove Authorization header manual setting
- [ ] Update login flow (no token handling needed)
- [ ] Update refresh logic (automatic via interceptor)
- [ ] Test all authenticated requests
- [ ] Update logout flow (call logout endpoint)

---

## 🎯 Endpoints Summary

| Endpoint | Method | Cookie Required | Cookie Set | Description |
|----------|--------|-----------------|------------|-------------|
| `/auth/send-otp` | POST | ❌ | ❌ | Send OTP to phone |
| `/auth/verify-otp` | POST | ❌ | ✅ | Login & set cookies |
| `/auth/refresh` | POST | ✅ (refresh) | ✅ | Rotate tokens |
| `/auth/me` | GET | ✅ (access) | ❌ | Get current user |
| `/auth/logout` | POST | ✅ (access) | ❌ (clears) | Logout & clear cookies |

---

## 📚 References

- [OWASP Cookie Security](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)
- [MDN HTTP Cookies](https://developer.mozilla.org/en-US/docs/Web/HTTP/Cookies)
- [SameSite Cookies Explained](https://web.dev/samesite-cookies-explained/)
- [JWT Best Practices](https://tools.ietf.org/html/rfc8725)

---

## 🎉 Benefits Achieved

✅ **Enhanced Security**: XSS and CSRF protection  
✅ **Better UX**: Automatic token management  
✅ **Cleaner Code**: No manual token handling in frontend  
✅ **Stateless Auth**: JWT still stateless, cookies are just transport  
✅ **Industry Standard**: Follows best practices of major platforms  
✅ **Mobile Ready**: Can switch to Authorization header for mobile apps  

---

## 📞 Support

If you encounter issues:
1. Check troubleshooting section above
2. Verify CORS configuration
3. Check browser DevTools → Network → Cookies
4. Review server logs for authentication errors

---

**Last Updated:** January 2026  
**Version:** 2.0 (Cookie-Based Auth)  
**Author:** Armoire eCommerce Team
