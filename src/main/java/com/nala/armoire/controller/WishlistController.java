package com.nala.armoire.controller;

import com.nala.armoire.annotation.CurrentUser;
import com.nala.armoire.model.dto.request.AddToWishlistRequest;
import com.nala.armoire.model.dto.response.WishlistResponse;
import com.nala.armoire.security.UserPrincipal;
import com.nala.armoire.service.WishlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wishlist Controller - Authenticated Users Only
 * 
 * Manages user wishlist operations including:
 * - Adding products to wishlist
 * - Removing products from wishlist
 * - Viewing wishlist items
 * - Checking if product is in wishlist
 * - Clearing entire wishlist
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    /**
     * GET /api/v1/wishlist
     * Get authenticated user's wishlist
     * 
     * @param currentUser - Authenticated user from JWT token
     * @return WishlistResponse with all wishlist items
     */
    @GetMapping
    public ResponseEntity<WishlistResponse> getWishlist(@CurrentUser UserPrincipal currentUser) {
        log.debug("GET /api/v1/wishlist - User: {}", currentUser.getId());
        WishlistResponse wishlist = wishlistService.getUserWishlist(currentUser.getId());
        return ResponseEntity.ok(wishlist);
    }

    /**
     * POST /api/v1/wishlist
     * Add product to wishlist
     * 
     * @param request - Product ID to add
     * @param currentUser - Authenticated user from JWT token
     * @return Updated wishlist
     */
    @PostMapping
    public ResponseEntity<WishlistResponse> addToWishlist(
            @Valid @RequestBody AddToWishlistRequest request,
            @CurrentUser UserPrincipal currentUser) {
        log.debug("POST /api/v1/wishlist - User: {}, Product: {}", 
                  currentUser.getId(), request.getProductId());
        
        WishlistResponse wishlist = wishlistService.addToWishlist(
                currentUser.getId(), 
                request.getProductId()
        );
        
        return ResponseEntity.status(HttpStatus.CREATED).body(wishlist);
    }

    /**
     * DELETE /api/v1/wishlist/{productId}
     * Remove product from wishlist
     * 
     * @param productId - Product ID to remove
     * @param currentUser - Authenticated user from JWT token
     * @return Updated wishlist
     */
    @DeleteMapping("/{productId}")
    public ResponseEntity<WishlistResponse> removeFromWishlist(
            @PathVariable UUID productId,
            @CurrentUser UserPrincipal currentUser) {
        log.debug("DELETE /api/v1/wishlist/{} - User: {}", productId, currentUser.getId());
        
        WishlistResponse wishlist = wishlistService.removeFromWishlist(
                currentUser.getId(), 
                productId
        );
        
        return ResponseEntity.ok(wishlist);
    }

    /**
     * GET /api/v1/wishlist/check/{productId}
     * Check if product is in user's wishlist
     * 
     * @param productId - Product ID to check
     * @param currentUser - Authenticated user from JWT token
     * @return Boolean indicating if product is in wishlist
     */
    @GetMapping("/check/{productId}")
    public ResponseEntity<Map<String, Boolean>> checkWishlist(
            @PathVariable UUID productId,
            @CurrentUser UserPrincipal currentUser) {
        log.debug("GET /api/v1/wishlist/check/{} - User: {}", productId, currentUser.getId());
        
        boolean isInWishlist = wishlistService.isInWishlist(currentUser.getId(), productId);
        
        Map<String, Boolean> response = new HashMap<>();
        response.put("inWishlist", isInWishlist);
        
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/v1/wishlist
     * Clear entire wishlist
     * 
     * @param currentUser - Authenticated user from JWT token
     * @return Success message
     */
    @DeleteMapping
    public ResponseEntity<Map<String, String>> clearWishlist(@CurrentUser UserPrincipal currentUser) {
        log.debug("DELETE /api/v1/wishlist - User: {}", currentUser.getId());
        
        wishlistService.clearWishlist(currentUser.getId());
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "Wishlist cleared successfully");
        
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/wishlist/count
     * Get wishlist item count
     * 
     * @param currentUser - Authenticated user from JWT token
     * @return Count of items in wishlist
     */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> getWishlistCount(@CurrentUser UserPrincipal currentUser) {
        log.debug("GET /api/v1/wishlist/count - User: {}", currentUser.getId());
        
        long count = wishlistService.getWishlistCount(currentUser.getId());
        
        Map<String, Long> response = new HashMap<>();
        response.put("count", count);
        
        return ResponseEntity.ok(response);
    }
}
