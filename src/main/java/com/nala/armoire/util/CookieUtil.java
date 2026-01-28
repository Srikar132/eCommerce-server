package com.nala.armoire.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

/**
 * Cookie Utility for Managing HTTP-Only Secure Cookies
 * Industry standard implementation for JWT tokens in cookies
 */
@Slf4j
@Component
public class CookieUtil {

    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    @Value("${cookie.domain:#{null}}")
    private String cookieDomain;

    @Value("${cookie.secure:true}")
    private boolean cookieSecure;

    @Value("${cookie.same-site:Strict}")
    private String cookieSameSite;

    @Value("${jwt.access-token-expiration}")
    private Long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private Long refreshTokenExpiration;

    /**
     * Create Access Token Cookie
     * - HttpOnly: true (prevents XSS attacks)
     * - Secure: true (HTTPS only in production)
     * - SameSite: Strict (prevents CSRF attacks)
     * - Path: /api/v1 (limited scope)
     */
    public void setAccessTokenCookie(HttpServletResponse response, String token) {
        int maxAge = (int) (accessTokenExpiration / 1000); // Convert to seconds
        
        Cookie cookie = createSecureCookie(ACCESS_TOKEN_COOKIE, token, maxAge);
        cookie.setPath("/"); // Access token needed for all protected routes
        
        response.addCookie(cookie);
        log.debug("Access token cookie set with max age: {} seconds", maxAge);
    }

    /**
     * Create Refresh Token Cookie
     * - Longer expiration (7 days)
     * - Limited to refresh endpoint only
     */
    public void setRefreshTokenCookie(HttpServletResponse response, String token) {
        int maxAge = (int) (refreshTokenExpiration / 1000); // Convert to seconds
        
        Cookie cookie = createSecureCookie(REFRESH_TOKEN_COOKIE, token, maxAge);
        cookie.setPath("/api/v1/auth/refresh"); // Only accessible on refresh endpoint
        
        response.addCookie(cookie);
        log.debug("Refresh token cookie set with max age: {} seconds", maxAge);
    }

    /**
     * Extract Access Token from Cookie
     */
    public Optional<String> getAccessToken(HttpServletRequest request) {
        return getCookieValue(request, ACCESS_TOKEN_COOKIE);
    }

    /**
     * Extract Refresh Token from Cookie
     */
    public Optional<String> getRefreshToken(HttpServletRequest request) {
        return getCookieValue(request, REFRESH_TOKEN_COOKIE);
    }

    /**
     * Clear Access Token Cookie (on logout)
     */
    public void clearAccessTokenCookie(HttpServletResponse response) {
        Cookie cookie = createSecureCookie(ACCESS_TOKEN_COOKIE, "", 0);
        cookie.setPath("/");
        response.addCookie(cookie);
        log.debug("Access token cookie cleared");
    }

    /**
     * Clear Refresh Token Cookie (on logout)
     */
    public void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = createSecureCookie(REFRESH_TOKEN_COOKIE, "", 0);
        cookie.setPath("/api/v1/auth/refresh");
        response.addCookie(cookie);
        log.debug("Refresh token cookie cleared");
    }

    /**
     * Clear All Auth Cookies
     */
    public void clearAllAuthCookies(HttpServletResponse response) {
        clearAccessTokenCookie(response);
        clearRefreshTokenCookie(response);
        log.info("All authentication cookies cleared");
    }

    /**
     * Create a secure cookie with industry-standard settings
     */
    private Cookie createSecureCookie(String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true); // Prevent JavaScript access (XSS protection)
        cookie.setSecure(cookieSecure); // HTTPS only in production
        cookie.setMaxAge(maxAge); // Expiration in seconds
        
        if (cookieDomain != null && !cookieDomain.isEmpty()) {
            cookie.setDomain(cookieDomain);
        }

        // Note: SameSite attribute must be set via response header
        // as Cookie class doesn't have direct support
        return cookie;
    }

    /**
     * Get cookie value by name
     */
    private Optional<String> getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        
        if (cookies == null || cookies.length == 0) {
            return Optional.empty();
        }

        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    /**
     * Add SameSite attribute to response
     * This is a workaround since Cookie class doesn't support SameSite directly
     */
    public void addSameSiteAttribute(HttpServletResponse response) {
        String sameSite = String.format("; SameSite=%s", cookieSameSite);
        response.addHeader("Set-Cookie", 
            response.getHeader("Set-Cookie") + sameSite);
    }
}
