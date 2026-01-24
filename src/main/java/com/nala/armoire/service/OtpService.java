package com.nala.armoire.service;

import com.nala.armoire.exception.BadRequestException;
import com.nala.armoire.exception.UnauthorizedException;
import com.nala.armoire.model.dto.request.OtpData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * OTP Service - Handles OTP generation, storage, and validation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final SmsService smsService;

    @Value("${otp.length:6}")
    private int otpLength;

    @Value("${otp.expiration:300}") // 5 minutes
    private long otpExpiration;

    @Value("${otp.max-attempts:3}")
    private int maxAttempts;

    @Value("${otp.rate-limit.max-requests:3}")
    private int rateLimitMaxRequests;

    @Value("${otp.rate-limit.window-seconds:600}") // 10 minutes
    private long rateLimitWindow;

    private static final String OTP_PREFIX = "otp:";
    private static final String RATE_LIMIT_PREFIX = "rate_limit:";
    private static final SecureRandom random = new SecureRandom();

    /**
     * Generate and send OTP to phone number
     */
    public void sendOtp(String phone) {
        // 1. Check rate limiting
        checkRateLimit(phone);

        // 2. Generate OTP
        String otp = generateOtp();
        log.info("Generated OTP for phone: {} (OTP: {})", maskPhone(phone), otp);

        // 3. Hash and store OTP in Redis
        OtpData otpData = OtpData.builder()
            .otpHash(passwordEncoder.encode(otp))
            .attempts(0)
            .createdAt(System.currentTimeMillis())
            .expiresAt(System.currentTimeMillis() + (otpExpiration * 1000))
            .build();

        String key = OTP_PREFIX + phone;
        redisTemplate.opsForValue().set(key, otpData, Duration.ofSeconds(otpExpiration));

        // 4. Send OTP via SMS
        smsService.sendOtp(phone, otp);

        // 5. Increment rate limit counter
        incrementRateLimit(phone);

        log.info("OTP sent successfully to phone: {}", maskPhone(phone));
    }

    /**
     * Verify OTP for phone number
     */
    public boolean verifyOtp(String phone, String otp) {
        String key = OTP_PREFIX + phone;

        // 1. Retrieve OTP data from Redis
        OtpData otpData = (OtpData) redisTemplate.opsForValue().get(key);

        if (otpData == null) {
            log.warn("OTP not found or expired for phone: {}", maskPhone(phone));
            throw new UnauthorizedException("OTP expired or invalid. Please request a new OTP.");
        }

        // 2. Check if OTP has expired
        if (System.currentTimeMillis() > otpData.getExpiresAt()) {
            redisTemplate.delete(key);
            log.warn("OTP expired for phone: {}", maskPhone(phone));
            throw new UnauthorizedException("OTP has expired. Please request a new OTP.");
        }

        // 3. Check max attempts
        if (otpData.getAttempts() >= maxAttempts) {
            redisTemplate.delete(key);
            log.warn("Max OTP attempts exceeded for phone: {}", maskPhone(phone));
            throw new UnauthorizedException("Maximum verification attempts exceeded. Please request a new OTP.");
        }

        // 4. Verify OTP
        if (!passwordEncoder.matches(otp, otpData.getOtpHash())) {
            // Increment attempt counter
            otpData.setAttempts(otpData.getAttempts() + 1);
            
            // Update in Redis
            long remainingTtl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            if (remainingTtl > 0) {
                redisTemplate.opsForValue().set(key, otpData, Duration.ofSeconds(remainingTtl));
            }

            log.warn("Invalid OTP attempt {}/{} for phone: {}", 
                     otpData.getAttempts(), maxAttempts, maskPhone(phone));
            
            throw new UnauthorizedException(
                String.format("Invalid OTP. %d attempts remaining.", 
                             maxAttempts - otpData.getAttempts())
            );
        }

        // 5. OTP is valid - delete from Redis
        redisTemplate.delete(key);
        log.info("OTP verified successfully for phone: {}", maskPhone(phone));

        return true;
    }

    /**
     * Check rate limiting for OTP requests
     */
    private void checkRateLimit(String phone) {
        String rateLimitKey = RATE_LIMIT_PREFIX + phone;
        Integer count = (Integer) redisTemplate.opsForValue().get(rateLimitKey);

        if (count != null && count >= rateLimitMaxRequests) {
            log.warn("Rate limit exceeded for phone: {}", maskPhone(phone));
            throw new BadRequestException(
                String.format("Too many OTP requests. Please try again after %d minutes.", 
                             rateLimitWindow / 60)
            );
        }
    }

    /**
     * Increment rate limit counter
     */
    private void incrementRateLimit(String phone) {
        String rateLimitKey = RATE_LIMIT_PREFIX + phone;
        
        Long count = redisTemplate.opsForValue().increment(rateLimitKey);
        
        // Set expiration on first increment
        if (count != null && count == 1) {
            redisTemplate.expire(rateLimitKey, Duration.ofSeconds(rateLimitWindow));
        }
    }

    /**
     * Generate random OTP
     */
    private String generateOtp() {
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < otpLength; i++) {
            otp.append(random.nextInt(10));
        }
        return otp.toString();
    }

    /**
     * Mask phone number for logging
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        int visibleDigits = 4;
        int maskLength = phone.length() - visibleDigits;
        return "*".repeat(maskLength) + phone.substring(maskLength);
    }

    /**
     * Check if OTP exists for phone (for testing/debugging)
     */
    public boolean otpExists(String phone) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(OTP_PREFIX + phone));
    }
}
