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
@Table(name = "customization", indexes = {
    @Index(name = "idx_customization_user", columnList = "userId"),
    @Index(name = "idx_customization_product", columnList = "productId"),
    @Index(name = "idx_customization_variant", columnList = "variantId"),
    @Index(name = "idx_customization_design", columnList = "designId"),
    @Index(name = "idx_customization_user_product", columnList = "userId, productId"),
    @Index(name = "idx_customization_created_at", columnList = "createdAt")
})
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

    @Column(length = 500)
    private String additionalNotes;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
}