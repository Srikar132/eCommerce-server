package com.nala.armoire.service;

import com.nala.armoire.exception.ResourceNotFoundException;
import com.nala.armoire.model.dto.Response.AddressDTO;
import com.nala.armoire.model.dto.Response.UserResponse;
import com.nala.armoire.model.entity.User;
import com.nala.armoire.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserResponse getUserProfile(UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User Not Found"));

        return mapToProfileDTO(user);
    }

    private AddressDTO mapToAddressDTO(com.nala.armoire.model.entity.Address address) {
        return AddressDTO.builder()
                .id(address.getId())
                .addressType(address.getAddressType())
                .streetAddress(address.getStreetAddress())
                .city(address.getCity())
                .state(address.getState())
                .postalCode(address.getPostalCode())
                .country(address.getCountry())
                .isDefault(address.getIsDefault())
                .createdAt(address.getCreatedAt())
                .build();
    }

    private UserResponse mapToProfileDTO(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .userName(user.getUserName())
                .phone(user.getPhone())
                .emailVerified(user.getEmailVerified())
                .createdAt(user.getCreatedAt())
                .addresses(user.getAddresses().stream()
                        .map(this::mapToAddressDTO)
                        .collect(Collectors.toList()))
                .build();
    }

}
