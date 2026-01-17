package com.nala.armoire.repository;

import com.nala.armoire.model.entity.Order;
import com.nala.armoire.model.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByOrderNumber(String orderNumber);

    Optional<Order> findByRazorpayOrderId(String razorpayOrderId);

    Page<Order> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<Order> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, OrderStatus status);

    Page<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.status = :status AND o.createdAt > :since")
    List<Order> findRecentOrdersByStatus(
            @Param("status") OrderStatus status,
            @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.user.id = :userId")
    Long countOrdersByUserId(@Param("userId") UUID userId);

    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.status IN :statuses")
    Double getTotalRevenue(@Param("statuses") List<OrderStatus> statuses);

    @Query("SELECT o FROM Order o WHERE o.status = :status AND " +
            "o.createdAt < :cutoffTime")
    List<Order> findStaleOrders(
            @Param("status") OrderStatus status,
            @Param("cutoffTime") LocalDateTime cutoffTime);
}