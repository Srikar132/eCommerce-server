package com.nala.armoire.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * RefreshToken entity for token rotation tracking
 * Stores refresh token metadata for security and revocation
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
    @Index(name = "idx_token_id", columnList = "token_id"),
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_expires_at", columnList = "expires_at"),
    @Index(name = "idx_revoked_expires", columnList = "revoked, expires_at")  // Bug Fix #10: Token validation queries
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "token_id", nullable = false, unique = true)
    private String tokenId; // Unique ID from JWT

    // Bug Fix #5: Add CASCADE constraint for automatic deletion when user is deleted
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_refresh_token_user",
                    foreignKeyDefinition = "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE"
            )
    )
    private User user;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "revoked", nullable = false)
    @Builder.Default
    private Boolean revoked = false;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "replaced_by_token_id")
    private String replacedByTokenId; // For token rotation tracking

    /**
     * Revoke token with replacement tracking (used during token rotation)
     * @param replacedBy The new token ID that replaced this one
     */
    public void revoke(String replacedBy) {
        this.revoked = true;
        this.revokedAt = LocalDateTime.now();
        this.replacedByTokenId = replacedBy;
    }

    /**
     * Revoke token without replacement (used during logout)
     * This is semantically correct - token wasn't replaced, it was terminated
     */
    public void revokeWithoutReplacement() {
        this.revoked = true;
        this.revokedAt = LocalDateTime.now();
        this.replacedByTokenId = null; // Explicitly null - not replaced
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
