package com.nala.armoire.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "product_images", indexes = {
        @Index(name = "idx_variant_id", columnList = "variant_id"),
        @Index(name = "idx_variant_primary", columnList = "variant_id, is_primary")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // CHANGED: Now references ProductVariant instead of Product
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

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

    @Column(name = "image_role", length = 30)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ImageRole imageRole = ImageRole.PREVIEW_BASE;

    // Ensure alt text defaults to variant description
    @PrePersist
    protected void onCreate() {
        if (altText == null && variant != null) {
            altText = variant.getProduct().getName() + " - " + variant.getColor();
        }
    }
}