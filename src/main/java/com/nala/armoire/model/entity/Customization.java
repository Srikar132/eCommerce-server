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

    @Column(nullable = false, unique = true)
    private String customizationId;

    private UUID userId;
    private String sessionId;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private UUID variantId;

    @Column(nullable = false)
    private UUID designId;

    @Column(nullable = false, length = 7)
    private String threadColorHex;

    @Column(nullable = false, length = 500)
    private String previewImageUrl;

    @Builder.Default
    private Boolean isCompleted = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
