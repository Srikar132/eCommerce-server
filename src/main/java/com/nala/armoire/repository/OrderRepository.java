package com.nala.armoire.repository;

import com.nala.armoire.model.entity.Order;
import com.nala.armoire.model.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
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

    // ===== OPTIMIZED COUNT QUERIES =====

    /**
     * Efficient count by status (replaces findByStatus(..., Pageable.unpaged()).getTotalElements())
     */
    long countByStatus(OrderStatus status);

    /**
     * Count orders by multiple statuses
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.status IN :statuses")
    long countByStatuses(@Param("statuses") List<OrderStatus> statuses);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.user.id = :userId")
    Long countOrdersByUserId(@Param("userId") UUID userId);

    // ===== OPTIMIZED REVENUE QUERIES =====

    /**
     * Get total revenue for specific statuses using BigDecimal
     */
    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status IN :statuses")
    Double getTotalRevenue(@Param("statuses") List<OrderStatus> statuses);

    /**
     * Get order count and total revenue in one query for statistics
     */
    @Query("SELECT new map(COUNT(o) as count, COALESCE(SUM(o.totalAmount), 0) as revenue) " +
            "FROM Order o WHERE o.status IN :statuses")
    List<Object> getCountAndRevenue(@Param("statuses") List<OrderStatus> statuses);

    // ===== ATTENTION REQUIRED QUERIES =====

    /**
     * Find orders requiring attention (stale orders by status)
     */
    @Query("SELECT o FROM Order o WHERE o.status = :status AND o.createdAt < :cutoffTime " +
            "ORDER BY o.createdAt ASC")
    List<Order> findStaleOrders(
            @Param("status") OrderStatus status,
            @Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Count stale orders (for quick checks without loading entities)
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status AND o.createdAt < :cutoffTime")
    long countStaleOrders(
            @Param("status") OrderStatus status,
            @Param("cutoffTime") LocalDateTime cutoffTime);

    // ===== RECENT ORDERS QUERIES =====

    @Query("SELECT o FROM Order o WHERE o.status = :status AND o.createdAt > :since " +
            "ORDER BY o.createdAt DESC")
    List<Order> findRecentOrdersByStatus(
            @Param("status") OrderStatus status,
            @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.createdAt > :since")
    long countRecentOrders(@Param("since") LocalDateTime since);

    // ===== STATUS-SPECIFIC TIMESTAMP QUERIES =====

    /**
     * Find orders delivered within a date range
     */
    @Query("SELECT o FROM Order o WHERE o.deliveredAt BETWEEN :startDate AND :endDate " +
            "ORDER BY o.deliveredAt DESC")
    List<Order> findOrdersDeliveredBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Find orders cancelled within a date range
     */
    @Query("SELECT o FROM Order o WHERE o.cancelledAt BETWEEN :startDate AND :endDate " +
            "ORDER BY o.cancelledAt DESC")
    List<Order> findOrdersCancelledBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}