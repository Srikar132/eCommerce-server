package com.nala.armoire.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "order_items", indexes = {
    @Index(name = "idx_order_item_order", columnList = "order_id"),
    @Index(name = "idx_order_item_variant", columnList = "product_variant_id"),
    @Index(name = "idx_order_item_production_status", columnList = "production_status"),
    @Index(name = "idx_order_item_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id", nullable = false)
    private ProductVariant productVariant;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "has_customization")
    @Builder.Default
    private Boolean hasCustomization = false;

    @Column(name = "customization_snapshot", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String customizationSnapshot; // JSON string of customization details

    @Column(name = "production_status", length = 50)
    @Builder.Default
    private String productionStatus = "PENDING"; // PENDING, IN_PROGRESS, COMPLETED

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Bug Fix #11: Enforce null validation to prevent incorrect totals
    @PrePersist
    @PreUpdate
    private void calculateTotalPrice() {
        if (unitPrice == null || quantity == null) {
            throw new IllegalStateException(
                "OrderItem validation failed: unitPrice and quantity must not be null. " +
                "OrderItem ID: " + (id != null ? id : "new") + 
                ", unitPrice: " + unitPrice + 
                ", quantity: " + quantity
            );
        }
        this.totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}