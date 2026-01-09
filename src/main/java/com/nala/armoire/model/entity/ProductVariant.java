package com.nala.armoire.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "product_variants", indexes = {
        @Index(name = "idx_product_color", columnList = "product_id, color"),
        @Index(name = "idx_sku", columnList = "sku", unique = true)
})
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
    private String size;

    @Column(nullable = false, length = 50)
    private String color;

    @Column(name = "color_hex", length = 7)
    private String colorHex;

    @Column(name = "stock_quantity")
    @Builder.Default
    private Integer stockQuantity = 0;

    @Column(name = "additional_price", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal additionalPrice = BigDecimal.ZERO;

    @Column(nullable = false, unique = true, length = 100)
    private String sku;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    // NEW: Images belong to variant
    @OneToMany(mappedBy = "variant", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC, isPrimary DESC")
    @Builder.Default
    private List<ProductImage> images = new ArrayList<>();

    // Helper method to add image
    public void addImage(ProductImage image) {
        images.add(image);
        image.setVariant(this);
    }

    // Helper method to remove image
    public void removeImage(ProductImage image) {
        images.remove(image);
        image.setVariant(null);
    }
}