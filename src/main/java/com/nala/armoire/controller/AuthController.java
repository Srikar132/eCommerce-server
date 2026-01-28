package com.nala.armoire.controller;

import com.nala.armoire.model.dto.request.RefreshTokenRequest;
import com.nala.armoire.model.dto.request.SendOtpRequest;
import com.nala.armoire.model.dto.request.VerifyOtpRequest;
import com.nala.armoire.model.dto.response.AuthResponse;
import com.nala.armoire.model.dto.response.MessageResponse;
import com.nala.armoire.model.dto.response.SendOtpResponse;
import com.nala.armoire.model.dto.response.UserResponse;
import com.nala.armoire.security.UserPrincipal;
import com.nala.armoire.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication Controller - Phone OTP with Bearer Token + Refresh Token Rotation
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

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
     * Returns Bearer tokens (access + refresh) in response body
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request) {
        
        log.info("OTP verification for phone: {}", maskPhone(request.getPhone()));
        
        AuthService.AuthToken authToken = authService.verifyOtpAndLogin(request);

        AuthResponse authResponse = AuthResponse.builder()
            .user(authToken.getUser())
            .accessToken(authToken.getAccessToken())
            .refreshToken(authToken.getRefreshToken())
            .tokenType(authToken.getTokenType())
            .expiresIn(authToken.getExpiresIn())
            .message("Login successful")
            .build();

        log.info("User authenticated successfully via OTP");
        return ResponseEntity.ok(authResponse);
    }

    /**
     * Step 3: Refresh Access Token using Refresh Token
     * POST /api/v1/auth/refresh
     * Implements token rotation for security
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {
        
        log.debug("Token refresh attempt");
        
        AuthService.AuthToken authToken = authService.refreshAccessToken(request.getRefreshToken());

        AuthResponse authResponse = AuthResponse.builder()
            .user(authToken.getUser())
            .accessToken(authToken.getAccessToken())
            .refreshToken(authToken.getRefreshToken())
            .tokenType(authToken.getTokenType())
            .expiresIn(authToken.getExpiresIn())
            .message("Token refreshed successfully")
            .build();

        log.debug("Token refreshed successfully");
        return ResponseEntity.ok(authResponse);
    }

    /**
     * Get current authenticated user
     * GET /api/v1/auth/me
     * Requires: Authorization: Bearer <token>
     */
    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        log.debug("Fetching current user: {}", userPrincipal.getId());
        
        UserResponse user = authService.getUserById(userPrincipal.getId());

        AuthResponse authResponse = AuthResponse.builder()
            .user(user)
            .message("User fetched successfully")
            .build();

        return ResponseEntity.ok(authResponse);
    }

    /**
     * Logout user
     * POST /api/v1/auth/logout
     * Note: Client must discard the Bearer token
     */
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        log.info("Logout request for user: {}", userPrincipal.getId());

        MessageResponse messageResponse = authService.logout(userPrincipal.getId());

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