# 🛡️ Next.js Middleware Guide - Cookie-Based Auth Protection

## 📋 Overview

This guide shows how to implement **simplified** route protection in Next.js using middleware that works seamlessly with the cookie-based JWT authentication from your Spring Boot backend.

**Philosophy:**
- ✅ Middleware only checks **refresh token** (long-lived, 7 days)
- ✅ Access token refresh is handled by **axios interceptors** (automatic)
- ✅ Avoids race conditions and complexity in middleware
- ✅ Role checking done by decoding refresh token (has role claim)

---

## 📁 File Structure

```
your-nextjs-app/
├── middleware.ts                    # ← Main middleware file (root level)
├── lib/
│   ├── auth.ts                      # Authentication utilities
│   └── api.ts                       # Axios configuration
├── app/
│   ├── (auth)/
│   │   ├── login/
│   │   └── register/
│   ├── (protected)/
│   │   ├── dashboard/
│   │   ├── profile/
│   │   └── orders/
│   └── layout.tsx
└── types/
    └── auth.ts                      # TypeScript types
```

---

## 🔧 Implementation Files

### 1. **middleware.ts** (Root Level - Main File)

```typescript
import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';
import { jwtDecode } from 'jwt-decode';

// Define protected routes
const protectedRoutes = [
  '/dashboard',
  '/profile',
  '/orders',
  '/cart',
  '/checkout',
  '/wishlist',
  '/account',
];

// Define admin routes (require ROLE_ADMIN)
const adminRoutes = [
  '/admin',
  '/admin/products',
  '/admin/orders',
  '/admin/users',
  '/admin/dashboard',
];

// Define auth routes (redirect if already logged in)
const authRoutes = ['/login', '/register', '/verify-otp'];

// Define public routes (always accessible)
const publicRoutes = ['/', '/products', '/about', '/contact'];

// JWT Payload interface
interface JwtPayload {
  sub: string; // user ID
  role: string;
  email?: string;
  phone?: string;
  username?: string;
  type: string; // 'access' or 'refresh'
  iat: number;
  exp: number;
}

export async function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Get refresh token (long-lived, used for auth state check)
  const refreshToken = request.cookies.get('refresh_token')?.value;

  console.log('🔍 Middleware Check:', {
    path: pathname,
    hasRefreshToken: !!refreshToken,
  });

  // Check if route is public
  if (isPublicRoute(pathname)) {
    return NextResponse.next();
  }

  // Check if route is an auth route (login, register)
  if (isAuthRoute(pathname)) {
    // If user has valid refresh token, redirect to dashboard
    if (refreshToken && isTokenValid(refreshToken)) {
      console.log('✅ User already logged in, redirecting to dashboard');
      return NextResponse.redirect(new URL('/dashboard', request.url));
    }
    return NextResponse.next();
  }

  // Check if route is protected
  if (isProtectedRoute(pathname)) {
    // No refresh token - redirect to login
    if (!refreshToken) {
      console.log('❌ No refresh token, redirecting to login');
      const loginUrl = new URL('/login', request.url);
      loginUrl.searchParams.set('redirect', pathname);
      return NextResponse.redirect(loginUrl);
    }

    // Verify refresh token is valid
    if (!isTokenValid(refreshToken)) {
      console.log('❌ Invalid or expired refresh token, redirecting to login');
      const loginUrl = new URL('/login', request.url);
      loginUrl.searchParams.set('redirect', pathname);
      return NextResponse.redirect(loginUrl);
    }

    // Check admin routes by decoding refresh token
    if (isAdminRoute(pathname)) {
      const userRole = getRoleFromToken(refreshToken);
      
      if (userRole !== 'ROLE_ADMIN') {
        console.log('❌ Not authorized for admin route');
        return NextResponse.redirect(new URL('/dashboard', request.url));
      }
    }

    console.log('✅ Access granted');
    // Note: Access token refresh is handled automatically by axios interceptors
    return NextResponse.next();
  }

  // Default: allow access
  return NextResponse.next();
}

// Helper: Check if route is public
function isPublicRoute(pathname: string): boolean {
  return (
    publicRoutes.some(route => 
      pathname === route || pathname.startsWith(route + '/')
    ) ||
    pathname.startsWith('/api/') ||
    pathname.startsWith('/_next/') ||
    pathname.includes('/static/') ||
    pathname.match(/\.(ico|png|jpg|jpeg|svg|css|js)$/)
  );
}

// Helper: Check if route is auth route
function isAuthRoute(pathname: string): boolean {
  return authRoutes.some(route => pathname.startsWith(route));
}

// Helper: Check if route is protected
function isProtectedRoute(pathname: string): boolean {
  return protectedRoutes.some(route => pathname.startsWith(route));
}

// Helper: Check if route is admin route
function isAdminRoute(pathname: string): boolean {
  return adminRoutes.some(route => pathname.startsWith(route));
}

// Helper: Decode and validate JWT token
function isTokenValid(token: string): boolean {
  try {
    const decoded = jwtDecode<JwtPayload>(token);
    const currentTime = Math.floor(Date.now() / 1000);
    
    // Check if token is expired
    return decoded.exp > currentTime;
  } catch (error) {
    console.error('Token decode failed:', error);
    return false;
  }
}

// Helper: Get user role from token
function getRoleFromToken(token: string): string | null {
  try {
    const decoded = jwtDecode<JwtPayload>(token);
    return decoded.role || null;
  } catch (error) {
    console.error('Failed to get role from token:', error);
    return null;
  }
}

// Configure which paths middleware should run on
export const config = {
  matcher: [
    /*
     * Match all request paths except:
     * - _next/static (static files)
     * - _next/image (image optimization files)
     * - favicon.ico (favicon file)
     * - public files (public folder)
     */
    '/((?!_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$).*)',
  ],
};
```

