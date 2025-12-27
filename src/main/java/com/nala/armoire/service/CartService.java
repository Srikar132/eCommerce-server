package com.nala.armoire.service;

import com.nala.armoire.exception.ResourceNotFoundException;
import com.nala.armoire.mapper.CartMapper;
import com.nala.armoire.model.dto.request.AddToCartRequest;
import com.nala.armoire.model.dto.request.UpdateCartItemRequest;
import com.nala.armoire.model.dto.response.CartResponse;
import com.nala.armoire.model.dto.response.CartSummaryResponse;
import com.nala.armoire.model.entity.*;
import com.nala.armoire.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CartMapper cartMapper;
    private final CustomizationRepository customizationRepository;

    // Get or create cart for authenticated user
    public CartResponse getCart(User user) {
        Cart cart = getOrCreateCartForUser(user);
        return cartMapper.toCartResponse(cart);
    }

    // Get or create cart for guest user
    public CartResponse getCart(String sessionId) {
        Cart cart = getOrCreateCartForSession(sessionId);
        return cartMapper.toCartResponse(cart);
    }

    // Add item to cart (authenticated user)
    public CartResponse addItemToCart(User user, AddToCartRequest request) {
        Cart cart = getOrCreateCartForUser(user);
        System.out.println("this method is being called");
        return addItemToCartInternal(cart, request);
    }

    // Add item to cart (guest user)
    public CartResponse addItemToCart(String sessionId, AddToCartRequest request) {
        Cart cart = getOrCreateCartForSession(sessionId);
        System.out.println("this method is being called");
        return addItemToCartInternal(cart, request);
    }

    // Update cart item (authenticated user)
    public CartResponse updateCartItem(User user, UUID itemId, UpdateCartItemRequest request) {
        Cart cart = getOrCreateCartForUser(user);
        return updateCartItemInternal(cart, itemId, request);
    }

    // Update cart item (guest user)
    public CartResponse updateCartItem(String sessionId, UUID itemId, UpdateCartItemRequest request) {
        Cart cart = getOrCreateCartForSession(sessionId);
        return updateCartItemInternal(cart, itemId, request);
    }

    // Remove cart item (authenticated user)
    public CartResponse removeCartItem(User user, UUID itemId) {
        Cart cart = getOrCreateCartForUser(user);
        return removeCartItemInternal(cart, itemId);
    }

    // Remove cart item (guest user)
    public CartResponse removeCartItem(String sessionId, UUID itemId) {
        Cart cart = getOrCreateCartForSession(sessionId);
        return removeCartItemInternal(cart, itemId);
    }

    // Clear cart (authenticated user)
    public CartResponse clearCart(User user) {
        Cart cart = getOrCreateCartForUser(user);
        cart.clearItems();
        cartRepository.save(cart);
        return cartMapper.toCartResponse(cart);
    }

    // Clear cart (guest user)
    public CartResponse clearCart(String sessionId) {
        Cart cart = getOrCreateCartForSession(sessionId);
        cart.clearItems();
        cartRepository.save(cart);
        return cartMapper.toCartResponse(cart);
    }

    // Get cart summary (authenticated user)
    public CartSummaryResponse getCartSummary(User user) {
        Cart cart = getOrCreateCartForUser(user);
        return cartMapper.toCartSummaryResponse(cart);
    }

    // Get cart summary (guest user)
    public CartSummaryResponse getCartSummary(String sessionId) {
        Cart cart = getOrCreateCartForSession(sessionId);
        return cartMapper.toCartSummaryResponse(cart);
    }

    // Merge guest cart to authenticated user cart
    public CartResponse mergeGuestCartToUser(String sessionId, User user) {
        Cart guestCart = cartRepository.findBySessionIdWithItems(sessionId).orElse(null);

        if (guestCart == null || guestCart.getItems().isEmpty()) {
            return getCart(user);
        }

        Cart userCart = getOrCreateCartForUser(user);

        // Transfer items from guest cart to user cart
        for (CartItem guestItem : guestCart.getItems()) {
            // Check if same item already exists in user cart
            boolean itemExists = userCart.getItems().stream()
                    .anyMatch(item -> isSameItem(item, guestItem));

            if (!itemExists) {
                CartItem newItem = CartItem.builder()
                        .product(guestItem.getProduct())
                        .productVariant(guestItem.getProductVariant())
                        .customization(guestItem.getCustomization())
                        .quantity(guestItem.getQuantity())
                        .unitPrice(guestItem.getUnitPrice())
                        .customizationPrice(guestItem.getCustomizationPrice())
                        .customizationSummary(guestItem.getCustomizationSummary())
                        .build();

                // Calculate item total before adding to cart
                newItem.calculateItemTotal();
                userCart.addItem(newItem);
            } else {
                // Update quantity if item exists
                userCart.getItems().stream()
                        .filter(item -> isSameItem(item, guestItem))
                        .findFirst()
                        .ifPresent(existingItem -> {
                            existingItem.setQuantity(existingItem.getQuantity() + guestItem.getQuantity());
                            existingItem.calculateItemTotal();
                        });
            }
        }

        // Deactivate guest cart
        guestCart.setIsActive(false);
        cartRepository.save(guestCart);

        // Recalculate user cart totals and save
        userCart.recalculateTotals();
        cartRepository.save(userCart);

        log.info("Merged guest cart to user cart: sessionId={}, userId={}", sessionId, user.getId());
        return cartMapper.toCartResponse(userCart);
    }

    // ==================== HELPER METHODS ====================

    private Cart getOrCreateCartForUser(User user) {
        return cartRepository.findByUserWithItems(user)
                .orElseGet(() -> {
                    Cart cart = Cart.builder()
                            .user(user)
                            .isActive(true)
                            .expiresAt(LocalDateTime.now().plusDays(30))
                            .build();
                    return cartRepository.save(cart);
                });
    }

    private Cart getOrCreateCartForSession(String sessionId) {
        System.out.println("this method is being called --> ");
        return cartRepository.findBySessionIdWithItems(sessionId)
                .orElseGet(() -> {
                    Cart cart = Cart.builder()
                            .sessionId(sessionId)
                            .isActive(true)
                            .expiresAt(LocalDateTime.now().plusDays(7))
                            .build();
                    return cartRepository.save(cart);
                });
    }

    private CartResponse addItemToCartInternal(Cart cart, AddToCartRequest request) {
        // Validate and fetch product
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + request.getProductId()));

        // Fetch variant if provided
        ProductVariant variant = null;
        if (request.getProductVariantId() != null) {
            variant = productVariantRepository.findById(request.getProductVariantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product variant not found with ID: " + request.getProductVariantId()));

            // Validate variant belongs to product
            if (!variant.getProduct().getId().equals(product.getId())) {
                throw new IllegalArgumentException("Product variant does not belong to the specified product");
            }
        }

        // Fetch customization if provided
        Customization customization = null;
        BigDecimal customizationPrice = BigDecimal.ZERO;
        if (request.getCustomizationId() != null) {
            customization = customizationRepository.findById(request.getCustomizationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customization not found with ID: " + request.getCustomizationId()));
            customizationPrice = calculateCustomizationPrice(customization);
        }

        // Check if same item (without customization) already exists in cart
        if (customization == null) {
            CartItem existingItem = cartItemRepository
                    .findByCartAndProductAndProductVariantAndCustomizationIsNull(cart, product, variant)
                    .orElse(null);

            if (existingItem != null) {
                // Update quantity of existing item
                existingItem.setQuantity(existingItem.getQuantity() + request.getQuantity());
                existingItem.calculateItemTotal();
                cart.recalculateTotals();
                cartRepository.save(cart);

                log.info("Updated existing cart item: itemId={}, newQuantity={}", existingItem.getId(), existingItem.getQuantity());
                return cartMapper.toCartResponse(cart);
            }
        }

        // Calculate unit price (base price + variant price if applicable)
        BigDecimal unitPrice = product.getBasePrice() != null ? product.getBasePrice() : BigDecimal.ZERO;
        if (variant != null && variant.getAdditionalPrice() != null) {
            unitPrice = unitPrice.add(variant.getAdditionalPrice());
        }

        // Create new cart item
        CartItem cartItem = CartItem.builder()
                .product(product)
                .productVariant(variant)
                .customization(customization)
                .quantity(request.getQuantity())
                .unitPrice(unitPrice)
                .customizationPrice(customizationPrice)
                .customizationSummary(request.getCustomizationSummary())
                .build();

        // CRITICAL: Calculate item total before adding to cart
        cartItem.calculateItemTotal();

        // Add item to cart and save
        cart.addItem(cartItem);
        cartRepository.save(cart);

        log.info("Added new item to cart: productId={}, variantId={}, quantity={}, unitPrice={}, itemTotal={}",
                product.getId(),
                variant != null ? variant.getId() : "none",
                request.getQuantity(),
                unitPrice,
                cartItem.getItemTotal());

        return cartMapper.toCartResponse(cart);
    }

    private CartResponse updateCartItemInternal(Cart cart, UUID itemId, UpdateCartItemRequest request) {
        CartItem cartItem = cart.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found with ID: " + itemId));

        // Validate quantity
        if (request.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }

        cartItem.setQuantity(request.getQuantity());
        cartItem.calculateItemTotal();
        cart.recalculateTotals();
        cartRepository.save(cart);

        log.info("Updated cart item: itemId={}, newQuantity={}, newItemTotal={}",
                itemId, request.getQuantity(), cartItem.getItemTotal());

        return cartMapper.toCartResponse(cart);
    }

    private CartResponse removeCartItemInternal(Cart cart, UUID itemId) {
        CartItem cartItem = cart.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found with ID: " + itemId));

        cart.removeItem(cartItem);
        cartRepository.save(cart);

        log.info("Removed item from cart: itemId={}", itemId);
        return cartMapper.toCartResponse(cart);
    }

    private BigDecimal calculateCustomizationPrice(Customization customization) {
        BigDecimal price = BigDecimal.ZERO;

        if (Boolean.TRUE.equals(customization.getHasText())) {
            price = price.add(BigDecimal.valueOf(5.00));
        }
        if (Boolean.TRUE.equals(customization.getHasDesign())) {
            price = price.add(BigDecimal.valueOf(10.00));
        }
        if (Boolean.TRUE.equals(customization.getHasUploadedImage())) {
            price = price.add(BigDecimal.valueOf(15.00));
        }

        return price;
    }

    private boolean isSameItem(CartItem item1, CartItem item2) {
        boolean sameProduct = item1.getProduct().getId().equals(item2.getProduct().getId());

        boolean sameVariant = (item1.getProductVariant() == null && item2.getProductVariant() == null) ||
                (item1.getProductVariant() != null && item2.getProductVariant() != null &&
                        item1.getProductVariant().getId().equals(item2.getProductVariant().getId()));

        boolean sameCustomization = (item1.getCustomization() == null && item2.getCustomization() == null) ||
                (item1.getCustomization() != null && item2.getCustomization() != null &&
                        item1.getCustomization().getId().equals(item2.getCustomization().getId()));

        return sameProduct && sameVariant && sameCustomization;
    }
}