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
@Table(name = "cart_items")
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
    private BigDecimal unitPrice;

    @Column(name = "customization_price", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal customizationPrice = BigDecimal.ZERO;

    @Column(name = "item_total", precision = 10, scale = 2)
    private BigDecimal itemTotal;

    @Column(name = "customization_summary", columnDefinition = "TEXT")
    private String customizationSummary;

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
        BigDecimal customPrice = customizationPrice != null ? customizationPrice : BigDecimal.ZERO;
        BigDecimal totalPrice = basePrice.add(customPrice);
        this.itemTotal = totalPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
