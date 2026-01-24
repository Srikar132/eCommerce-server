package com.nala.armoire.model.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OTP Data stored in Redis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpData {
    private String otpHash; // BCrypt hashed OTP
    private int attempts;
    private long createdAt;
    private long expiresAt;
}