package com.nala.armoire.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutResponse {
    private String orderNumber;
    private String razorpayOrderId;
    private String razorpayKeyId;
    private BigDecimal amount; // In rupees
    private String currency;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
}