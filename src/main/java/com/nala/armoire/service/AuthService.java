package com.nala.armoire.service;

import com.nala.armoire.exception.BadRequestException;
import com.nala.armoire.exception.ResourceNotFoundException;
import com.nala.armoire.exception.UnauthorizedException;
import com.nala.armoire.model.dto.request.SendOtpRequest;
import com.nala.armoire.model.dto.request.VerifyOtpRequest;
import com.nala.armoire.model.dto.response.MessageResponse;
import com.nala.armoire.model.dto.response.SendOtpResponse;
import com.nala.armoire.model.dto.response.UserResponse;
import com.nala.armoire.model.entity.RefreshToken;
import com.nala.armoire.model.entity.User;
import com.nala.armoire.repository.RefreshTokenRepository;
import com.nala.armoire.repository.UserRepository;
import com.nala.armoire.security.JwtTokenProvider;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Authentication Service with Phone OTP and Refresh Token Rotation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OtpService otpService;
    private final SmsService smsService;
    private final JwtTokenProvider tokenProvider;

    @Value("${otp.expiration:300}")
    private int otpExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private Long refreshTokenExpiration;

    /**
     * Send OTP to phone number
     * Creates user if doesn't exist
     */
    @Transactional
    public SendOtpResponse sendOtp(SendOtpRequest request) {
        // 1. Validate and format phone number
        String formattedPhone = validateAndFormatPhone(request.getPhone());

        // 2. Check if user exists, create if not
        User user = userRepository.findByPhone(formattedPhone)
            .orElseGet(() -> createPendingUser(formattedPhone));

        // 3. Check if account is blocked
        if (!user.getIsActive()) {
            throw new BadRequestException("Account is inactive. Please contact support.");
        }

        // 4. Send OTP
        otpService.sendOtp(formattedPhone);

        log.info("OTP sent to phone: {}", maskPhone(formattedPhone));

        return SendOtpResponse.builder()
            .success(true)
            .message("OTP sent successfully")
            .expiresIn(otpExpiration)
            .maskedPhone(maskPhone(formattedPhone))
            .build();
    }

    /**
     * Verify OTP and login/register user - Returns tokens with rotation
     */
    @Transactional
    public AuthToken verifyOtpAndLogin(VerifyOtpRequest request) {
        // 1. Validate phone number
        String formattedPhone = validateAndFormatPhone(request.getPhone());

        // 2. Verify OTP
        otpService.verifyOtp(formattedPhone, request.getOtp());

        // 3. Find or create user
        User user = userRepository.findByPhone(formattedPhone)
            .orElseThrow(() -> new ResourceNotFoundException("User not found. Please request OTP again."));

        // 4. Check if account is active
        if (!user.getIsActive()) {
            throw new UnauthorizedException("Account is inactive. Please contact support.");
        }

        // 5. Mark phone as verified
        if (Boolean.FALSE.equals(user.getPhoneVerified())) {
            user.setPhoneVerified(true);
            user.setPhoneVerifiedAt(LocalDateTime.now());
            
            // Send welcome SMS for new users
            try {
                smsService.sendWelcomeSms(formattedPhone, user.getUserName());
            } catch (Exception e) {
                log.warn("Failed to send welcome SMS: {}", e.getMessage());
            }
        }

        // 6. Reset failed login attempts
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        // 7. Generate tokens with rotation
        return generateAuthTokens(user);
    }

    /**
     * Refresh access token using refresh token rotation
     */
    @Transactional
    public AuthToken refreshAccessToken(String refreshTokenString) {
        try {
            // 1. Validate refresh token format
            if (!tokenProvider.validateToken(refreshTokenString)) {
                throw new UnauthorizedException("Invalid refresh token");
            }

            // 2. Extract token ID and user ID
            String tokenId = tokenProvider.getTokenId(refreshTokenString);
            UUID userId = tokenProvider.getUserIdFromToken(refreshTokenString);

            // 3. Verify token type
            String tokenType = tokenProvider.getTokenType(refreshTokenString);
            if (!"refresh".equals(tokenType)) {
                throw new UnauthorizedException("Token is not a refresh token");
            }

            // 4. Find and validate refresh token in database
            RefreshToken refreshToken = refreshTokenRepository
                .findValidTokenByTokenId(tokenId, LocalDateTime.now())
                .orElseThrow(() -> new UnauthorizedException("Refresh token not found or expired"));

            // 5. Check if token was revoked
            if (refreshToken.getRevoked()) {
                // Token reuse detected - revoke all user tokens (security breach)
                log.warn("Refresh token reuse detected for user: {}", userId);
                refreshTokenRepository.revokeAllUserTokens(userId, LocalDateTime.now());
                throw new UnauthorizedException("Token reuse detected. All sessions have been terminated. Please login again.");
            }

            // 6. Get user
            User user = refreshToken.getUser();
            if (!user.getIsActive()) {
                throw new UnauthorizedException("Account is inactive");
            }

            // 7. Generate new tokens (rotation)
            AuthToken newTokens = generateAuthTokens(user);

            // 8. Revoke old refresh token and link to new one
            refreshToken.revoke(tokenProvider.getTokenId(newTokens.getRefreshToken()));
            refreshTokenRepository.save(refreshToken);

            log.info("Access token refreshed successfully for user: {}", userId);

            return newTokens;

        } catch (UnauthorizedException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error during token refresh: {}", ex.getMessage(), ex);
            throw new UnauthorizedException("Failed to refresh token. Please login again.");
        }
    }

    /**
     * Logout user - Revoke all refresh tokens
     */
    @Transactional
    public MessageResponse logout(UUID userId) {
        if (userId == null) {
            throw new UnauthorizedException("User ID cannot be null");
        }

        userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Revoke all refresh tokens for this user
        int revokedCount = refreshTokenRepository.revokeAllUserTokens(userId, LocalDateTime.now());

        log.info("User logged out: {} ({} tokens revoked)", userId, revokedCount);

        return MessageResponse.builder()
            .message("Logged out successfully. All sessions have been terminated.")
            .success(true)
            .build();
    }

    /**
     * Get user by ID
     */
    public UserResponse getUserById(UUID userId) {
        log.debug("Fetching user by ID: {}", userId);
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return mapToUserResponse(user);
    }

    /**
     * Create pending user (before OTP verification)
     */
    private User createPendingUser(String phone) {
        User user = User.builder()
            .phone(phone)
            .countryCode(extractCountryCode(phone))
            .phoneVerified(false)
            .isActive(true)
            .failedLoginAttempts(0)
            .build();

        user = userRepository.save(user);
        log.info("Pending user created for phone: {}", maskPhone(phone));

        return user;
    }

    /**
     * Validate and format phone number using libphonenumber
     */
    private String validateAndFormatPhone(String phone) {
        try {
            PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
            PhoneNumber number = phoneUtil.parse(phone, null);

            if (!phoneUtil.isValidNumber(number)) {
                throw new BadRequestException("Invalid phone number");
            }

            // Format in E164 format: +919876543210
            return phoneUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.E164);

        } catch (Exception e) {
            log.error("Phone validation failed: {}", e.getMessage());
            throw new BadRequestException("Invalid phone number format. Use international format: +919876543210");
        }
    }

    /**
     * Extract country code from phone
     */
    private String extractCountryCode(String phone) {
        try {
            PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
            PhoneNumber number = phoneUtil.parse(phone, null);
            return "+" + number.getCountryCode();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Mask phone number for logging
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return phone.substring(0, phone.length() - 4) + "****";
    }

    /**
     * Generate access and refresh tokens with rotation
     */
    private AuthToken generateAuthTokens(User user) {
        // Generate tokens
        String accessToken = tokenProvider.generateAccessToken(user);
        String refreshToken = tokenProvider.generateRefreshToken(user);
        
        // Extract token ID from refresh token
        String tokenId = tokenProvider.getTokenId(refreshToken);

        // Save refresh token to database
        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .tokenId(tokenId)
                .user(user)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshTokenEntity);

        log.info("Tokens generated successfully for user: {}", user.getId());

        return AuthToken.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .tokenType("Bearer")
            .expiresIn(tokenProvider.getAccessTokenExpirationInSeconds())
            .user(mapToUserResponse(user))
            .build();
    }

    /**
     * Map User entity to UserResponse DTO
     */
    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
            .id(user.getId())
            .phone(user.getPhone())
            .countryCode(user.getCountryCode())
            .phoneVerified(user.getPhoneVerified())
            .phoneVerifiedAt(user.getPhoneVerifiedAt())
            .email(user.getEmail())
            .emailVerified(user.getEmailVerified())
            .username(user.getUserName())
            .role(user.getRole())
            .isActive(user.getIsActive())
            .createdAt(user.getCreatedAt())
            .updatedAt(user.getUpdatedAt())
            .failedLoginAttempts(user.getFailedLoginAttempts())
            .lockedUntil(user.getLockedUntil())
            .build();
    }

    /**
     * Auth token holder with refresh token
     */
    @Data
    @Builder
    @AllArgsConstructor
    public static class AuthToken {
        private String accessToken;
        private String refreshToken;
        private String tokenType;
        private Long expiresIn; // access token expiry in seconds
        private UserResponse user;
    }
}
