package com.nala.armoire.model.entity;

import jakarta.persistence.*;
import lombok.*;

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
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "design_category_id")
    private DesignCategory designCategory;

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

    @Column(name = "base_price", nullable = false)
    private Double basePrice;

    @Column(name = "complexity_level", length = 20)
    private String complexityLevel; // SIMPLE, MEDIUM, COMPLEX

    @Column(name = "estimated_days")
    private Integer estimatedDays = 3;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at")
    private Date createdAt = new Date();
}
