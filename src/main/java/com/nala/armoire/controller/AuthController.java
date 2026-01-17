package com.nala.armoire.controller;

import com.nala.armoire.exception.UnauthorizedException;
import com.nala.armoire.model.dto.request.*;
import com.nala.armoire.model.dto.response.AuthResponse;
import com.nala.armoire.model.dto.response.MessageResponse;
import com.nala.armoire.model.dto.response.UserResponse;
import com.nala.armoire.security.UserPrincipal;
import com.nala.armoire.service.AuthService;
import com.nala.armoire.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CookieUtil cookieUtil;

    /**
     * Register a new user
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        
        log.info("Registration attempt for email: {}", request.getEmail());
        
        AuthService.TokenPair tokenPair = authService.register(request);

        // Set tokens in HTTP-only cookies
        cookieUtil.addAccessTokenCookie(response, tokenPair.accessToken);
        cookieUtil.addRefreshTokenCookie(response, tokenPair.refreshToken);

        AuthResponse authResponse = AuthResponse.builder()
                .user(tokenPair.user)
                .message("Registration successful")
                .build();

        log.info("User registered successfully: {}", request.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(authResponse);
    }

    /**
     * Login user
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        
        log.info("Login attempt for email: {}", request.getEmail());
        
        AuthService.TokenPair tokenPair = authService.login(request);

        // Set tokens in HTTP-only cookies
        cookieUtil.addAccessTokenCookie(response, tokenPair.accessToken);
        cookieUtil.addRefreshTokenCookie(response, tokenPair.refreshToken);

        AuthResponse authResponse = AuthResponse.builder()
                .user(tokenPair.user)
                .message("Login successful")
                .build();

        log.info("User logged in successfully: {}", request.getEmail());
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
        cookieUtil.addAccessTokenCookie(response, tokenPair.accessToken);
        cookieUtil.addRefreshTokenCookie(response, tokenPair.refreshToken);

        AuthResponse authResponse = AuthResponse.builder()
                .user(tokenPair.user)
                .message("Token refreshed successfully")
                .build();

        log.debug("Token refreshed successfully for user: {}", tokenPair.user.getEmail());
        return ResponseEntity.ok(authResponse);
    }

    /**
     * Get current authenticated user
     * Uses @AuthenticationPrincipal to get user from security context
     */
    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        if (userPrincipal == null) {
            log.warn("Unauthenticated access attempt to /me endpoint");
            throw new UnauthorizedException("Authentication required");
        }

        log.debug("Fetching current user: {}", userPrincipal.getEmail());
        
        UserResponse user = authService.getUserById(userPrincipal.getId());

        AuthResponse authResponse = AuthResponse.builder()
                .user(user)
                .build();

        return ResponseEntity.ok(authResponse);
    }

    /**
     * Logout user
     * Clears refresh token from database and removes cookies
     */
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletResponse response) {

        if (userPrincipal == null) {
            log.warn("Unauthenticated logout attempt");
            throw new UnauthorizedException("User not authenticated");
        }

        log.info("Logout request for user: {}", userPrincipal.getEmail());

        MessageResponse messageResponse = authService.logout(userPrincipal.getId());

        // Clear authentication cookies
        cookieUtil.clearAuthCookies(response);

        log.info("User logged out successfully: {}", userPrincipal.getEmail());
        return ResponseEntity.ok(messageResponse);
    }

    /**
     * Verify email with verification token
     */
    @GetMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(@RequestParam String token) {
        log.info("Email verification attempt with token");
        
        MessageResponse response = authService.verifyEmail(token);
        
        log.info("Email verified successfully");
        return ResponseEntity.ok(response);
    }

    /**
     * Request password reset
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        
        log.info("Password reset requested for email: {}", request.getEmail());
        
        MessageResponse response = authService.forgotPassword(request);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Reset password with token
     */
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        
        log.info("Password reset attempt with token");
        
        MessageResponse response = authService.resetPassword(request);
        
        log.info("Password reset successfully");
        return ResponseEntity.ok(response);
    }

    /**
     * Resend email verification
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(
            @Valid @RequestBody ForgotPasswordRequest request) {
        
        log.info("Resend verification requested for email: {}", request.getEmail());
        
        MessageResponse response = authService.resendVerification(request);
        
        return ResponseEntity.ok(response);
    }
}