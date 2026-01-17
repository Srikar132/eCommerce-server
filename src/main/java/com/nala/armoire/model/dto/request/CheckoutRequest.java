package com.nala.armoire.model.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutRequest {

    @NotNull(message = "Shipping address cannot be null")
    private UUID shippingAddressId;

    private UUID billingAddressId;

    private String notes;
}
