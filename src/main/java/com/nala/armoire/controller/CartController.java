package com.nala.armoire.controller;


import com.nala.armoire.model.dto.request.AddToCartRequest;
import com.nala.armoire.model.dto.request.SyncLocalCartRequest;
import com.nala.armoire.model.dto.request.UpdateCartItemRequest;
import com.nala.armoire.model.dto.response.CartResponse;
import com.nala.armoire.model.dto.response.CartSummaryResponse;
import com.nala.armoire.security.UserPrincipal;
import com.nala.armoire.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Cart Controller - Authenticated Users Only
 * 
 * Architecture:
 * - Guest users: Cart managed locally in frontend (localStorage)
 * - Authenticated users: Cart persisted in database
 * - On login/checkout: Sync local cart to backend using POST /api/v1/cart/sync
 * 
 * This approach:
 * ✅ No session management complexity
 * ✅ No race conditions in cart creation
 * ✅ Better performance (no DB writes for guests)
 * ✅ Cleaner separation of concerns
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /**
     * Get authenticated user's cart
     * Returns 401 if not authenticated
     */
    @GetMapping
    public ResponseEntity<CartResponse> getCart(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        log.info("GET /cart - userId={}", userPrincipal.getId());
        CartResponse cart = cartService.getCartByUserId(userPrincipal.getId());
        log.info("Cart retrieved: totalItems={}, total={}", cart.getTotalItems(), cart.getTotal());
        
        return ResponseEntity.ok(cart);
    }

    /**
     * Add item to authenticated user's cart
     * Returns 401 if not authenticated
     */
    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItemToCart(
            @Valid @RequestBody AddToCartRequest request, 
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        if (userPrincipal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        log.info("POST /cart/items - userId={}, productId={}, variantId={}, customizationId={}, quantity={}", 
                userPrincipal.getId(),
                request.getProductId(),
                request.getProductVariantId(),
                request.getCustomizationId(),
                request.getQuantity());

        CartResponse cart = cartService.addItemToCartByUserId(userPrincipal.getId(), request);
        log.info("Item added to cart: totalItems={}, total={}", cart.getTotalItems(), cart.getTotal());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(cart);
    }

    /**
     * Update cart item quantity for authenticated user
     * Returns 401 if not authenticated
     */
    @PutMapping("/items/{id}")
    public ResponseEntity<CartResponse> updateCartItem(
            @PathVariable UUID id, 
            @Valid @RequestBody UpdateCartItemRequest request, 
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        if (userPrincipal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        log.info("PUT /cart/items/{} - userId={}, newQuantity={}", 
                id, userPrincipal.getId(), request.getQuantity());

        CartResponse cart = cartService.updateCartItemByUserId(userPrincipal.getId(), id, request);
        log.info("Cart item updated: totalItems={}, total={}", cart.getTotalItems(), cart.getTotal());
        
        return ResponseEntity.ok(cart);
    }

    /**
     * Remove item from authenticated user's cart
     * Returns 401 if not authenticated
     */
    @DeleteMapping("/items/{id}")
    public ResponseEntity<CartResponse> deleteCartItem(
            @PathVariable UUID id, 
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        if (userPrincipal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        log.info("DELETE /cart/items/{} - userId={}", id, userPrincipal.getId());
        CartResponse cart = cartService.removeCartItemByUserId(userPrincipal.getId(), id);
        log.info("Cart item deleted: totalItems={}, total={}", cart.getTotalItems(), cart.getTotal());
        
        return ResponseEntity.ok(cart);
    }

    /**
     * Clear authenticated user's cart
     * Returns 401 if not authenticated
     */
    @DeleteMapping
    public ResponseEntity<CartResponse> clearCart(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        log.info("DELETE /cart - userId={}", userPrincipal.getId());
        CartResponse cart = cartService.clearCartByUserId(userPrincipal.getId());
        log.info("Cart cleared successfully");
        
        return ResponseEntity.ok(cart);
    }

    /**
     * Get cart summary for authenticated user
     * Returns 401 if not authenticated
     */
    @GetMapping("/summary")
    public ResponseEntity<CartSummaryResponse> getCartSummary(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        CartSummaryResponse summary = cartService.getCartSummaryByUserId(userPrincipal.getId());
        return ResponseEntity.ok(summary);
    }

    /**
     * Sync local cart to backend on login/checkout
     * 
     * This endpoint:
     * 1. Receives local cart items from frontend
     * 2. Saves any unsaved customizations to DB
     * 3. Adds all items to user's cart
     * 4. Returns merged cart
     * 
     * Called when:
     * - Guest user logs in
     * - Guest user proceeds to checkout (triggers login)
     */
    @PostMapping("/sync")
    public ResponseEntity<CartResponse> syncLocalCart(
            @Valid @RequestBody SyncLocalCartRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        if (userPrincipal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        log.info("POST /cart/sync - userId={}, itemCount={}", 
                userPrincipal.getId(), request.getItems().size());
        
        CartResponse cart = cartService.syncLocalCartByUserId(userPrincipal.getId(), request);
        log.info("Local cart synced: totalItems={}, total={}", cart.getTotalItems(), cart.getTotal());
        
        return ResponseEntity.ok(cart);
    }
}
