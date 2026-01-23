package com.nala.armoire.service;

import com.nala.armoire.exception.BadRequestException;
import com.nala.armoire.exception.ResourceNotFoundException;
import com.nala.armoire.model.dto.response.WishlistItemResponse;
import com.nala.armoire.model.dto.response.WishlistResponse;
import com.nala.armoire.model.entity.Product;
import com.nala.armoire.model.entity.ProductVariant;
import com.nala.armoire.model.entity.User;
import com.nala.armoire.model.entity.WishList;
import com.nala.armoire.repository.ProductRepository;
import com.nala.armoire.repository.UserRepository;
import com.nala.armoire.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    /**
     * Get user's wishlist
     */
    @Transactional(readOnly = true)
    public WishlistResponse getUserWishlist(UUID userId) {
        log.debug("Fetching wishlist for user: {}", userId);

        List<WishList> wishlistItems = wishlistRepository.findByUserIdWithProduct(userId);

        List<WishlistItemResponse> items = wishlistItems.stream()
                .map(this::mapToWishlistItemResponse)
                .collect(Collectors.toList());

        return WishlistResponse.builder()
                .items(items)
                .totalItems(items.size())
                .build();
    }

    /**
     * Add product to wishlist
     */
    @Transactional
    public WishlistResponse addToWishlist(UUID userId, UUID productId) {
        log.debug("Adding product {} to wishlist for user {}", productId, userId);

        // Validate user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // Validate product exists and is active
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        if (!product.getIsActive()) {
            throw new BadRequestException("Cannot add inactive product to wishlist");
        }

        // Check if already in wishlist
        if (wishlistRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new BadRequestException("Product is already in your wishlist");
        }

        // Create wishlist item
        WishList wishlistItem = WishList.builder()
                .user(user)
                .product(product)
                .build();

        wishlistRepository.save(wishlistItem);

        log.info("Product {} added to wishlist for user {}", productId, userId);

        // Return updated wishlist
        return getUserWishlist(userId);
    }

    /**
     * Remove product from wishlist
     */
    @Transactional
    public WishlistResponse removeFromWishlist(UUID userId, UUID productId) {
        log.debug("Removing product {} from wishlist for user {}", productId, userId);

        // Verify the wishlist item exists
        WishList wishlistItem = wishlistRepository.findByUserIdAndProductId(userId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Wishlist item not found"));

        wishlistRepository.delete(wishlistItem);

        log.info("Product {} removed from wishlist for user {}", productId, userId);

        // Return updated wishlist
        return getUserWishlist(userId);
    }

    /**
     * Check if product is in user's wishlist
     */
    @Transactional(readOnly = true)
    public boolean isInWishlist(UUID userId, UUID productId) {
        return wishlistRepository.existsByUserIdAndProductId(userId, productId);
    }

    /**
     * Clear user's entire wishlist
     */
    @Transactional
    public void clearWishlist(UUID userId) {
        log.debug("Clearing wishlist for user {}", userId);
        wishlistRepository.deleteByUserId(userId);
        log.info("Wishlist cleared for user {}", userId);
    }

    /**
     * Get wishlist item count for user
     */
    @Transactional(readOnly = true)
    public long getWishlistCount(UUID userId) {
        return wishlistRepository.countByUserId(userId);
    }

    /**
     * Map WishList entity to WishlistItemResponse DTO
     */
    private WishlistItemResponse mapToWishlistItemResponse(WishList wishlistItem) {
        Product product = wishlistItem.getProduct();

        // Get primary image URL from first active variant
        String primaryImageUrl = product.getVariants().stream()
                .filter(ProductVariant::getIsActive)
                .flatMap(variant -> variant.getImages().stream())
                .filter(image -> image.getIsPrimary() != null && image.getIsPrimary())
                .findFirst()
                .map(image -> image.getImageUrl())
                .orElse(null);

        // Check if any variant is in stock
        boolean inStock = product.getVariants().stream()
                .anyMatch(variant -> variant.getIsActive() && 
                                   variant.getStockQuantity() != null && 
                                   variant.getStockQuantity() > 0);

        return WishlistItemResponse.builder()
                .id(wishlistItem.getId())
                .productId(product.getId())
                .productName(product.getName())
                .productSlug(product.getSlug())
                .basePrice(product.getBasePrice())
                .sku(product.getSku())
                .isActive(product.getIsActive())
                .isCustomizable(product.getIsCustomizable())
                .primaryImageUrl(primaryImageUrl)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .brandName(product.getBrand() != null ? product.getBrand().getName() : null)
                .addedAt(wishlistItem.getCreatedAt())
                .inStock(inStock)
                .build();
    }
}
