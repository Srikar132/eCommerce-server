package com.nala.armoire.service;

import com.nala.armoire.exception.BadRequestException;
import com.nala.armoire.exception.ResourceNotFoundException;
import com.nala.armoire.model.dto.response.OrderResponseDTO;
import com.nala.armoire.model.entity.Order;
import com.nala.armoire.model.entity.OrderStatus;
import com.nala.armoire.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin Order Management Service
 * Handles administrative operations for orders
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminOrderService {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final EmailService emailService;

    /**
     * Get all orders (admin view) with pagination
     * @param pageable Pagination parameters
     * @return Page of OrderResponseDTO
     */
    @Transactional(readOnly = true)
    public Page<OrderResponseDTO> getAllOrders(Pageable pageable) {
        log.info("Admin: Fetching all orders - page: {}, size: {}",
                pageable.getPageNumber(), pageable.getPageSize());

        Page<Order> orders = orderRepository.findAll(pageable);
        return orders.map(this::mapToDTO);
    }

    /**
     * Get orders by status with pagination
     * @param status Order status to filter
     * @param pageable Pagination parameters
     * @return Page of OrderResponseDTO
     */
    @Transactional(readOnly = true)
    public Page<OrderResponseDTO> getOrdersByStatus(OrderStatus status, Pageable pageable) {
        log.info("Admin: Fetching orders with status: {}", status);

        Page<Order> orders = orderRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return orders.map(this::mapToDTO);
    }

    /**
     * Update order status with optional tracking information
     * Handles status-specific logic and sends appropriate email notifications
     * 
     * @param orderNumber Order number
     * @param newStatus New order status
     * @param trackingNumber Tracking number (required for SHIPPED status)
     * @param carrier Shipping carrier (required for SHIPPED status)
     * @return Updated OrderResponseDTO
     */
    @Transactional
    public OrderResponseDTO updateOrderStatus(
            String orderNumber,
            OrderStatus newStatus,
            String trackingNumber,
            String carrier) {

        log.info("Admin: Updating order {} to status: {}", orderNumber, newStatus);

        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with number: " + orderNumber));

        OrderStatus oldStatus = order.getStatus();

        // Validate status transition
        validateStatusTransition(oldStatus, newStatus);

        order.setStatus(newStatus);

        // Handle status-specific updates
        switch (newStatus) {
            case PENDING:
                log.info("Order {} moved to pending", orderNumber);
                break;

            case CONFIRMED:
                log.info("Order {} confirmed by admin", orderNumber);
                try {
                    emailService.sendOrderConfirmationEmail(order);
                } catch (Exception e) {
                    log.error("Failed to send confirmation email for order: {}", orderNumber, e);
                }
                break;

            case PROCESSING:
                log.info("Order {} moved to processing", orderNumber);
                break;

            case SHIPPED:
                if (trackingNumber == null || trackingNumber.isBlank()) {
                    throw new BadRequestException("Tracking number is required for shipped status");
                }
                if (carrier == null || carrier.isBlank()) {
                    throw new BadRequestException("Carrier is required for shipped status");
                }
                order.setTrackingNumber(trackingNumber);
                order.setCarrier(carrier);
                log.info("Order {} shipped with tracking: {} via {}", orderNumber, trackingNumber, carrier);

                // Send shipping email
                try {
                    emailService.sendOrderShippedEmail(order);
                } catch (Exception e) {
                    log.error("Failed to send shipping email for order: {}", orderNumber, e);
                }
                break;

            case DELIVERED:
                order.setDeliveredAt(LocalDateTime.now());
                log.info("Order {} marked as delivered", orderNumber);

                // Send delivery email
                try {
                    emailService.sendOrderDeliveredEmail(order);
                } catch (Exception e) {
                    log.error("Failed to send delivery email for order: {}", orderNumber, e);
                }
                break;

            case CANCELLED:
                if (!oldStatus.canBeCancelled()) {
                    throw new BadRequestException(
                            "Order cannot be cancelled from status: " + oldStatus +
                            ". Only PENDING, CONFIRMED, or PROCESSING orders can be cancelled."
                    );
                }
                order.setCancelledAt(LocalDateTime.now());
                log.info("Order {} cancelled by admin from status: {}", orderNumber, oldStatus);

                // Send cancellation email
                try {
                    emailService.sendOrderCancellationEmail(order);
                } catch (Exception e) {
                    log.error("Failed to send cancellation email for order: {}", orderNumber, e);
                }
                break;

            case RETURN_REQUESTED:
                log.info("Order {} marked as return requested", orderNumber);
                break;

            case RETURNED:
                log.info("Order {} marked as returned", orderNumber);
                break;

            case REFUNDED:
                log.info("Order {} marked as refunded", orderNumber);
                break;
        }

        Order savedOrder = orderRepository.save(order);

        log.info("Order {} status updated from {} to {}", orderNumber, oldStatus, newStatus);

        return mapToDTO(savedOrder);
    }

    /**
     * Get comprehensive order statistics
     * @return Map containing various order metrics
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getOrderStatistics() {
        log.info("Admin: Generating order statistics");

        Map<String, Object> stats = new HashMap<>();

        // Total orders
        long totalOrders = orderRepository.count();
        stats.put("totalOrders", totalOrders);

        // Orders by status
        Map<String, Long> ordersByStatus = new HashMap<>();
        for (OrderStatus status : OrderStatus.values()) {
            long count = orderRepository.findByStatusOrderByCreatedAtDesc(
                    status, Pageable.unpaged()).getTotalElements();
            ordersByStatus.put(status.name(), count);
        }
        stats.put("ordersByStatus", ordersByStatus);

        // Total revenue (confirmed, processing, shipped, delivered orders)
        List<OrderStatus> paidStatuses = Arrays.asList(
                OrderStatus.CONFIRMED,
                OrderStatus.PROCESSING,
                OrderStatus.SHIPPED,
                OrderStatus.DELIVERED
        );
        Double totalRevenue = orderRepository.getTotalRevenue(paidStatuses);
        stats.put("totalRevenue", totalRevenue != null ? totalRevenue : 0.0);

        // Recent orders (last 7 days)
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<Order> recentOrders = orderRepository.findRecentOrdersByStatus(
                OrderStatus.CONFIRMED, sevenDaysAgo);
        stats.put("recentOrdersCount", recentOrders.size());

        // Pending orders count
        long pendingCount = orderRepository.findByStatusOrderByCreatedAtDesc(
                OrderStatus.PENDING, Pageable.unpaged()).getTotalElements();
        stats.put("pendingOrdersCount", pendingCount);

        // Processing orders count
        long processingCount = orderRepository.findByStatusOrderByCreatedAtDesc(
                OrderStatus.PROCESSING, Pageable.unpaged()).getTotalElements();
        stats.put("processingOrdersCount", processingCount);

        log.info("Admin: Order statistics generated - Total: {}, Revenue: {}",
                totalOrders, stats.get("totalRevenue"));

        return stats;
    }

    /**
     * Get orders requiring attention
     * Returns pending/processing orders older than 24 hours
     * @return List of orders requiring attention
     */
    @Transactional(readOnly = true)
    public List<OrderResponseDTO> getOrdersRequiringAttention() {
        log.info("Admin: Fetching orders requiring attention");

        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);

        // Get stale pending orders
        List<Order> pendingOrders = orderRepository.findStaleOrders(
                OrderStatus.PENDING, twentyFourHoursAgo);

        // Get stale processing orders
        List<Order> processingOrders = orderRepository.findStaleOrders(
                OrderStatus.PROCESSING, twentyFourHoursAgo);

        // Combine both lists
        pendingOrders.addAll(processingOrders);

        log.info("Admin: Found {} orders requiring attention", pendingOrders.size());

        return pendingOrders.stream()
                .map(this::mapToDTO)
                .toList();
    }

    /**
     * Validate status transition
     * @param oldStatus Current status
     * @param newStatus Target status
     */
    private void validateStatusTransition(OrderStatus oldStatus, OrderStatus newStatus) {
        // Prevent invalid transitions
        if (oldStatus == OrderStatus.DELIVERED && newStatus == OrderStatus.PROCESSING) {
            throw new BadRequestException("Cannot move delivered order back to processing");
        }

        if (oldStatus == OrderStatus.CANCELLED && newStatus != OrderStatus.REFUNDED) {
            throw new BadRequestException("Cancelled orders can only be moved to REFUNDED status");
        }

        if (oldStatus == OrderStatus.REFUNDED) {
            throw new BadRequestException("Cannot change status of refunded orders");
        }
    }

    /**
     * Map Order entity to DTO
     * Reuses existing mapping from OrderService
     * @param order Order entity
     * @return OrderResponseDTO
     */
    private OrderResponseDTO mapToDTO(Order order) {
        return orderService.getOrderDetails(order.getUser().getId(), order.getOrderNumber());
    }
}
