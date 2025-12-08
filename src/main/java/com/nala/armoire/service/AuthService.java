package com.nala.armoire.service;

import com.nala.armoire.exception.BadRequestException;
import com.nala.armoire.model.dto.request.*;
import com.nala.armoire.exception.UnauthorizedException;
import com.nala.armoire.model.dto.response.MessageResponse;
import com.nala.armoire.model.dto.response.UserResponse;
import com.nala.armoire.model.entity.RefreshToken;
import com.nala.armoire.model.entity.User;
import com.nala.armoire.model.entity.UserRole;
import com.nala.armoire.repository.RefreshTokenRepository;
import com.nala.armoire.repository.UserRepository;
import com.nala.armoire.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${jwt.refresh-token-expiration}")
    private Long refreshTokenExpiration;

    @Value("${app.verification-token-expiration}")
    private Long verificationTokenExpiration;

    @Transactional
    public TokenPair register(RegisterRequest request) {
        // Check if email already exists
        if(userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already registered");
        }

        // Create user
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .userName(request.getUsername())
                .role(UserRole.CUSTOMER)
                .isActive(true)
                .emailVerified(false)
                .verificationToken(UUID.randomUUID().toString())
                .verificationTokenExpiresAt(
                        LocalDateTime.now().plusSeconds(verificationTokenExpiration / 1000)
                ).build();

        user = userRepository.save(user);
        emailService.sendVerificationEmail(user);

        return generateTokenPair(user);
    }

    @Transactional
    public TokenPair login(LoginRequest request) {
        // Authenticate user
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Get the user
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid User Credentials"));

        if(!user.getIsActive()) {
            throw new UnauthorizedException("Account is deactivated");
        }

        return generateTokenPair(user);
    }

    @Transactional
    public TokenPair refreshToken(String refreshTokenString) {
        // Validate the refresh token
        if(!tokenProvider.validateToken(refreshTokenString)) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        // Find refresh token in the database
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenString)
                .orElseThrow(() -> new UnauthorizedException("Refresh token not found"));

        // Check if revoked or expired
        if(refreshToken.getRevoked() || refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("Refresh token is revoked or expired");
        }

        // Get user
        User user = refreshToken.getUser();

        // Generate new tokens
        return generateTokenPair(user);
    }

    public UserResponse getCurrentUser(String accessToken) {
        // Validate token
        if(!tokenProvider.validateToken(accessToken)) {
            throw new UnauthorizedException("Invalid access token");
        }

        // Extract user ID from token
        UUID userId = tokenProvider.getUserIdFromToken(accessToken);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        return mapToUserResponse(user);
    }

    @Transactional
    public MessageResponse verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid verification token"));

        if(user.getVerificationTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Verification token is expired");
        }

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiresAt(null);
        userRepository.save(user);

        return MessageResponse.builder()
                .message("Email verified successfully")
                .success(true)
                .build();
    }

    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("User not found"));

        // Generate reset token
        String resetToken = UUID.randomUUID().toString();
        user.setPasswordResetToken(resetToken);
        user.setPasswordResetTokenExpiresAt(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        // Send reset email
        emailService.sendPasswordResetEmail(user, resetToken);

        return MessageResponse.builder()
                .message("Password reset email sent")
                .success(true)
                .build();
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByPasswordResetToken(request.getToken())
                .orElseThrow(() -> new BadRequestException("Invalid reset token"));

        if (user.getPasswordResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Reset token expired");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiresAt(null);
        userRepository.save(user);

        return MessageResponse.builder()
                .message("Password reset successful")
                .success(true)
                .build();
    }

    @Transactional
    public MessageResponse logout(UUID userId) {
        if (userId == null) {
            throw new UnauthorizedException("User ID cannot be null");
        }

        userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        // Count before
        long countBefore = refreshTokenRepository.count();
        System.out.println("Total refresh tokens BEFORE: " + countBefore);

        // Revoke all refresh tokens
        refreshTokenRepository.deleteByUserId(userId);
        refreshTokenRepository.flush(); // Force the delete

        long countAfter = refreshTokenRepository.count();
        System.out.println("Total refresh tokens AFTER: " + countAfter);
        System.out.println("DELETED REFRESH TOKEN");

        return MessageResponse.builder()
                .message("Logged out successfully")
                .success(true)
                .build();
    }

    @Transactional
    public MessageResponse resendVerification(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("User not found"));

        // Check if already verified
        if (user.getEmailVerified()) {
            return MessageResponse.builder()
                    .message("Email is already verified")
                    .success(true)
                    .build();
        }

        // Generate new verification token if needed
        if (user.getVerificationToken() == null ||
                user.getVerificationTokenExpiresAt() == null ||
                user.getVerificationTokenExpiresAt().isBefore(LocalDateTime.now())) {

            user.setVerificationToken(UUID.randomUUID().toString());
            user.setVerificationTokenExpiresAt(
                    LocalDateTime.now().plusSeconds(verificationTokenExpiration / 1000)
            );
            userRepository.save(user);
        }

        // Send verification email
        emailService.sendVerificationEmail(user);

        return MessageResponse.builder()
                .message("Verification email sent successfully")
                .success(true)
                .build();
    }

    // Helper method to generate tokens and user response
    private TokenPair generateTokenPair(User user) {
        // Generate access token
        String accessToken = tokenProvider.generateAccessToken(
                user
        );

        // Generate refresh token
        String refreshTokenString = tokenProvider.generateRefreshToken(user.getId());

        // Save refresh token to database
        RefreshToken refreshToken = RefreshToken.builder()
                .token(refreshTokenString)
                .user(user)
                .revoked(false)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000))
                .build();

        if (refreshToken != null) {
            refreshTokenRepository.save(refreshToken);
        }

        return new TokenPair(accessToken, refreshTokenString, mapToUserResponse(user));
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .userName(user.getUserName())
                .phone(user.getPhone())
                .role(user.getRole())
                .emailVerified(user.getEmailVerified())
                .createdAt(user.getCreatedAt())
                .build();
    }

    // Inner class to hold tokens and user data
    public static class TokenPair {
        public final String accessToken;
        public final String refreshToken;
        public final UserResponse user;

        public TokenPair(String accessToken, String refreshToken, UserResponse user) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.user = user;
        }
    }
}