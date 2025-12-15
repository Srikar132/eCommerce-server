package com.nala.armoire.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
public class Customization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customization_id", nullable = false, unique = true)
    private String customizationId;

    @Column(name = "user_id")
    private UUID userId; // Nullable for guest users - matches User entity

    @Column(name = "session_id", length = 100)
    private String sessionId; // For guest tracking

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "preview_image_url", length = 500)
    private String previewImageUrl;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    // Stores complete customization configuration
    @Column(name = "configuration_json", columnDefinition = "TEXT", nullable = false)
    private String configurationJson;

    // Quick metadata for queries without parsing JSON
    @Column(name = "has_text")
    private Boolean hasText = false;

    @Column(name = "has_design")
    private Boolean hasDesign = false;

    @Column(name = "has_uploaded_image")
    private Boolean hasUploadedImage = false;

    @Column(name = "layer_count")
    private Integer layerCount = 0;

    @Column(name = "is_completed")
    private Boolean isCompleted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;

    @PreUpdate
    protected void onUpdate() {
        lastAccessedAt = LocalDateTime.now();
    }
}
