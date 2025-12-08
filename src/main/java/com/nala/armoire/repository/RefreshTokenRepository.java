package com.nala.armoire.repository;

import com.nala.armoire.model.entity.RefreshToken;
import com.nala.armoire.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);

    // Explicit delete query
    @Modifying
    @Transactional
    @Query("delete from RefreshToken r where r.user = :user")
    void deleteByUser(@Param("user") User user);

    // Or, delete by user id (handy if you only have id)
    @Modifying
    @Transactional
    @Query("delete from RefreshToken r where r.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
