package com.nala.armoire.controller;

import com.nala.armoire.annotation.CurrentUser;
import com.nala.armoire.model.dto.request.CheckoutRequest;
import com.nala.armoire.model.dto.request.PaymentVerificationRequest;
import com.nala.armoire.model.dto.response.CheckoutResponse;
import com.nala.armoire.model.dto.response.OrderResponseDTO;
import com.nala.armoire.model.dto.response.PagedResponse;
import com.nala.armoire.security.UserPrincipal;
import com.nala.armoire.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Order Controller
 * Handles order creation, payment verification, and order management
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * POST /api/v1/orders/checkout
     * Create order from cart and initiate payment
     * Returns Razorpay payment details
     */
    @PostMapping("/checkout")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CheckoutResponse> checkout(
            @CurrentUser UserPrincipal currentUser,
            @Valid @RequestBody CheckoutRequest request) {

        log.info("Checkout initiated by user: {}", currentUser.getId());

        CheckoutResponse response = orderService.createOrder(currentUser.getId(), request);

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/orders/verify-payment
     * Verify Razorpay payment and confirm order
     */
    @PostMapping("/verify-payment")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponseDTO> verifyPayment(
            @CurrentUser UserPrincipal currentUser,
            @Valid @RequestBody PaymentVerificationRequest request) {

        log.info("Payment verification for order: {}", request.getRazorpayOrderId());

        OrderResponseDTO order = orderService.verifyPaymentAndConfirmOrder(
                currentUser.getId(), request);

        return ResponseEntity.ok(order);
    }

    /**
     * GET /api/v1/orders
     * Get user's order history
     */
    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<PagedResponse<OrderResponseDTO>> getMyOrders(
            @CurrentUser UserPrincipal currentUser,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {

        log.info("Fetching orders for user: {}", currentUser.getId());

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OrderResponseDTO> orders = orderService.getUserOrders(currentUser.getId(), pageable);

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
     * GET /api/v1/orders/{orderNumber}
     * Get single order details
     */
    @GetMapping("/{orderNumber}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponseDTO> getOrderDetails(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable String orderNumber) {

        log.info("Fetching order details: {} for user: {}", orderNumber, currentUser.getId());

        OrderResponseDTO order = orderService.getOrderDetails(currentUser.getId(), orderNumber);

        return ResponseEntity.ok(order);
    }

    /**
     * POST /api/v1/orders/{orderNumber}/cancel
     * Cancel an order
     */
    @PostMapping("/{orderNumber}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponseDTO> cancelOrder(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable String orderNumber,
            @RequestParam String reason) {

        log.info("Cancelling order: {} by user: {}", orderNumber, currentUser.getId());

        OrderResponseDTO order = orderService.cancelOrder(
                currentUser.getId(), orderNumber, reason);

        return ResponseEntity.ok(order);
    }

    /**
     * POST /api/v1/orders/{orderNumber}/return
     * Request order return
     */
    @PostMapping("/{orderNumber}/return")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponseDTO> requestReturn(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable String orderNumber,
            @RequestParam String reason) {

        log.info("Return requested for order: {} by user: {}", orderNumber, currentUser.getId());

        OrderResponseDTO order = orderService.requestReturn(
                currentUser.getId(), orderNumber, reason);

        return ResponseEntity.ok(order);
    }
}