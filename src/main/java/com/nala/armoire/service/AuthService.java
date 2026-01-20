package com.nala.armoire.service;

import com.nala.armoire.exception.BadRequestException;
import com.nala.armoire.exception.ResourceNotFoundException;
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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
        try {
            // Check if email already exists
            if(userRepository.existsByEmail(request.getEmail())) {
                log.warn("Registration attempt with existing email: {}", request.getEmail());
                throw new BadRequestException("This email is already registered. Please login or use a different email.");
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
            
            log.info("New user registered successfully: {}", user.getEmail());
            
            // Send verification email
            try {
                emailService.sendVerificationEmail(user);
            } catch (Exception e) {
                log.error("Failed to send verification email to: {}", user.getEmail(), e);
                // Don't fail registration if email fails
            }

            return generateTokenPair(user);
            
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error during registration: {}", ex.getMessage(), ex);
            throw new BadRequestException("Registration failed. Please try again.");
        }
    }

    @Transactional
    public TokenPair login(LoginRequest request) {
        try {
            // First check if user exists
            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

            // Check if account is active
            if(!user.getIsActive()) {
                throw new UnauthorizedException("Your account has been deactivated. Please contact support.");
            }

            // Check if email is verified (optional - uncomment if you want to enforce this)
            // if(!user.getEmailVerified()) {
            //     throw new UnauthorizedException("Please verify your email before logging in. Check your inbox for the verification link.");
            // }

            // Authenticate user
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            return generateTokenPair(user);
            
        } catch (org.springframework.security.authentication.BadCredentialsException ex) {
            log.warn("Failed login attempt for email: {}", request.getEmail());
            throw new UnauthorizedException("Invalid email or password. Please check your credentials and try again.");
        } catch (org.springframework.security.authentication.DisabledException ex) {
            log.warn("Disabled account login attempt: {}", request.getEmail());
            throw new UnauthorizedException("Your account has been disabled. Please contact support.");
        } catch (org.springframework.security.authentication.LockedException ex) {
            log.warn("Locked account login attempt: {}", request.getEmail());
            throw new UnauthorizedException("Your account has been locked. Please contact support or try again later.");
        } catch (UnauthorizedException ex) {
            // Re-throw our custom exceptions
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error during login: {}", ex.getMessage(), ex);
            throw new UnauthorizedException("Login failed. Please try again.");
        }
    }

    @Transactional
    public TokenPair refreshToken(String refreshTokenString) {
        try {
            // Validate the refresh token
            if(!tokenProvider.validateToken(refreshTokenString)) {
                log.warn("Invalid refresh token provided");
                throw new UnauthorizedException("Invalid or expired refresh token. Please login again.");
            }

            // Find refresh token in the database
            RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenString)
                    .orElseThrow(() -> new UnauthorizedException("Refresh token not found. Please login again."));

            // Check if revoked or expired
            if(refreshToken.getRevoked()) {
                log.warn("Revoked refresh token used: {}", refreshToken.getToken());
                throw new UnauthorizedException("Refresh token has been revoked. Please login again.");
            }
            
            if(refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.warn("Expired refresh token used");
                throw new UnauthorizedException("Refresh token has expired. Please login again.");
            }

            // Get user
            User user = refreshToken.getUser();
            
            if(!user.getIsActive()) {
                log.warn("Inactive user attempted token refresh: {}", user.getEmail());
                throw new UnauthorizedException("Your account has been deactivated. Please contact support.");
            }

            refreshTokenRepository.delete(refreshToken);

            log.info("Token refreshed successfully for user: {}", user.getEmail());

            // Generate new tokens
            return generateTokenPair(user);
            
        } catch (UnauthorizedException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error during token refresh: {}", ex.getMessage(), ex);
            throw new UnauthorizedException("Failed to refresh token. Please login again.");
        }
    }

    public UserResponse getCurrentUser(String accessToken) {
        try {
            // Validate token
            if(!tokenProvider.validateToken(accessToken)) {
                throw new UnauthorizedException("Invalid or expired access token. Please login again.");
            }

            // Extract user ID from token
            UUID userId = tokenProvider.getUserIdFromToken(accessToken);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new UnauthorizedException("User not found. Please login again."));

            if(!user.getIsActive()) {
                throw new UnauthorizedException("Your account has been deactivated. Please contact support.");
            }

            return mapToUserResponse(user);
            
        } catch (UnauthorizedException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error fetching current user: {}", ex.getMessage(), ex);
            throw new UnauthorizedException("Failed to get user information. Please login again.");
        }
    }

    @Transactional
    public MessageResponse verifyEmail(String token) {
        try {
            User user = userRepository.findByVerificationToken(token)
                    .orElseThrow(() -> new BadRequestException("Invalid verification link. The token may have been used or is incorrect."));

            if(user.getEmailVerified()) {
                log.info("Email already verified for user: {}", user.getEmail());
                return MessageResponse.builder()
                        .message("Email is already verified. You can proceed to login.")
                        .success(true)
                        .build();
            }

            if(user.getVerificationTokenExpiresAt().isBefore(LocalDateTime.now())) {
                log.warn("Expired verification token used for user: {}", user.getEmail());
                throw new BadRequestException("Verification link has expired. Please request a new verification email.");
            }

            user.setEmailVerified(true);
            user.setVerificationToken(null);
            user.setVerificationTokenExpiresAt(null);
            userRepository.save(user);

            log.info("Email verified successfully for user: {}", user.getEmail());

            return MessageResponse.builder()
                    .message("Email verified successfully! You can now login to your account.")
                    .success(true)
                    .build();
                    
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error during email verification: {}", ex.getMessage(), ex);
            throw new BadRequestException("Email verification failed. Please try again or contact support.");
        }
    }

    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        try {
            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new BadRequestException("No account found with this email address."));

            if(!user.getIsActive()) {
                throw new BadRequestException("Your account has been deactivated. Please contact support.");
            }

            // Generate reset token
            String resetToken = UUID.randomUUID().toString();
            user.setPasswordResetToken(resetToken);
            user.setPasswordResetTokenExpiresAt(LocalDateTime.now().plusHours(1));
            userRepository.save(user);

            log.info("Password reset requested for user: {}", user.getEmail());

            // Send reset email
            try {
                emailService.sendPasswordResetEmail(user, resetToken);
            } catch (Exception e) {
                log.error("Failed to send password reset email to: {}", user.getEmail(), e);
                throw new BadRequestException("Failed to send password reset email. Please try again.");
            }

            return MessageResponse.builder()
                    .message("Password reset link has been sent to your email. Please check your inbox.")
                    .success(true)
                    .build();
                    
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error during forgot password: {}", ex.getMessage(), ex);
            throw new BadRequestException("Failed to process password reset request. Please try again.");
        }
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        try {
            User user = userRepository.findByPasswordResetToken(request.getToken())
                    .orElseThrow(() -> new BadRequestException("Invalid or expired password reset link."));

            if (user.getPasswordResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
                log.warn("Expired password reset token used for user: {}", user.getEmail());
                throw new BadRequestException("Password reset link has expired. Please request a new one.");
            }

            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
            user.setPasswordResetToken(null);
            user.setPasswordResetTokenExpiresAt(null);
            userRepository.save(user);

            log.info("Password reset successfully for user: {}", user.getEmail());

            return MessageResponse.builder()
                    .message("Password reset successful! You can now login with your new password.")
                    .success(true)
                    .build();
                    
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error during password reset: {}", ex.getMessage(), ex);
            throw new BadRequestException("Failed to reset password. Please try again or request a new reset link.");
        }
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


    public UserResponse getUserById(UUID userId) {
    log.debug("Fetching user by ID: {}", userId);
    
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    return UserResponse.builder()
            .id(user.getId())
            .email(user.getEmail())
            .username(user.getUserName())
            .phone(user.getPhone())
            .role(user.getRole())
            .emailVerified(user.getEmailVerified())
            .createdAt(user.getCreatedAt())
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

        // Save refresh token to a database
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
                .username(user.getUserName())
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