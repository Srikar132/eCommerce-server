package com.nala.armoire.model.entity;

/**
 * Payment Status Enum
 */
public enum PaymentStatus {
    PENDING("Payment pending"),
    PROCESSING("Payment is being processed"),
    PAID("Payment successful"),
    FAILED("Payment failed"),
    REFUNDED("Payment refunded"),
    PARTIALLY_REFUNDED("Partially refunded");

    private final String description;

    PaymentStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}