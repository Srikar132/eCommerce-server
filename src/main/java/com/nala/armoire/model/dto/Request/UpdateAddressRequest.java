package com.nala.armoire.model.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateAddressRequest {
    @Pattern(regexp = "HOME|OFFICE|OTHER", message = "Address type must be HOME, OFFICE, or OTHER")
    private String addressType;

    @Size(max = 500, message = "Street address too long")
    private String streetAddress;

    @Size(max = 100, message = "City name too long")
    private String city;

    @Size(max = 100, message = "State name too long")
    private String state;

    @Pattern(regexp = "^[0-9]{5,10}$", message = "Invalid postal code")
    private String postalCode;

    @Size(max = 100, message = "Country name too long")
    private String country;

    private Boolean isDefault;
}