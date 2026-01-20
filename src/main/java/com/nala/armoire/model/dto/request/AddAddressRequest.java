package com.nala.armoire.model.dto.request;

// AddAddressRequest.java

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddAddressRequest {
    @NotBlank(message = "Address type is required")
    @Pattern(regexp = "HOME|OFFICE|OTHER", message = "Address type must be HOME, OFFICE, or OTHER")
    private String addressType;

    @NotBlank(message = "Street address is required")
    @Size(max = 500, message = "Street address too long")
    private String streetAddress;

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City name too long")
    private String city;

    @NotBlank(message = "State is required")
    @Size(max = 100, message = "State name too long")
    private String state;

    @NotBlank(message = "Postal code is required")
    @Pattern(regexp = "^[0-9]{5,10}$", message = "Invalid postal code")
    private String postalCode;

    @NotBlank(message = "Country is required")
    @Size(max = 100, message = "Country name too long")
    private String country;

    @JsonProperty("isDefault")
    private boolean isDefault;
}