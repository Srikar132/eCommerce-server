package com.nala.armoire.controller;


import com.nala.armoire.model.dto.request.AddToCartRequest;
import com.nala.armoire.model.dto.request.UpdateCartItemRequest;
import com.nala.armoire.model.dto.response.ApiResponse;
import com.nala.armoire.model.dto.response.CartResponse;
import com.nala.armoire.model.dto.response.CartSummaryResponse;
import com.nala.armoire.model.entity.Cart;
import com.nala.armoire.model.entity.User;
import com.nala.armoire.service.CartService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart(@AuthenticationPrincipal User user, HttpSession session) {
        CartResponse cart = user != null ? cartService.getCart(user) : cartService.getCart(getSessionId(session));

        return ResponseEntity.ok(ApiResponse.success(cart, "Cart retrieved successfully"));
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartResponse>> addItemToCart(@Valid @RequestBody AddToCartRequest request, @AuthenticationPrincipal User user, HttpSession session) {

        CartResponse cart = user != null ? cartService.addItemToCart(user, request) : cartService.addItemToCart(getSessionId(session), request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(cart, "Item added to the cart successfully"));
    }

    @PutMapping("/items/{id}")
    public ResponseEntity<ApiResponse<CartResponse>> updateCartItem(@PathVariable UUID id, @Valid @RequestBody UpdateCartItemRequest request, @AuthenticationPrincipal User user, HttpSession session) {

        CartResponse cart = user != null ? cartService.updateCartItem(user, id, request) : cartService.updateCartItem(getSessionId(session), id, request);

        return ResponseEntity.ok(ApiResponse.success(cart, "Cart item updated successfully"));
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<ApiResponse<CartResponse>> deleteCartItem(@PathVariable UUID id, @AuthenticationPrincipal User user, HttpSession session) {

        CartResponse cart = user != null ? cartService.removeCartItem(user, id) : cartService.removeCartItem(getSessionId(session), id);

        return ResponseEntity.ok(ApiResponse.success(cart, "Item removed from cart successfully"));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<CartResponse>> clearCart(@AuthenticationPrincipal User user, HttpSession session) {
        CartResponse cart = user != null ? cartService.clearCart(user) : cartService.clearCart(getSessionId(session));

        return ResponseEntity.ok(ApiResponse.success(cart, "Cart cleared successfully"));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<CartSummaryResponse>> getCartSummary(
            @AuthenticationPrincipal User user,
            HttpSession session) {

        CartSummaryResponse summary = user != null
                ? cartService.getCartSummary(user)
                : cartService.getCartSummary(getSessionId(session));

        return ResponseEntity.ok(ApiResponse.success(summary, "Cart summary retrieved successfully"));
    }

    @PostMapping("/merge")
    public ResponseEntity<ApiResponse<CartResponse>> mergeGuestCard(@AuthenticationPrincipal User user, HttpSession session) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("User must be authenticated to merge the cart", "401"));
        }

        CartResponse cart = cartService.mergeGuestCartToUser(getSessionId(session), user);
        return ResponseEntity.ok(ApiResponse.success(cart, "Guest cart merged successfully"));
    }

    private String getSessionId(HttpSession session) {
        String sessionId = (String) session.getAttribute("cartSessionId");
        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString();
            session.setAttribute("cartSessionId", sessionId);
        }
        return sessionId;
    }
}
