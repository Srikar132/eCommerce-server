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
 * Authentication Service with Phone OTP
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
     * Verify OTP and login/register user
     */
    @Transactional
    public TokenPair verifyOtpAndLogin(VerifyOtpRequest request) {
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

        // 7. Generate tokens
        return generateTokenPair(user);
    }

    /**
     * Refresh access token
     */
    @Transactional
    public TokenPair refreshToken(String refreshTokenString) {
        try {
            // 1. Validate the refresh token
            if (!tokenProvider.validateToken(refreshTokenString)) {
                throw new UnauthorizedException("Invalid refresh token");
            }

            // 2. Find refresh token in the database
            RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenString)
                .orElseThrow(() -> new UnauthorizedException("Refresh token not found. Please login again."));

            // 3. Check if revoked or expired
            if (refreshToken.getRevoked()) {
                throw new UnauthorizedException("Refresh token has been revoked. Please login again.");
            }
            
            if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
                refreshTokenRepository.delete(refreshToken);
                throw new UnauthorizedException("Refresh token has expired. Please login again.");
            }

            // 4. Get user
            User user = refreshToken.getUser();
            
            if (!user.getIsActive()) {
                throw new UnauthorizedException("Account is inactive. Please contact support.");
            }

            // 5. Delete old refresh token
            refreshTokenRepository.delete(refreshToken);

            log.info("Token refreshed successfully for user: {}", user.getId());

            // 6. Generate new tokens
            return generateTokenPair(user);
            
        } catch (UnauthorizedException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error during token refresh: {}", ex.getMessage(), ex);
            throw new UnauthorizedException("Failed to refresh token. Please login again.");
        }
    }

    /**
     * Logout user
     */
    @Transactional
    public MessageResponse logout(UUID userId) {
        if (userId == null) {
            throw new UnauthorizedException("User ID cannot be null");
        }

        userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Delete all refresh tokens for this user
        refreshTokenRepository.deleteByUserId(userId);
        refreshTokenRepository.flush();

        log.info("User logged out: {}", userId);

        return MessageResponse.builder()
            .message("Logged out successfully")
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
     * Generate tokens and save refresh token
     */
    private TokenPair generateTokenPair(User user) {
        // Generate access token
        String accessToken = tokenProvider.generateAccessToken(user);

        // Generate refresh token
        String refreshTokenString = tokenProvider.generateRefreshToken(user.getId());

        // Save refresh token to database
        RefreshToken refreshToken = RefreshToken.builder()
                .token(refreshTokenString)
                .user(user)
                .revoked(false)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000))
                .build();

        refreshTokenRepository.save(refreshToken);

        log.info("Tokens generated successfully for user: {}", user.getId());

        return TokenPair.builder()
            .accessToken(accessToken)
            .refreshToken(refreshTokenString)
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
     * Token pair holder
     */
    @Data
    @Builder
    @AllArgsConstructor
    public static class TokenPair {
        private String accessToken;
        private String refreshToken;
        private UserResponse user;
    }
}
