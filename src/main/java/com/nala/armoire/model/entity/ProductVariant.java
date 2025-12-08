package com.nala.armoire.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "product_variants")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 20)
    private String size; // S, M, L, XL, XXL

    @Column(nullable = false, length = 50)
    private String color;

    @Column(name = "color_hex", length = 7)
    private String colorHex; // #FFFFFF

    @Column(name = "stock_quantity")
    @Builder.Default
    private Integer stockQuantity = 0;

    // CHANGED: Double -> BigDecimal for precise price handling
    @Column(name = "additional_price", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal additionalPrice = BigDecimal.ZERO;

    @Column(nullable = false, unique = true, length = 100)
    private String sku;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
}