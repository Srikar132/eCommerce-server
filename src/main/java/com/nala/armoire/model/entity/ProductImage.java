package com.nala.armoire.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "product_images", indexes = {
        @Index(name = "idx_product_images_product", columnList = "product_id"),
        @Index(name = "idx_product_images_primary", columnList = "product_id, is_primary")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"product", "variantImages"})
@ToString(exclude = {"product", "variantImages"})
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // NEW: Now references Product instead of Variant
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "alt_text", length = 255)
    private String altText;

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "is_primary")
    @Builder.Default
    private Boolean isPrimary = false;

    @Column(name = "image_type", length = 50)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ImageRole imageType = ImageRole.PREVIEW_BASE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // Many-to-many through junction table
    @OneToMany(mappedBy = "productImage", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<VariantImage> variantImages = new ArrayList<>();

    // Helper: Get all variants this image applies to
    public List<ProductVariant> getApplicableVariants() {
        return variantImages.stream()
                .map(VariantImage::getProductVariant)
                .collect(Collectors.toList());
    }

    // Ensure alt text defaults to product name
    @PrePersist
    protected void onCreate() {
        if (altText == null && product != null) {
            altText = product.getName();
        }
    }
}