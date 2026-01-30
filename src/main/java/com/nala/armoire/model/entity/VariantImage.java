package com.nala.armoire.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Junction table entity linking ProductImage to ProductVariant
 * Allows many-to-many relationship: one image can be used by multiple variants
 */
@Entity
@Table(name = "variant_images", 
       indexes = {
           @Index(name = "idx_variant_images_variant", columnList = "product_variant_id"),
           @Index(name = "idx_variant_images_image", columnList = "product_image_id"),
           @Index(name = "idx_variant_images_composite", columnList = "product_variant_id, display_order")
       },
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_variant_image", 
                           columnNames = {"product_image_id", "product_variant_id"})
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"productImage", "productVariant"})
@ToString(exclude = {"productImage", "productVariant"})
public class VariantImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_image_id", nullable = false)
    private ProductImage productImage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id", nullable = false)
    private ProductVariant productVariant;

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
