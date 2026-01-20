package com.nala.armoire.service;

import com.nala.armoire.exception.ResourceNotFoundException;
import com.nala.armoire.model.dto.request.UpdateProfileRequest;
import com.nala.armoire.model.dto.response.UserProfileDTO;
import com.nala.armoire.model.entity.User;
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
        user.setUserName(request.getUsername());
        user.setPhone(request.getPhone());

        User updatedUser = userRepository.save(user);
        log.info("User profile updated successfully for userId: {}", userId);

        return mapToProfileDTO(updatedUser);
    }



    private UserProfileDTO mapToProfileDTO(User user ) {
        return UserProfileDTO.builder()
                .id(String.valueOf(user.getId()))
                .email(user.getEmail())
                .username(user.getUserName())
                .phone(user.getPhone())
                .emailVerified(user.getEmailVerified())
                .createdAt(user.getCreatedAt())
                .build();
    }

}
