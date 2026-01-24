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
 * Authentication Controller - Phone OTP Based
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
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request,
            HttpServletResponse response) {
        
        log.info("OTP verification for phone: {}", maskPhone(request.getPhone()));
        
        AuthService.TokenPair tokenPair = authService.verifyOtpAndLogin(request);

        // Set tokens in HTTP-only cookies
        cookieUtil.addAccessTokenCookie(response, tokenPair.getAccessToken());
        cookieUtil.addRefreshTokenCookie(response, tokenPair.getRefreshToken());

        AuthResponse authResponse = AuthResponse.builder()
            .user(tokenPair.getUser())
            .message("Login successful")
            .build();

        log.info("User authenticated successfully via OTP");
        return ResponseEntity.ok(authResponse);
    }

    /**
     * Refresh access token using refresh token
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(
            HttpServletRequest request,
            HttpServletResponse response) {
        
        log.debug("Token refresh attempt");
        
        // Extract refresh token from cookie
        String refreshToken = cookieUtil.extractTokenFromCookie(request, "refreshToken");

        if (refreshToken == null) {
            log.warn("Refresh token missing in cookie");
            throw new UnauthorizedException("Refresh token is required");
        }

        AuthService.TokenPair tokenPair = authService.refreshToken(refreshToken);

        // Set new tokens in cookies
        cookieUtil.addAccessTokenCookie(response, tokenPair.getAccessToken());
        cookieUtil.addRefreshTokenCookie(response, tokenPair.getRefreshToken());

        AuthResponse authResponse = AuthResponse.builder()
            .user(tokenPair.getUser())
            .message("Token refreshed successfully")
            .build();

        log.debug("Token refreshed successfully");
        return ResponseEntity.ok(authResponse);
    }

    /**
     * Get current authenticated user
     * GET /api/v1/auth/me
     */
    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        if (userPrincipal == null) {
            log.warn("Unauthenticated access attempt to /me endpoint");
            throw new UnauthorizedException("Authentication required");
        }

        log.debug("Fetching current user: {}", userPrincipal.getId());
        
        UserResponse user = authService.getUserById(userPrincipal.getId());

        AuthResponse authResponse = AuthResponse.builder()
            .user(user)
            .build();

        return ResponseEntity.ok(authResponse);
    }

    /**
     * Logout user
     * POST /api/v1/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletResponse response) {

        if (userPrincipal == null) {
            log.warn("Unauthenticated logout attempt");
            throw new UnauthorizedException("User not authenticated");
        }

        log.info("Logout request for user: {}", userPrincipal.getId());

        MessageResponse messageResponse = authService.logout(userPrincipal.getId());

        // Clear authentication cookies
        cookieUtil.clearAuthCookies(response);

        log.info("User logged out successfully");
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