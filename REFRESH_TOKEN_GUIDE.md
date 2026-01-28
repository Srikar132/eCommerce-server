# 🔐 Bearer Token with Refresh Token Rotation - Complete Guide

## 📋 Overview

This application uses **Bearer Token Authentication** with **Refresh Token Rotation** for maximum security.

### Token Strategy
- **Access Token**: 15 minutes (short-lived, for API access)
- **Refresh Token**: 7 days (long-lived, for obtaining new access tokens)
- **Rotation**: Each refresh generates new tokens and revokes old ones
- **Security**: Token reuse detection with automatic session termination

---

## 🎯 Authentication Flow

```
┌─────────────┐         ┌──────────┐         ┌──────────────┐
│   Client    │         │   API    │         │   Database   │
└──────┬──────┘         └────┬─────┘         └──────┬───────┘
       │                     │                       │
       │  1. Send OTP        │                       │
       ├────────────────────>│                       │
       │                     │                       │
       │  2. OTP sent ✓      │                       │
       │<────────────────────┤                       │
       │                     │                       │
       │  3. Verify OTP      │                       │
       ├────────────────────>│                       │
       │                     │  Save refresh token   │
       │                     ├──────────────────────>│
       │  4. Tokens          │                       │
       │  - accessToken      │                       │
       │  - refreshToken     │                       │
       │<────────────────────┤                       │
       │                     │                       │
       │  Store both tokens  │                       │
       │                     │                       │
       │  5. API Request     │                       │
       │  Authorization:     │                       │
       │  Bearer <access>    │                       │
       ├────────────────────>│                       │
       │                     │                       │
       │  6. Response        │                       │
       │<────────────────────┤                       │
       │                     │                       │
       │  (After 15 min)     │                       │
       │  7. Refresh Request │                       │
       │  {refreshToken}     │                       │
       ├────────────────────>│  Validate token      │
       │                     ├──────────────────────>│
       │                     │  Revoke old token    │
       │                     ├──────────────────────>│
       │                     │  Save new token      │
       │                     ├──────────────────────>│
       │  8. New Tokens      │                       │
       │  - accessToken      │                       │
       │  - refreshToken     │                       │
       │<────────────────────┤                       │
       │                     │                       │
```

---

## 🔑 Configuration

### application.properties
```properties
# Access Token: 15 minutes (900000 ms)
jwt.access-token-expiration=900000

# Refresh Token: 7 days (604800000 ms)
jwt.refresh-token-expiration=604800000

# JWT Secret (256-bit minimum)
jwt.secret=THISISMYSECURESECRETKEYFORJWTECOMMERCEBACKEND123456
```

### Environment Variables (.env)
```env
JWT_ACCESS_TOKEN_EXPIRATION=900000
JWT_REFRESH_TOKEN_EXPIRATION=604800000
JWT_SECRET=your-super-secret-key-here
```

---

## 📡 API Endpoints

### 1. Send OTP 📱

**Endpoint:** `POST /api/v1/auth/send-otp`

**Request:**
```json
{
  "phone": "+919876543210"
}
```

**Response:**
```json
{
  "success": true,
  "message": "OTP sent successfully",
  "expiresIn": 300,
  "maskedPhone": "+91987654****"
}
```

---

### 2. Verify OTP & Login ✅

**Endpoint:** `POST /api/v1/auth/verify-otp`

**Request:**
```json
{
  "phone": "+919876543210",
  "otp": "123456"
}
```

**Response:**
```json
{
  "user": {
    "id": "uuid",
    "phone": "+919876543210",
    "email": "user@example.com",
    "username": "johndoe",
    "role": "CUSTOMER"
  },
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "message": "Login successful"
}
```

**Important:**
- Store **both tokens** securely
- Use `accessToken` for API requests
- Use `refreshToken` to get new access tokens

---

### 3. Refresh Access Token 🔄

**Endpoint:** `POST /api/v1/auth/refresh`

**Request:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response:**
```json
{
  "user": {
    "id": "uuid",
    "phone": "+919876543210",
    "email": "user@example.com",
    "username": "johndoe",
    "role": "CUSTOMER"
  },
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9... (NEW)",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9... (NEW)",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "message": "Token refreshed successfully"
}
```

**Token Rotation:**
- Old tokens are **automatically revoked**
- New tokens are generated
- Update stored tokens with new ones
- Old refresh token **cannot be reused**

**Security:**
- If old token is reused → All sessions terminated
- Protects against token theft

---

### 4. Get Current User 👤

**Endpoint:** `GET /api/v1/auth/me`

**Headers:**
```
Authorization: Bearer <accessToken>
```

**Response:**
```json
{
  "user": {
    "id": "uuid",
    "phone": "+919876543210",
    "email": "user@example.com",
    "username": "johndoe",
    "role": "CUSTOMER"
  },
  "message": "User fetched successfully"
}
```

---

### 5. Logout 🚪

**Endpoint:** `POST /api/v1/auth/logout`

**Headers:**
```
Authorization: Bearer <accessToken>
```

**Response:**
```json
{
  "message": "Logged out successfully. All sessions have been terminated.",
  "success": true
}
```

**What happens:**
- All refresh tokens revoked in database
- Client must delete both tokens
- Access token remains valid until expiry (15 min max)

---

## 💻 Frontend Integration

### Token Storage

```javascript
// Store tokens after login
const storeTokens = (accessToken, refreshToken) => {
  localStorage.setItem('accessToken', accessToken);
  localStorage.setItem('refreshToken', refreshToken);
};

// Get tokens
const getAccessToken = () => localStorage.getItem('accessToken');
const getRefreshToken = () => localStorage.getItem('refreshToken');

// Clear tokens
const clearTokens = () => {
  localStorage.removeItem('accessToken');
  localStorage.removeItem('refreshToken');
};
```

