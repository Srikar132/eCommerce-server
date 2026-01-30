package com.nala.armoire.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "carts", indexes = {
    @Index(name = "idx_cart_user", columnList = "user_id"),
    @Index(name = "idx_cart_session", columnList = "session_id"),
    @Index(name = "idx_cart_created_at", columnList = "created_at"),
    @Index(name = "idx_cart_updated_at", columnList = "updated_at"),
    @Index(name = "idx_cart_expires_at", columnList = "expires_at")  // Bug Fix #10: For cleanup jobs
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "session_id", length = 255, unique = true)
    private String sessionId;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CartItem> items = new ArrayList<>();

    @Column(name = "subtotal", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "shipping_cost", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal shippingCost = BigDecimal.ZERO;

    @Column(name = "total", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    @Transient
    @Builder.Default
    private BigDecimal gstRate = BigDecimal.valueOf(0.18); // Default 18%, overridden by service

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    //Helper methods - Bug Fix #1: Proper cascade removal handling
    public void addItem(CartItem item) {
        items.add(item);
        item.setCart(this);
        recalculateTotals();
    }

    public void removeItem(CartItem item) {
        if (items.remove(item)) {  // ✅ Remove from collection first
            item.setCart(null);
            recalculateTotals();
        }
    }

    // Bug Fix #3: Safe null handling for gstRate
    public void recalculateTotals() {

        this.subtotal = items.stream()
                .map(item -> item.getItemTotal() != null
                        ? item.getItemTotal()
                        : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal safeDiscount = discountAmount != null ? discountAmount : BigDecimal.ZERO;

        BigDecimal taxableAmount = subtotal.subtract(safeDiscount);

        // ✅ Bug Fix #3: Safe null handling with default value
        BigDecimal effectiveGstRate = (gstRate != null) ? gstRate : BigDecimal.valueOf(0.18);
        this.taxAmount = taxableAmount
                .multiply(effectiveGstRate)
                .setScale(2, RoundingMode.HALF_UP);

        // Shipping cost is determined by service based on threshold
        // Note: shippingCost should already be set by CartService before calling recalculateTotals()
        BigDecimal safeShipping = shippingCost != null ? shippingCost : BigDecimal.ZERO;

        this.total = subtotal
                .subtract(safeDiscount)
                .add(taxAmount)
                .add(safeShipping);
    }

    public void clearItems() {
        items.clear();
        recalculateTotals();
    }

    public int getTotalItemCount() {
        return items.stream()
                .mapToInt(item -> item.getQuantity() != null ? item.getQuantity() : 0)
                .sum();
    }


}
