package com.nala.armoire.model.entity;



/**
 * Order Status Enum
 * Defines the lifecycle of an order
 */
public enum OrderStatus {
    PENDING("Payment pending"),
    CONFIRMED("Order confirmed"),
    PROCESSING("Order is being processed"),
    SHIPPED("Order has been shipped"),
    DELIVERED("Order delivered successfully"),
    CANCELLED("Order cancelled"),
    RETURN_REQUESTED("Return requested by customer"),
    RETURNED("Order returned"),
    REFUNDED("Payment refunded");

    private final String description;

    OrderStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if order can be cancelled
     */
    public boolean canBeCancelled() {
        return this == PENDING || this == CONFIRMED || this == PROCESSING;
    }

    /**
     * Check if return can be requested
     */
    public boolean canRequestReturn() {
        return this == DELIVERED;
    }
}