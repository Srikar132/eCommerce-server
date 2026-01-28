package com.nala.armoire.controller;

import com.nala.armoire.exception.UnauthorizedException;
import com.nala.armoire.model.dto.request.SendOtpRequest;
import com.nala.armoire.model.dto.request.VerifyOtpRequest;
import com.nala.armoire.model.dto.response.AuthResponse;
import com.nala.armoire.model.dto.response.MessageResponse;
import com.nala.armoire.model.dto.response.SendOtpResponse;
import com.nala.armoire.model.dto.response.UserResponse;
import com.nala.armoire.security.UserPrincipal;
import com.nala.armoire.service.AuthService;
import com.nala.armoire.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication Controller - Cookie-based JWT Authentication
 * Implements Phone OTP with HTTP-Only Secure Cookies
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CookieUtil cookieUtil;

    /**
     * Step 1: Send OTP to phone number
     * POST /api/v1/auth/send-otp
     */
    @PostMapping("/send-otp")
    public ResponseEntity<SendOtpResponse> sendOtp(
            @Valid @RequestBody SendOtpRequest request) {
        
        log.info("OTP request for phone: {}", maskPhone(request.getPhone()));
        
        SendOtpResponse response = authService.sendOtp(request);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Step 2: Verify OTP and login/register user
     * POST /api/v1/auth/verify-otp
     * Sets HTTP-Only secure cookies (access_token + refresh_token)
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request,
            HttpServletResponse response) {
        
        log.info("OTP verification for phone: {}", maskPhone(request.getPhone()));
        
        AuthService.AuthToken authToken = authService.verifyOtpAndLogin(request);

        // Set tokens in HTTP-Only secure cookies
        cookieUtil.setAccessTokenCookie(response, authToken.getAccessToken());
        cookieUtil.setRefreshTokenCookie(response, authToken.getRefreshToken());

        // Response body does NOT contain tokens for security
        AuthResponse authResponse = AuthResponse.builder()
            .user(authToken.getUser())
            .message("Login successful")
            .success(true)
            .build();

        log.info("User authenticated successfully via OTP - cookies set");
        return ResponseEntity.ok(authResponse);
    }

    /**
     * Step 3: Refresh Access Token using Refresh Token from Cookie
     * POST /api/v1/auth/refresh
     * Implements token rotation - reads refresh_token cookie, issues new cookies
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(
            HttpServletRequest request,
            HttpServletResponse response) {
        
        log.debug("Token refresh attempt");
        
        // Extract refresh token from HTTP-Only cookie
        String refreshToken = cookieUtil.getRefreshToken(request)
            .orElseThrow(() -> new UnauthorizedException("Refresh token not found. Please login again."));
        
        AuthService.AuthToken authToken = authService.refreshAccessToken(refreshToken);

        // Set new tokens in HTTP-Only secure cookies (token rotation)
        cookieUtil.setAccessTokenCookie(response, authToken.getAccessToken());
        cookieUtil.setRefreshTokenCookie(response, authToken.getRefreshToken());

        AuthResponse authResponse = AuthResponse.builder()
            .user(authToken.getUser())
            .message("Token refreshed successfully")
            .success(true)
            .build();

        log.debug("Token refreshed successfully - new cookies set");
        return ResponseEntity.ok(authResponse);
    }

    /**
     * Get current authenticated user
     * GET /api/v1/auth/me
     * Requires: access_token cookie
     */
    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        log.debug("Fetching current user: {}", userPrincipal.getId());
        
        UserResponse user = authService.getUserById(userPrincipal.getId());

        AuthResponse authResponse = AuthResponse.builder()
            .user(user)
            .message("User fetched successfully")
            .success(true)
            .build();

        return ResponseEntity.ok(authResponse);
    }

    /**
     * Logout user
     * POST /api/v1/auth/logout
     * Clears all authentication cookies and revokes refresh tokens
     */
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletResponse response) {

        log.info("Logout request for user: {}", userPrincipal.getId());

        MessageResponse messageResponse = authService.logout(userPrincipal.getId());

        // Clear all authentication cookies
        cookieUtil.clearAllAuthCookies(response);

        log.info("User logged out successfully - cookies cleared");
        return ResponseEntity.ok(messageResponse);
    }

    /**
     * Health check endpoint
     * GET /api/v1/auth/health
     */
    @GetMapping("/health")
    public ResponseEntity<MessageResponse> health() {
        return ResponseEntity.ok(
            MessageResponse.builder()
                .message("Auth service is running")
                .success(true)
                .build()
        );
    }

    /**
     * Mask phone for logging
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return phone.substring(0, phone.length() - 4) + "****";
    }
}