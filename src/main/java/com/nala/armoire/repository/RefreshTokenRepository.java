package com.nala.armoire.repository;

import com.nala.armoire.model.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    
    Optional<RefreshToken> findByTokenId(String tokenId);

    @Query("SELECT rt FROM RefreshToken rt WHERE rt.tokenId = :tokenId AND rt.revoked = false AND rt.expiresAt > :now")
    Optional<RefreshToken> findValidTokenByTokenId(@Param("tokenId") String tokenId, @Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken rt SET rt.revoked = true, rt.revokedAt = :now WHERE rt.user.id = :userId AND rt.revoked = false")
    int revokeAllUserTokens(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    int deleteExpiredTokens(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(rt) FROM RefreshToken rt WHERE rt.user.id = :userId AND rt.revoked = false AND rt.expiresAt > :now")
    long countActiveTokensByUserId(@Param("userId") UUID userId, @Param("now") LocalDateTime now);
}
