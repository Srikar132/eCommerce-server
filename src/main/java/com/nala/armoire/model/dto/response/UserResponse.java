package com.nala.armoire.model.dto.response;

import com.nala.armoire.model.entity.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private UUID id;
    private String phone;
    private String countryCode;
    private Boolean phoneVerified;
    private LocalDateTime phoneVerifiedAt;
    private String email;
    private Boolean emailVerified;
    private String username;
    private UserRole role;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer failedLoginAttempts;
    private LocalDateTime lockedUntil;
}