package com.nala.armoire.model.dto.request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;;

@Data
public class UpdateProfileRequest {
    @NotBlank(message = "username is required")
    @Size(min = 2, max = 100, message = "username must be between 2 and 100 characters")
    private String username;

    @Pattern(regexp = "^[+]?[0-9]{10,20}$", message = "Invalid phone number")
    private String phone;
}