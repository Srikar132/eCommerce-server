package com.nala.armoire.controller;

import com.nala.armoire.annotation.CurrentUser;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CookieUtil cookieUtil;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {

        AuthService.TokenPair tokenPair = authService.register(request);

        // Set tokens in HTTP-only cookies
        cookieUtil.addAccessTokenCookie(response, tokenPair.accessToken);
        cookieUtil.addRefreshTokenCookie(response, tokenPair.refreshToken);

        // Return only user data (no tokens in body)
        AuthResponse authResponse = AuthResponse.builder()
                .user(tokenPair.user)
                .message("Registration successful")
                .build();
        System.out.println("User register controller");

        return ResponseEntity.status(HttpStatus.CREATED).body(authResponse);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {

        AuthService.TokenPair tokenPair = authService.login(request);

        // Set tokens in HTTP-only cookies
        cookieUtil.addAccessTokenCookie(response, tokenPair.accessToken);
        cookieUtil.addRefreshTokenCookie(response, tokenPair.refreshToken);

        // Return only user data
        AuthResponse authResponse = AuthResponse.builder()
                .user(tokenPair.user)
                .message("Login successful")
                .build();

        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(
            HttpServletRequest request,
            HttpServletResponse response) {

        // Extract refresh token from cookie
        String refreshToken = cookieUtil.extractTokenFromCookie(request, "refreshToken");

        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        AuthService.TokenPair tokenPair = authService.refreshToken(refreshToken);

        // Set new tokens in cookies
        cookieUtil.addAccessTokenCookie(response, tokenPair.accessToken);
        cookieUtil.addRefreshTokenCookie(response, tokenPair.refreshToken);

        // Return user data
        AuthResponse authResponse = AuthResponse.builder()
                .user(tokenPair.user)
                .message("Token refreshed successfully")
                .build();
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(
            @CurrentUser UserPrincipal currentUser,
            HttpServletRequest request,
            HttpServletResponse response) {

        // Validate current user exists
        if (currentUser == null) {
            throw new UnauthorizedException("User not authenticated");
        }

        // Optional: Validate access token from cookie for extra security
        String accessToken = cookieUtil.extractTokenFromCookie(request, "accessToken");
        if (accessToken != null) {
            // This will also validate if the token belongs to the current user
            authService.getCurrentUser(accessToken);
        }

        MessageResponse messageResponse = authService.logout(currentUser.getId());

        // Clear cookies
        cookieUtil.clearAuthCookies(response);

        return ResponseEntity.ok(messageResponse);
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getCurrentUser(HttpServletRequest request) {
        // Extract access token from cookie
        String accessToken = cookieUtil.extractTokenFromCookie(request, "accessToken");

        if (accessToken == null) {
            throw new UnauthorizedException("Invalid token");
        }

        UserResponse user = authService.getCurrentUser(accessToken);

        AuthResponse authResponse = AuthResponse.builder()
                .user(user)
                .build();

        return ResponseEntity.ok(authResponse);
    }

    @GetMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(@RequestParam String token) {
        return ResponseEntity.ok(authService.verifyEmail(token));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(
            @Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.resendVerification(request));
    }
}