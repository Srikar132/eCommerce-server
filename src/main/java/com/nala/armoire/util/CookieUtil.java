package com.nala.armoire.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CookieUtil {

    @Value("${jwt.access-token-expiration}")
    private Long accessTokenExpiration; // in milliseconds

    @Value("${jwt.refresh-token-expiration}")
    private Long refreshTokenExpiration; // in milliseconds

    @Value("${app.cookie.secure:false}")
    private boolean secure; // true for HTTPS in production

    @Value("${app.cookie.domain:}")
    private String domain; // Optional: set for subdomain sharing

    public void addAccessTokenCookie(HttpServletResponse response, String token) {
        Cookie cookie = createCookie(
                "accessToken",
                token,
                (int) (accessTokenExpiration / 1000) // Convert ms to seconds
        );
        response.addCookie(cookie);
    }

    public void addRefreshTokenCookie(HttpServletResponse response, String token) {
        Cookie cookie = createCookie(
                "refreshToken",
                token,
                (int) (refreshTokenExpiration / 1000) // Convert ms to seconds
        );
        response.addCookie(cookie);
    }

    public void clearAuthCookies(HttpServletResponse response) {
        Cookie accessCookie = createCookie("accessToken", "", 0);  // Empty string instead of null
        Cookie refreshCookie = createCookie("refreshToken", "", 0); // Empty string instead of null

        response.addCookie(accessCookie);
        response.addCookie(refreshCookie);
    }

    public String extractTokenFromCookie(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;

        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private Cookie createCookie(String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);

        // Set domain if specified (useful for subdomain sharing)
        if (domain != null && !domain.isEmpty()) {
            cookie.setDomain(domain);
        }

        // For Spring Boot 3.x+ (Jakarta EE 10+)
        // cookie.setAttribute("SameSite", "Strict");

        return cookie;
    }
}