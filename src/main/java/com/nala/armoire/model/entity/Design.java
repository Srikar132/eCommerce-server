package com.nala.armoire.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "designs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Design {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "design_category_id", nullable = false)
    private DesignCategory category;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, unique = true, length = 255)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "design_image_url", nullable = false, columnDefinition = "TEXT")
    private String designImageUrl;

    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    @Column(length = 500)
    private String tags;

    @Column(name = "allowed_product_types", columnDefinition = "TEXT")
    private String allowedProductTypes;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "is_premium")
    private Boolean isPremium = false;

    @Column(name = "download_count")
    private Long downloadCount = 0L;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
