# 📊 Cookie-Based Authentication - Visual Changes

## 🔄 Before & After Comparison

### 1. AuthResponse.java
```java
// ❌ BEFORE
@Data
@Builder
public class AuthResponse {
    private UserResponse user;
    private String accessToken;      // ← Exposed to client
    private String refreshToken;     // ← Security risk
    private String tokenType;
    private Long expiresIn;
    private String message;
}

// ✅ AFTER
@Data
@Builder
public class AuthResponse {
    private UserResponse user;
    private String message;
    private Boolean success;
    // Tokens sent via HTTP-Only cookies, NOT in response body
}
```

---

### 2. AuthController.java

#### Verify OTP Endpoint
```java
// ❌ BEFORE
@PostMapping("/verify-otp")
public ResponseEntity<AuthResponse> verifyOtp(@RequestBody VerifyOtpRequest request) {
    AuthService.AuthToken authToken = authService.verifyOtpAndLogin(request);
    
    return ResponseEntity.ok(AuthResponse.builder()
        .user(authToken.getUser())
        .accessToken(authToken.getAccessToken())      // ← Exposed
        .refreshToken(authToken.getRefreshToken())    // ← Exposed
        .build());
}

// ✅ AFTER
@PostMapping("/verify-otp")
public ResponseEntity<AuthResponse> verifyOtp(
        @RequestBody VerifyOtpRequest request,
        HttpServletResponse response) {                // ← Added
    
    AuthService.AuthToken authToken = authService.verifyOtpAndLogin(request);
    
    // Set tokens in HTTP-Only cookies
    cookieUtil.setAccessTokenCookie(response, authToken.getAccessToken());
    cookieUtil.setRefreshTokenCookie(response, authToken.getRefreshToken());
    
    return ResponseEntity.ok(AuthResponse.builder()
        .user(authToken.getUser())
        .message("Login successful")
        .success(true)
        .build());
}
```

#### Refresh Token Endpoint
```java
// ❌ BEFORE
@PostMapping("/refresh")
public ResponseEntity<AuthResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
    AuthService.AuthToken authToken = authService.refreshAccessToken(request.getRefreshToken());
    
    return ResponseEntity.ok(AuthResponse.builder()
        .accessToken(authToken.getAccessToken())
        .refreshToken(authToken.getRefreshToken())
        .build());
}

// ✅ AFTER
@PostMapping("/refresh")
public ResponseEntity<AuthResponse> refreshToken(
        HttpServletRequest request,          // ← Added
        HttpServletResponse response) {       // ← Added
    
    // Extract from cookie automatically
    String refreshToken = cookieUtil.getRefreshToken(request)
        .orElseThrow(() -> new UnauthorizedException("Refresh token not found"));
    
    AuthService.AuthToken authToken = authService.refreshAccessToken(refreshToken);
    
    // Set new cookies (token rotation)
    cookieUtil.setAccessTokenCookie(response, authToken.getAccessToken());
    cookieUtil.setRefreshTokenCookie(response, authToken.getRefreshToken());
    
    return ResponseEntity.ok(AuthResponse.builder()
        .user(authToken.getUser())
        .message("Token refreshed successfully")
        .success(true)
        .build());
}
```

#### Logout Endpoint
```java
// ❌ BEFORE
@PostMapping("/logout")
public ResponseEntity<MessageResponse> logout(@AuthenticationPrincipal UserPrincipal userPrincipal) {
    MessageResponse messageResponse = authService.logout(userPrincipal.getId());
    return ResponseEntity.ok(messageResponse);
    // Client must manually delete tokens from localStorage
}

// ✅ AFTER
@PostMapping("/logout")
public ResponseEntity<MessageResponse> logout(
        @AuthenticationPrincipal UserPrincipal userPrincipal,
        HttpServletResponse response) {                             // ← Added
    
    MessageResponse messageResponse = authService.logout(userPrincipal.getId());
    
    // Clear all authentication cookies
    cookieUtil.clearAllAuthCookies(response);
    
    return ResponseEntity.ok(messageResponse);
}
```

---

### 3. JwtAuthenticationFilter.java

