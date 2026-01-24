package com.nala.armoire.repository;


import com.nala.armoire.model.entity.User;

import io.lettuce.core.dynamic.annotation.Param;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
        /**
     * Find user by phone number (PRIMARY authentication method)
     */
    Optional<User> findByPhone(String phone);

    /**
     * Find user by email (optional, for notifications)
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if phone exists
     */
    boolean existsByPhone(String phone);

    /**
     * Check if email exists
     */
    boolean existsByEmail(String email);

    /**
     * Find active users by phone
     */
    @Query("SELECT u FROM User u WHERE u.phone = :phone AND u.isActive = true")
    Optional<User> findActiveUserByPhone(@Param("phone") String phone);

    /**
     * Count users by role
     */
    long countByRole(String role);

    /**
     * Find users by phone verified status
     */
    @Query("SELECT u FROM User u WHERE u.phoneVerified = :verified ORDER BY u.createdAt DESC")
    Iterable<User> findByPhoneVerified(@Param("verified") boolean verified);
}
