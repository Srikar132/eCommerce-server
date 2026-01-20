package com.nala.armoire.controller;

import com.nala.armoire.annotation.CurrentUser;
import com.nala.armoire.model.dto.request.AddAddressRequest;
import com.nala.armoire.model.dto.request.UpdateAddressRequest;
import com.nala.armoire.model.dto.request.UpdateProfileRequest;
import com.nala.armoire.model.dto.response.AddressDTO;
import com.nala.armoire.model.dto.response.UserProfileDTO;
import com.nala.armoire.security.UserPrincipal;
import com.nala.armoire.service.AddressService;
import com.nala.armoire.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AddressService addressService;

    @GetMapping("/profile")
    ResponseEntity<UserProfileDTO> getUserProfile(@CurrentUser UserPrincipal currentUser) {
        UserProfileDTO profile = userService.getUserProfile(currentUser.getId());
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/profile")
    ResponseEntity<UserProfileDTO> updateUserProfile(@CurrentUser UserPrincipal currentUser,
            @Valid @RequestBody UpdateProfileRequest userProfile) {
        UserProfileDTO profile = userService.updateUserProfile(currentUser.getId(), userProfile);
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/addresses")
    ResponseEntity<List<AddressDTO>> getAddresses(@AuthenticationPrincipal UserPrincipal currentUser) {
        List<AddressDTO> userAddresses = addressService.getUserAddresses(currentUser.getId());
        
        return ResponseEntity.ok(userAddresses);
    }

    @PostMapping("/addresses")
    ResponseEntity<AddressDTO> addAddresses(
            @CurrentUser UserPrincipal currentUser,
            @Valid @RequestBody AddAddressRequest request) {
        AddressDTO address = addressService.addAddress(currentUser.getId(), request);
        return ResponseEntity.ok(address);
    }

    @PutMapping("/addresses/{id}")
    ResponseEntity<AddressDTO> updateAddress(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable String id,
            @Valid @RequestBody UpdateAddressRequest request) {
        AddressDTO address = addressService.updateAddress(
                currentUser.getId(),
                UUID.fromString(id),
                request);
        return ResponseEntity.ok(address);
    }

    @DeleteMapping("/addresses/{id}")
    ResponseEntity<AddressDTO> deleteAddress(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable String id) {
        addressService.deleteAddress(
                currentUser.getId(),
                UUID.fromString(id));
        return ResponseEntity.noContent().build();
    }
}