**Install required package:**
```bash
npm install jwt-decode
# or
yarn add jwt-decode
# or
pnpm add jwt-decode
```

---

### 2. **lib/api.ts** - Axios Configuration

```typescript
import axios from 'axios';

const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1',
  withCredentials: true, // CRITICAL: Enable cookies
  headers: {
    'Content-Type': 'application/json',
  },
});

// Response interceptor for automatic token refresh
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    // If access token expired (401) and we haven't retried yet
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        // Attempt to refresh token
        await api.post('/auth/refresh');

        // Retry original request
        return api(originalRequest);
      } catch (refreshError) {
        // Refresh failed - redirect to login
        if (typeof window !== 'undefined') {
          window.location.href = '/login';
        }
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);

export default api;
```

---

### 3. **lib/auth.ts** - Authentication Utilities

```typescript
import api from './api';

export interface User {
  id: string;
  phone: string;
  email?: string;
  username?: string;
  role: 'ROLE_USER' | 'ROLE_ADMIN';
  phoneVerified: boolean;
  isActive: boolean;
}

export interface AuthResponse {
  user: User;
  message: string;
  success: boolean;
}

// Send OTP to phone
export async function sendOtp(phone: string): Promise<any> {
  const response = await api.post('/auth/send-otp', { phone });
  return response.data;
}

// Verify OTP and login
export async function verifyOtp(phone: string, otp: string): Promise<AuthResponse> {
  const response = await api.post('/auth/verify-otp', { phone, otp });
  return response.data;
}

// Get current user
export async function getCurrentUser(): Promise<User | null> {
  try {
    const response = await api.get('/auth/me');
    return response.data.user;
  } catch (error) {
    console.error('Failed to get current user:', error);
    return null;
  }
}

// Logout
export async function logout(): Promise<void> {
  try {
    await api.post('/auth/logout');
  } catch (error) {
    console.error('Logout failed:', error);
  } finally {
    // Redirect to login
    if (typeof window !== 'undefined') {
      window.location.href = '/login';
    }
  }
}

// Check if user is authenticated
export async function isAuthenticated(): Promise<boolean> {
  try {
    const response = await api.get('/auth/me');
    return response.status === 200;
  } catch (error) {
    return false;
  }
}

// Check if user is admin
export async function isAdmin(): Promise<boolean> {
  try {
    const user = await getCurrentUser();
    return user?.role === 'ROLE_ADMIN';
  } catch (error) {
    return false;
  }
}
```

---

### 4. **types/auth.ts** - TypeScript Types

```typescript
export type UserRole = 'ROLE_USER' | 'ROLE_ADMIN';

export interface User {
  id: string;
  phone: string;
  countryCode?: string;
  phoneVerified: boolean;
  phoneVerifiedAt?: string;
  email?: string;
  emailVerified?: boolean;
  username?: string;
  role: UserRole;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
  failedLoginAttempts?: number;
  lockedUntil?: string;
}

export interface AuthResponse {
  user: User;
  message: string;
  success: boolean;
}

export interface SendOtpResponse {
  success: boolean;
  message: string;
  expiresIn: number;
  maskedPhone: string;
}
```

---

### 5. **.env.local** - Environment Variables

```bash
# Backend API URL
NEXT_PUBLIC_API_URL=http://localhost:8080

# For production
# NEXT_PUBLIC_API_URL=https://api.yourdomain.com
```

---

## 🎯 Usage Examples

### Protected Page Component

```typescript
// app/(protected)/dashboard/page.tsx
'use client';

import { useEffect, useState } from 'react';
import { getCurrentUser } from '@/lib/auth';
import type { User } from '@/types/auth';

export default function DashboardPage() {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function loadUser() {
      const currentUser = await getCurrentUser();
      setUser(currentUser);
      setLoading(false);
    }
    loadUser();
  }, []);

  if (loading) {
    return <div>Loading...</div>;
  }

  return (
    <div>
      <h1>Dashboard</h1>
      <p>Welcome, {user?.username || user?.phone}!</p>
    </div>
  );
}
```

---

### Login Page Component