```java
// ❌ BEFORE
@Override
protected void doFilterInternal(HttpServletRequest request, ...) {
    // Extract JWT from Authorization header
    String jwt = extractJwtFromRequest(request);
    
    if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
        // Authenticate user
    }
}

private String extractJwtFromRequest(HttpServletRequest request) {
    String bearerToken = request.getHeader("Authorization");
    if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
        return bearerToken.substring(7);
    }
    return null;
}

// ✅ AFTER
@Override
protected void doFilterInternal(HttpServletRequest request, ...) {
    // Extract JWT from cookie (primary method)
    String jwt = extractJwtFromCookie(request);
    
    // Fallback: Authorization header (optional)
    if (!StringUtils.hasText(jwt)) {
        jwt = extractJwtFromAuthorizationHeader(request);
    }
    
    if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
        // Authenticate user
    }
}

private String extractJwtFromCookie(HttpServletRequest request) {
    return cookieUtil.getAccessToken(request).orElse(null);
}
```

---

### 4. SecurityConfig.java

```java
// ❌ BEFORE
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOriginPatterns(Arrays.asList("*"));  // ← Wildcard
    configuration.setAllowCredentials(true);
    // ...
}

// ✅ AFTER
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    
    // CRITICAL: Exact origins for cookie-based auth
    configuration.setAllowedOrigins(Arrays.asList(
        "http://localhost:3000",
        "http://localhost:5173",
        "https://yourdomain.com"
    ));
    
    configuration.setAllowCredentials(true);  // ← MUST be true
    configuration.setExposedHeaders(Arrays.asList("Set-Cookie"));
    // ...
}
```

---

### 5. Frontend Changes

#### Axios Configuration
```javascript
// ❌ BEFORE
import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080/api/v1'
});

// Manually add token to headers
api.interceptors.request.use(config => {
  const token = localStorage.getItem('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// ✅ AFTER
import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080/api/v1',
  withCredentials: true  // ← CRITICAL: Enables cookies
});

// No need to manually add headers - cookies sent automatically!
```

#### Login Flow
```javascript
// ❌ BEFORE
const login = async (phone, otp) => {
  const response = await api.post('/auth/verify-otp', { phone, otp });
  
  // Store tokens in localStorage
  localStorage.setItem('accessToken', response.data.accessToken);
  localStorage.setItem('refreshToken', response.data.refreshToken);
  
  return response.data;
};

// ✅ AFTER
const login = async (phone, otp) => {
  const response = await api.post('/auth/verify-otp', { phone, otp });
  
  // Cookies are set automatically by browser!
  // No need to store anything
  
  return response.data;
};
```

#### Authenticated Requests
```javascript
// ❌ BEFORE
const getCurrentUser = async () => {
  const token = localStorage.getItem('accessToken');
  
  const response = await axios.get('http://localhost:8080/api/v1/auth/me', {
    headers: { Authorization: `Bearer ${token}` }
  });
  
  return response.data;
};

// ✅ AFTER
const getCurrentUser = async () => {
  // Cookies sent automatically!
  const response = await api.get('/auth/me');
  return response.data;
};
```

#### Token Refresh
```javascript
// ❌ BEFORE
const refreshToken = async () => {
  const oldRefreshToken = localStorage.getItem('refreshToken');
  
  const response = await api.post('/auth/refresh', {
    refreshToken: oldRefreshToken
  });
  
  // Store new tokens
  localStorage.setItem('accessToken', response.data.accessToken);
  localStorage.setItem('refreshToken', response.data.refreshToken);
};

// ✅ AFTER
const refreshToken = async () => {
  // Refresh token sent from cookie automatically!
  await api.post('/auth/refresh');
  
  // New cookies set automatically by server
  // Nothing to store!
};
```

#### Logout
```javascript
// ❌ BEFORE
const logout = async () => {
  await api.post('/auth/logout');
  
  // Manually clear localStorage
  localStorage.removeItem('accessToken');
  localStorage.removeItem('refreshToken');
  
  window.location.href = '/login';
};

// ✅ AFTER
const logout = async () => {
  await api.post('/auth/logout');
  
  // Cookies cleared automatically by server!
  
  window.location.href = '/login';
};
```

