package com.nala.armoire.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Auth Response - Cookie-based Authentication
 * Tokens are now sent via HTTP-Only cookies, not in response body
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private UserResponse user;
    private String message;
    private Boolean success;
    
    // Note: Tokens are NOT exposed in response body for security
    // They are sent via HTTP-Only, Secure cookies
}
