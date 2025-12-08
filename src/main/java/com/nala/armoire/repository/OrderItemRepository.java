package com.nala.armoire.repository;

import com.nala.armoire.model.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    @Query("SELECT CASE WHEN COUNT(oi) > 0 THEN true ELSE false END " +
            "FROM OrderItem oi " +
            "JOIN oi.order o " +
            "WHERE o.user.id = :userId " +
            "AND oi.productVariant.product.id = :productId " +
            "AND o.paymentStatus = 'PAID'")
    boolean existsByUserIdAndProductId(
            @Param("userId") UUID userId,
            @Param("productId") UUID productId
    );
}
