package com.nala.armoire.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Image Asset Entity
 * Tracks all images uploaded to S3
 * Used for admin asset management
 */
@Entity
@Table(name = "image_assets", indexes = {
    @Index(name = "idx_image_asset_s3_key", columnList = "s3Key"),
    @Index(name = "idx_image_asset_created_at", columnList = "created_at"),
    @Index(name = "idx_image_asset_file_name", columnList = "fileName")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String fileName;

    @Column(nullable = false, length = 500)
    private String s3Key; // Full S3 path: assets/products/filename.jpg

    @Column(nullable = false, length = 500)
    private String imageUrl; // Public URL to access image (CDN if available, otherwise S3)

    @Column(nullable = false)
    private Long fileSize; // Size in bytes

    @Column(length = 50)
    private String mimeType; // image/jpeg, image/png, etc.

    @Column(length = 20)
    private String dimensions; // e.g., "1920x1080"

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

}