---

## 📦 New File: CookieUtil.java

```java
@Component
public class CookieUtil {
    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    
    // Set access token cookie (15 min, HttpOnly, Secure)
    public void setAccessTokenCookie(HttpServletResponse response, String token) {
        Cookie cookie = createSecureCookie(ACCESS_TOKEN_COOKIE, token, 900);
        cookie.setPath("/");
        response.addCookie(cookie);
    }
    
    // Set refresh token cookie (7 days, HttpOnly, Secure, limited path)
    public void setRefreshTokenCookie(HttpServletResponse response, String token) {
        Cookie cookie = createSecureCookie(REFRESH_TOKEN_COOKIE, token, 604800);
        cookie.setPath("/api/v1/auth/refresh");
        response.addCookie(cookie);
    }
    
    // Clear all auth cookies
    public void clearAllAuthCookies(HttpServletResponse response) {
        clearAccessTokenCookie(response);
        clearRefreshTokenCookie(response);
    }
    
    private Cookie createSecureCookie(String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);   // ← Prevents XSS
        cookie.setSecure(true);      // ← HTTPS only
        cookie.setMaxAge(maxAge);
        return cookie;
    }
}
```

---

## 🔒 Security Comparison

| Feature | Bearer Token | Cookie-Based | Winner |
|---------|-------------|--------------|--------|
| **XSS Protection** | ❌ Vulnerable (localStorage) | ✅ Protected (HttpOnly) | 🏆 Cookie |
| **CSRF Protection** | ✅ Not vulnerable | ✅ Protected (SameSite) | ⚖️ Tie |
| **Token Exposure** | ❌ Visible to JS | ✅ Hidden | 🏆 Cookie |
| **Auto-Send** | ❌ Manual headers | ✅ Automatic | 🏆 Cookie |
| **Mobile App Support** | ✅ Easy | ⚠️ Needs workaround | 🏆 Bearer |
| **API Testing** | ✅ Easy (curl) | ✅ Easy (curl -c/-b) | ⚖️ Tie |
| **Implementation** | ✅ Simple | ⚠️ Moderate | 🏆 Bearer |
| **Industry Standard** | ⚠️ Common | ✅ Best Practice | 🏆 Cookie |

**Overall Winner:** 🏆 **Cookie-Based** (Better Security)

---

## 📡 HTTP Headers Comparison

### Login Response

#### Before (Bearer Token)
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "user": { ... },
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

#### After (Cookie-Based)
```http
HTTP/1.1 200 OK
Content-Type: application/json
Set-Cookie: access_token=eyJhbGciOiJIUzI1NiIs...; HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=900
Set-Cookie: refresh_token=eyJhbGciOiJIUzI1NiIs...; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth/refresh; Max-Age=604800

{
  "user": { ... },
  "message": "Login successful",
  "success": true
}
```

---

### Authenticated Request

#### Before (Bearer Token)
```http
GET /api/v1/auth/me HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

#### After (Cookie-Based)
```http
GET /api/v1/auth/me HTTP/1.1
Cookie: access_token=eyJhbGciOiJIUzI1NiIs...
```

---

## 🎯 Key Takeaways

### ✅ What Improved
1. **Security**: HTTP-Only cookies prevent XSS attacks
2. **Simplicity**: No manual token management in frontend
3. **Automation**: Browser handles cookies automatically
4. **Standards**: Follows industry best practices

### ⚠️ What to Watch
1. **CORS**: Must set exact origins, not wildcard
2. **Credentials**: Must enable `withCredentials: true`
3. **HTTPS**: Required in production (`cookie.secure=true`)
4. **Testing**: Need to handle cookies in API tests

### 🚀 Next Steps
1. ✅ Update frontend code
2. ✅ Test locally with cookies
3. ✅ Update production CORS configuration
4. ✅ Enable `cookie.secure=true` in production
5. ✅ Deploy and monitor

---

**Migration Complete!** 🎉

See `COOKIE_AUTH_GUIDE.md` for comprehensive documentation.
