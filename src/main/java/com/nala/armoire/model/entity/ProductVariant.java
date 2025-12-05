package com.nala.armoire.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.util.UUID;

@Entity
@Table(name = "product_variants")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariant {

    @Id
    @GeneratedValue
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
    private Integer stockQuantity = 0;

    @Column(name = "additional_price")
    private Double additionalPrice = 0.0;

    @Column(nullable = false, unique = true, length = 100)
    private String sku;

    @Column(name = "is_active")
    private Boolean isActive = true;
}