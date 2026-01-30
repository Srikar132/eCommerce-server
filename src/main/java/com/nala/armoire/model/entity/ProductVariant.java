package com.nala.armoire.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "product_variants", indexes = {
        @Index(name = "idx_product_color", columnList = "product_id, color"),
        @Index(name = "idx_sku", columnList = "sku", unique = true),
        @Index(name = "idx_variant_active", columnList = "product_id, is_active")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"product", "variantImages"})
@ToString(exclude = {"product", "variantImages"})
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

    @Column(name = "created_at", updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Version
    private Long version;

    // NEW: Many-to-many through junction table
    @OneToMany(mappedBy = "productVariant", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<VariantImage> variantImages = new ArrayList<>();

    // Helper: Get images for this variant
    public List<ProductImage> getImages() {
        return variantImages.stream()
                .sorted(Comparator.comparing(VariantImage::getDisplayOrder))
                .map(VariantImage::getProductImage)
                .collect(Collectors.toList());
    }

    // Helper method to add image
    public void addImage(ProductImage image, Integer displayOrder) {
        VariantImage variantImage = VariantImage.builder()
                .productImage(image)
                .productVariant(this)
                .displayOrder(displayOrder != null ? displayOrder : 0)
                .build();
        variantImages.add(variantImage);
        image.getVariantImages().add(variantImage);
    }

    // Helper method to remove image - Bug Fix #1: Proper cascade removal
    public void removeImage(ProductImage image) {
        variantImages.removeIf(vi -> {
            if (vi.getProductImage().equals(image)) {
                // ✅ Remove bidirectional references before orphan removal
                image.getVariantImages().remove(vi);
                return true;
            }
            return false;
        });
    }
}