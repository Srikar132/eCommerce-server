package com.nala.armoire.service;


import com.nala.armoire.exception.ResourceNotFoundException;
import com.nala.armoire.model.dto.request.AddAddressRequest;
import com.nala.armoire.model.dto.request.UpdateAddressRequest;
import com.nala.armoire.model.dto.response.AddressDTO;
import com.nala.armoire.model.entity.Address;
import com.nala.armoire.model.entity.User;
import com.nala.armoire.repository.AddressRepository;
import com.nala.armoire.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AddressService {
    private final AddressRepository addressRepository;
    private final UserRepository userRepository;


    /*
    *   GET All Addresses of a user
    * */
    @Transactional(readOnly = true)
    public List<AddressDTO> getUserAddresses(UUID userId) {
        List<Address> addresses = addressRepository.findByUserId(userId);
        return addresses.stream()
                .map(this::mapToAddressDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public AddressDTO addAddress(UUID userId, AddAddressRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // If this is set as default, clear other defaults
        if (request.isDefault()) {
            addressRepository.clearDefaultAddressForUser(userId);
        }

        Address address = Address.builder()
                .user(user)
                .addressType(request.getAddressType())
                .streetAddress(request.getStreetAddress())
                .city(request.getCity())
                .state(request.getState())
                .postalCode(request.getPostalCode())
                .country(request.getCountry())
                .isDefault(request.isDefault())
                .build();

        Address savedAddress = addressRepository.save(address);
        log.info("Address added successfully for userId: {}, addressId: {}", userId, savedAddress.getId());

        return mapToAddressDTO(savedAddress);
    }

    @Transactional
    public AddressDTO updateAddress(UUID userId, UUID addressId, UpdateAddressRequest request) {
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        // Update fields if provided
        if (request.getAddressType() != null) {
            address.setAddressType(request.getAddressType());
        }
        if (request.getStreetAddress() != null) {
            address.setStreetAddress(request.getStreetAddress());
        }
        if (request.getCity() != null) {
            address.setCity(request.getCity());
        }
        if (request.getState() != null) {
            address.setState(request.getState());
        }
        if (request.getPostalCode() != null) {
            address.setPostalCode(request.getPostalCode());
        }
        if (request.getCountry() != null) {
            address.setCountry(request.getCountry());
        }
        if (request.getIsDefault() != null) {
            if (request.getIsDefault()) {
                addressRepository.clearDefaultAddressForUser(userId);
            }
            address.setIsDefault(request.getIsDefault());
        }

        Address updatedAddress = addressRepository.save(address);
        log.info("Address updated successfully: {}", addressId);

        return mapToAddressDTO(updatedAddress);
    }

    @Transactional
    public void deleteAddress(UUID userId, UUID addressId) {
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        addressRepository.delete(address);
        log.info("Address deleted successfully: {}", addressId);
    }


    private AddressDTO mapToAddressDTO(Address address) {
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
}
