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

    private UUID userId;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private UUID variantId;

    @Column(nullable = false)
    private UUID designId;

    @Column(nullable = false, length = 7)
    private String threadColorHex;

    @Column( length = 500)  // S3 URLs are typically under 500 characters
    private String previewImageUrl;

    @Builder.Default
    private Boolean isCompleted = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