### Axios Interceptor with Auto-Refresh

```javascript
import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080/api/v1',
});

// Request interceptor - Add access token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('accessToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor - Auto-refresh on 401
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    // If 401 and not already retried
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        // Try to refresh token
        const refreshToken = localStorage.getItem('refreshToken');
        
        if (!refreshToken) {
          throw new Error('No refresh token');
        }

        const response = await axios.post(
          'http://localhost:8080/api/v1/auth/refresh',
          { refreshToken }
        );

        const { accessToken, refreshToken: newRefreshToken } = response.data;

        // Store new tokens
        localStorage.setItem('accessToken', accessToken);
        localStorage.setItem('refreshToken', newRefreshToken);

        // Retry original request with new token
        originalRequest.headers.Authorization = `Bearer ${accessToken}`;
        return api(originalRequest);

      } catch (refreshError) {
        // Refresh failed - redirect to login
        localStorage.clear();
        window.location.href = '/login';
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);

export default api;
```

### React Hook for Authentication

```javascript
import { useState, useEffect } from 'react';
import api from './api';

export const useAuth = () => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadUser = async () => {
      const token = localStorage.getItem('accessToken');
      if (token) {
        try {
          const response = await api.get('/auth/me');
          setUser(response.data.user);
        } catch (error) {
          console.error('Failed to load user:', error);
        }
      }
      setLoading(false);
    };

    loadUser();
  }, []);

  const login = async (phone, otp) => {
    const response = await api.post('/auth/verify-otp', { phone, otp });
    const { accessToken, refreshToken, user } = response.data;
    
    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('refreshToken', refreshToken);
    setUser(user);
    
    return user;
  };

  const logout = async () => {
    try {
      await api.post('/auth/logout');
    } finally {
      localStorage.clear();
      setUser(null);
    }
  };

  return { user, loading, login, logout };
};
```

---

## 🔒 Security Features

### 1. Token Rotation
- Each refresh generates **new tokens**
- Old tokens are **immediately revoked**
- Prevents token theft attacks

### 2. Reuse Detection
- If revoked token is used → All sessions terminated
- Indicates potential security breach
- Forces re-authentication

### 3. Short-Lived Access Tokens
- 15-minute expiry reduces attack window
- Frequent rotation limits exposure
- Automatic cleanup of expired tokens

### 4. Database Tracking
- All refresh tokens stored and validated
- Audit trail for security monitoring
- Ability to revoke all user sessions

---

## 🧪 Testing with Postman

### 1. Login Flow

**Step 1: Send OTP**
```
POST http://localhost:8080/api/v1/auth/send-otp
Body: {"phone": "+919876543210"}
```

**Step 2: Verify OTP**
```
POST http://localhost:8080/api/v1/auth/verify-otp
Body: {"phone": "+919876543210", "otp": "123456"}

→ Copy accessToken and refreshToken from response
```

**Step 3: Use Access Token**
```
GET http://localhost:8080/api/v1/auth/me
Authorization: Bearer <accessToken>
```

**Step 4: Refresh Tokens**
```
POST http://localhost:8080/api/v1/auth/refresh
Body: {"refreshToken": "<refreshToken>"}

→ Get new accessToken and refreshToken
```

### 2. Test Token Rotation

1. Login and save tokens
2. Refresh token (get new tokens)
3. Try using old refresh token → Should fail
4. Try using old refresh token again → All sessions revoked

---

## ⚠️ Error Handling

### 401 Unauthorized - Token Expired
```json
{
  "timestamp": "2026-01-28T10:30:00.000+00:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Token expired",
  "path": "/api/v1/auth/me"
}
```
**Action:** Use refresh token to get new access token

### 401 Unauthorized - Token Reuse Detected
```json
{
  "timestamp": "2026-01-28T10:30:00.000+00:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Token reuse detected. All sessions have been terminated. Please login again.",
  "path": "/api/v1/auth/refresh"
}
```
**Action:** All tokens revoked. User must login again.

### 401 Unauthorized - Invalid Refresh Token
```json
{
  "timestamp": "2026-01-28T10:30:00.000+00:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Refresh token not found or expired",
  "path": "/api/v1/auth/refresh"
}
```
**Action:** Redirect to login

---

## 📊 Database Schema

### refresh_tokens table
```sql
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    token_id VARCHAR(255) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMP,
    replaced_by_token_id VARCHAR(255),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_token_id ON refresh_tokens(token_id);
CREATE INDEX idx_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_expires_at ON refresh_tokens(expires_at);
```

---

## 🚀 Best Practices

### Token Management
1. ✅ Store tokens in secure storage (not localStorage for production)
2. ✅ Implement auto-refresh logic
3. ✅ Clear tokens on logout
4. ✅ Handle 401 errors gracefully
5. ✅ Never log or expose tokens

### Security
1. ✅ Use HTTPS in production
2. ✅ Rotate tokens regularly
3. ✅ Implement rate limiting
4. ✅ Monitor for suspicious activity
5. ✅ Clean up expired tokens periodically

### Frontend
1. ✅ Intercept API requests
2. ✅ Auto-refresh before expiry
3. ✅ Handle refresh failures
4. ✅ Show loading states
5. ✅ Redirect on auth errors

---

## 📝 Changelog

### v2.0 - Refresh Token Rotation
- ✅ Added refresh token support
- ✅ Implemented token rotation
- ✅ Added reuse detection
- ✅ Database tracking
- ✅ 15-minute access tokens
- ✅ 7-day refresh tokens

---

**Last Updated:** January 28, 2026  
**Version:** 2.0  
**Status:** ✅ Production Ready with Enhanced Security
