package com.nala.armoire.controller;

import com.nala.armoire.model.dto.response.OrderResponseDTO;
import com.nala.armoire.model.dto.response.PagedResponse;
import com.nala.armoire.model.entity.OrderStatus;
import com.nala.armoire.service.AdminOrderService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin Order Management Controller
 * Requires ADMIN role for all operations
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    /**
     * GET /api/v1/admin/orders
     * Get all orders with pagination and sorting
     * 
     * @param page Page number (default: 0)
     * @param size Page size (default: 20)
     * @param sortBy Sort field (default: createdAt)
     * @param sortDir Sort direction (default: DESC)
     * @return Paged response of orders
     */
    @GetMapping
    public ResponseEntity<PagedResponse<OrderResponseDTO>> getAllOrders(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        log.info("Admin: Fetching all orders - page: {}, size: {}, sortBy: {}, sortDir: {}",
                page, size, sortBy, sortDir);

        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDir)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        Page<OrderResponseDTO> orders = adminOrderService.getAllOrders(pageable);

        PagedResponse<OrderResponseDTO> response = PagedResponse.<OrderResponseDTO>builder()
                .content(orders.getContent())
                .page(orders.getNumber())
                .size(orders.getSize())
                .totalElements(orders.getTotalElements())
                .totalPages(orders.getTotalPages())
                .first(orders.isFirst())
                .last(orders.isLast())
                .hasNext(orders.hasNext())
                .hasPrevious(orders.hasPrevious())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/admin/orders/status/{status}
     * Get orders filtered by status
     * 
     * @param status Order status to filter
     * @param page Page number (default: 0)
     * @param size Page size (default: 20)
     * @return Paged response of orders with the specified status
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<PagedResponse<OrderResponseDTO>> getOrdersByStatus(
            @PathVariable @NotNull OrderStatus status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        log.info("Admin: Fetching orders with status: {} - page: {}, size: {}", status, page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OrderResponseDTO> orders = adminOrderService.getOrdersByStatus(status, pageable);

        PagedResponse<OrderResponseDTO> response = PagedResponse.<OrderResponseDTO>builder()
                .content(orders.getContent())
                .page(orders.getNumber())
                .size(orders.getSize())
                .totalElements(orders.getTotalElements())
                .totalPages(orders.getTotalPages())
                .first(orders.isFirst())
                .last(orders.isLast())
                .hasNext(orders.hasNext())
                .hasPrevious(orders.hasPrevious())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * PUT /api/v1/admin/orders/{orderNumber}/status
     * Update order status with optional tracking information
     * 
     * @param orderNumber Order number
     * @param status New order status
     * @param trackingNumber Tracking number (required for SHIPPED status)
     * @param carrier Shipping carrier (required for SHIPPED status)
     * @return Updated order
     */
    @PutMapping("/{orderNumber}/status")
    public ResponseEntity<OrderResponseDTO> updateOrderStatus(
            @PathVariable @NotNull String orderNumber,
            @RequestParam @NotNull OrderStatus status,
            @RequestParam(required = false) String trackingNumber,
            @RequestParam(required = false) String carrier) {

        log.info("Admin: Updating order {} to status: {} (tracking: {}, carrier: {})",
                orderNumber, status, trackingNumber, carrier);

        OrderResponseDTO order = adminOrderService.updateOrderStatus(
                orderNumber, status, trackingNumber, carrier);

        return ResponseEntity.ok(order);
    }

    /**
     * GET /api/v1/admin/orders/statistics
     * Get comprehensive order statistics
     * 
     * @return Order statistics including counts by status, revenue, etc.
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getOrderStatistics() {
        log.info("Admin: Fetching order statistics");

        Map<String, Object> stats = adminOrderService.getOrderStatistics();

        return ResponseEntity.ok(stats);
    }

    /**
     * GET /api/v1/admin/orders/attention-required
     * Get orders requiring attention (stale pending/processing orders)
     * Returns orders that have been in PENDING or PROCESSING status for more than 24 hours
     * 
     * @return List of orders requiring attention
     */
    @GetMapping("/attention-required")
    public ResponseEntity<List<OrderResponseDTO>> getOrdersRequiringAttention() {
        log.info("Admin: Fetching orders requiring attention");

        List<OrderResponseDTO> orders = adminOrderService.getOrdersRequiringAttention();

        log.info("Admin: Found {} orders requiring attention", orders.size());

        return ResponseEntity.ok(orders);
    }
}
