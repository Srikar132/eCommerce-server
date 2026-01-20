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
    private String email;
    private String username;
    private String phone;
    private UserRole role;
    private Boolean emailVerified;
    private LocalDateTime createdAt;
}