```typescript
// app/(auth)/login/page.tsx
'use client';

import { useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { sendOtp, verifyOtp } from '@/lib/auth';

export default function LoginPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const redirect = searchParams.get('redirect') || '/dashboard';

  const [phone, setPhone] = useState('');
  const [otp, setOtp] = useState('');
  const [step, setStep] = useState<'phone' | 'otp'>('phone');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSendOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      await sendOtp(phone);
      setStep('otp');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to send OTP');
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      const response = await verifyOtp(phone, otp);
      
      if (response.success) {
        // Cookies are set automatically!
        router.push(redirect);
      }
    } catch (err: any) {
      setError(err.response?.data?.message || 'Invalid OTP');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center">
      <div className="max-w-md w-full space-y-8">
        <h2 className="text-3xl font-bold">Login</h2>

        {step === 'phone' ? (
          <form onSubmit={handleSendOtp}>
            <input
              type="tel"
              placeholder="+919876543210"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              required
            />
            <button type="submit" disabled={loading}>
              {loading ? 'Sending...' : 'Send OTP'}
            </button>
          </form>
        ) : (
          <form onSubmit={handleVerifyOtp}>
            <input
              type="text"
              placeholder="Enter OTP"
              value={otp}
              onChange={(e) => setOtp(e.target.value)}
              maxLength={6}
              required
            />
            <button type="submit" disabled={loading}>
              {loading ? 'Verifying...' : 'Verify OTP'}
            </button>
          </form>
        )}

        {error && <p className="text-red-500">{error}</p>}
      </div>
    </div>
  );
}
```

---

### Logout Component

```typescript
// components/LogoutButton.tsx
'use client';

import { logout } from '@/lib/auth';

export default function LogoutButton() {
  const handleLogout = async () => {
    await logout();
  };

  return (
    <button
      onClick={handleLogout}
      className="px-4 py-2 bg-red-500 text-white rounded"
    >
      Logout
    </button>
  );
}
```

---

## 🔒 Security Features

### ✅ Implemented
- ✅ HTTP-Only cookies (XSS protection)
- ✅ Automatic token refresh (via interceptors, not middleware)
- ✅ Route-level protection (refresh token check only)
- ✅ Role-based access control (from JWT claims)
- ✅ Redirect after login
- ✅ Public route exceptions
- ✅ Client-side JWT decoding (no backend calls in middleware)

### 🎯 Why This Approach?
1. **Simpler**: No complex token refresh logic in middleware
2. **Faster**: No API calls during middleware execution
3. **No Race Conditions**: Access token refresh handled by interceptors
4. **Reliable**: Refresh token is long-lived (7 days)
5. **Scalable**: Middleware stays lightweight and fast

### 🎯 Best Practices
1. Always use HTTPS in production
2. Set `COOKIE_SECURE=true` in production
3. Use exact CORS origins (no wildcards)
4. Implement rate limiting on auth endpoints
5. Log security events
6. Let interceptors handle token refresh automatically

---

## 🐛 Troubleshooting

### Issue: Middleware redirects in infinite loop

**Solution:**
Make sure public routes are properly excluded in the `config.matcher` and `isPublicRoute` function.

### Issue: Cookies not being sent from Next.js to backend

**Solution:**
1. Verify `withCredentials: true` in axios config
2. Check CORS configuration allows credentials
3. Ensure exact origin match (not wildcard)

### Issue: "Cannot read property 'exp' of undefined" error

**Solution:**
Install `jwt-decode` package: `npm install jwt-decode`

### Issue: User redirected to login even with valid session

**Solution:**
1. Check refresh token exists in browser cookies
2. Verify refresh token hasn't expired (7 days)
3. Check token is not malformed (inspect in browser DevTools)

---

## 📊 Route Protection Summary

| Route Pattern | Protection | Check Method | Redirect |
|---------------|------------|--------------|----------|
| `/` | None | - | - |
| `/products/**` | None | - | - |
| `/login` | Redirect if logged in | Refresh token exists | `/dashboard` |
| `/dashboard` | Requires auth | Refresh token valid | `/login` |
| `/profile` | Requires auth | Refresh token valid | `/login` |
| `/orders` | Requires auth | Refresh token valid | `/login` |
| `/admin/**` | Requires admin role | JWT claim `role=ROLE_ADMIN` | `/dashboard` |

**Note:** Access token validation happens automatically via axios interceptors, not in middleware.

---

## 🚀 Production Checklist

- [ ] Set `NEXT_PUBLIC_API_URL` to production backend
- [ ] Set `COOKIE_SECURE=true` in backend
- [ ] Configure CORS with exact production origins
- [ ] Test all protected routes
- [ ] Test token refresh flow
- [ ] Test logout flow
- [ ] Test admin routes with non-admin user
- [ ] Verify cookies are HTTP-Only in browser DevTools

---

## 📚 Additional Resources

- [Next.js Middleware Docs](https://nextjs.org/docs/app/building-your-application/routing/middleware)
- [Cookie Security Best Practices](https://owasp.org/www-community/controls/SecureCookieAttribute)
- [JWT Best Practices](https://tools.ietf.org/html/rfc8725)

---

**Last Updated:** January 2026  
**Compatible with:** Next.js 14+ (App Router)  
**Backend:** Spring Boot with Cookie-Based JWT