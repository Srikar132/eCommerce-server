package com.nala.armoire.service;

import com.nala.armoire.exception.ResourceNotFoundException;
import com.nala.armoire.model.dto.request.UpdateProfileRequest;
import com.nala.armoire.model.dto.response.UserProfileDTO;
import com.nala.armoire.model.entity.User;
import com.nala.armoire.repository.RefreshTokenRepository;
import com.nala.armoire.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;  // Bug Fix #5

    @Transactional(readOnly = true)
    public UserProfileDTO getUserProfile(UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User Not Found"));

        return mapToProfileDTO(user);
    }

    @Transactional()
    public UserProfileDTO updateUserProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User Not Found"));
        
        // Update username if provided
        if (request.getUsername() != null && !request.getUsername().trim().isEmpty()) {
            user.setUserName(request.getUsername().trim());
        }
        
        // Update email if provided and check for uniqueness
        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            String newEmail = request.getEmail().trim().toLowerCase();
            
            // Check if email is different from current
            if (!newEmail.equals(user.getEmail())) {
                // Check if email already exists for another user
                if (userRepository.existsByEmail(newEmail)) {
                    throw new IllegalArgumentException("Email already in use by another account");
                }
                user.setEmail(newEmail);
                // Reset email verification when email is changed
                user.setEmailVerified(false);
            }
        }

        User updatedUser = userRepository.save(user);
        log.info("User profile updated successfully for userId: {}", userId);

        return mapToProfileDTO(updatedUser);
    }



    private UserProfileDTO mapToProfileDTO(User user ) {
        return UserProfileDTO.builder()
                .id(String.valueOf(user.getId()))
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
     * Bug Fix #5: Delete all refresh tokens for a user
     * Called during user deletion or security cleanup
     * Note: With CASCADE constraint, this is redundant during user deletion
     * but useful for manual cleanup scenarios
     */
    @Transactional
    public int deleteAllRefreshTokensForUser(UUID userId) {
        int deletedCount = refreshTokenRepository.deleteAllByUserId(userId);
        log.info("Deleted {} refresh tokens for userId: {}", deletedCount, userId);
        return deletedCount;
    }

}
