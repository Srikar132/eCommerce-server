package com.nala.armoire.repository;

import com.nala.armoire.model.entity.Cart;
import com.nala.armoire.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartRepository extends JpaRepository<Cart, UUID> {

    Optional<Cart> findByUserAndIsActiveTrue(User user);

    Optional<Cart> findBySessionIdAndIsActiveTrue(String sessionId);

    @Query("SELECT c FROM Cart c LEFT JOIN FETCH c.items WHERE c.user = :user AND c.isActive = true")
    Optional<Cart> findByUserWithItems(@Param("user") User user);

    @Query("SELECT c FROM Cart c LEFT JOIN FETCH c.items WHERE c.sessionId = :sessionId AND c.isActive = true")
    Optional<Cart> findBySessionIdWithItems(@Param("sessionId") String sessionId);

    List<Cart> findByExpiresAtBeforeAndIsActiveTrue(LocalDateTime expiresAt);

    void deleteByExpiresAtBeforeAndIsActiveTrue(LocalDateTime expiresAt);
}
