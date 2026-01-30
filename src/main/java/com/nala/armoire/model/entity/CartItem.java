package com.nala.armoire.model.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cart_items", indexes = {
    @Index(name = "idx_cart_item_cart", columnList = "cart_id"),
    @Index(name = "idx_cart_item_product", columnList = "product_id"),
    @Index(name = "idx_cart_item_variant", columnList = "product_variant_id"),
    @Index(name = "idx_cart_item_customization", columnList = "customization_id"),
    @Index(name = "idx_cart_item_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id")
    private ProductVariant productVariant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customization_id")
    private Customization customization;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    @Column(name = "unit_price", precision = 10, scale = 2, nullable = false)
    @Builder.Default  // *** ADD THIS ***
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "design_price", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal designPrice = BigDecimal.ZERO;

    @Column(name = "item_total", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal itemTotal = BigDecimal.ZERO;


    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void calculateItemTotal() {
        BigDecimal basePrice = unitPrice != null ? unitPrice : BigDecimal.ZERO;
        BigDecimal customPrice = designPrice != null ? designPrice : BigDecimal.ZERO;
        int qty = quantity != null ? quantity : 1;  // Also handle null quantity
        BigDecimal totalPrice = basePrice.add(customPrice);
        this.itemTotal = totalPrice.multiply(BigDecimal.valueOf(qty));
    }
}