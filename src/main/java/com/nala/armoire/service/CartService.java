package com.nala.armoire.service;

import com.nala.armoire.exception.ResourceNotFoundException;
import com.nala.armoire.mapper.CartMapper;
import com.nala.armoire.model.dto.request.AddToCartRequest;
import com.nala.armoire.model.dto.request.UpdateCartItemRequest;
import com.nala.armoire.model.dto.response.CartResponse;
import com.nala.armoire.model.dto.response.CartSummaryResponse;
import com.nala.armoire.model.dto.response.CustomizationSummary;
import com.nala.armoire.model.entity.*;
import com.nala.armoire.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.sql.Delete;
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
    private final CustomizationSummary customizationSummary;
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

    // add item to the cart(Authenticated user)
    public CartResponse addItemToCart(User user, AddToCartRequest request) {
        Cart cart = getOrCreateCartForUser(user);
        return addItemToCartInternal(cart, request);
    }

    // Add item to cart (guest user)
    public CartResponse addItemToCart(String sessionId, AddToCartRequest request) {
        Cart cart = getOrCreateCartForSession(sessionId);
        return addItemToCartInternal(cart, request);
    }

    // update cart item(authenticated user)
    public CartResponse updateCartItem(User user, UUID itemId, UpdateCartItemRequest request) {
        Cart cart = getOrCreateCartForUser(user);
        return updateCartItemInternal(cart, itemId, request);
    }

    // update cart item(guest user)
    public CartResponse updateCartItem(String sessionId, UUID itemId, UpdateCartItemRequest request) {
        Cart cart = getOrCreateCartForSession(sessionId);
        return updateCartItemInternal(cart, itemId, request);
    }

    //remove cart item (authenticated user)
    public CartResponse removeCartItem(User user, UUID itemId) {
        Cart cart = getOrCreateCartForUser(user);
        return removeCartItemInternal(cart, itemId);
    }

    // remove cart item (Guest user)
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

    public CartResponse mergeGuestCartToUser(String sessionId, User user) {
        Cart guestCart = cartRepository.findBySessionIdWithItems(sessionId).orElse(null);

        if(guestCart == null || guestCart.getItems().isEmpty()) {
            return getCart(user);
        }

        Cart userCart = getOrCreateCartForUser(user);

        // Transfer items from guest cart to user cart
        for (CartItem guestItem : guestCart.getItems()) {
            CartItem newItem = CartItem.builder()
                    .product(guestItem.getProduct())
                    .productVariant(guestItem.getProductVariant())
                    .customization(guestItem.getCustomization())
                    .quantity(guestItem.getQuantity())
                    .unitPrice(guestItem.getUnitPrice())
                    .customizationPrice(guestItem.getCustomizationPrice())
                    .customizationSummary(guestItem.getCustomizationSummary())
                    .build();
            userCart.addItem(newItem);
        }

        // Delete guest cart
        guestCart.setIsActive(false);
        cartRepository.save(guestCart);

        cartRepository.save(userCart);
        return cartMapper.toCartResponse(userCart);
    }

    //helper methods
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
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        ProductVariant variant = null;
        if(request.getProductVariantId() != null) {
            variant = productVariantRepository.findById(request.getProductVariantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product variant not found"));
        }

        Customization customization = null;
        BigDecimal customizationPrice = BigDecimal.ZERO;

        if(request.getCustomizationId() != null) {
            customization = customizationRepository.findById(request.getCustomizationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customization not found"));

            //calculating customization price based on your business logic
            customizationPrice = calculateCustomizationPrice(customization);

        }

        //check if same item without customization already exists
        if(customization == null) {
            CartItem existingItem = cartItemRepository
                    .findByCartAndProductAndProductVariantAndCustomizationIsNull(cart, product, variant)
                    .orElse(null);

            if(existingItem != null) {
                existingItem.setQuantity(existingItem.getQuantity() + request.getQuantity());
                existingItem.calculateItemTotal();
                cart.recalculateTotals();
                cartRepository.save(cart);

                return cartMapper.toCartResponse(cart);
            }
        }

        // Get price from variant or product
        BigDecimal unitPrice = product.getBasePrice();

        if (variant != null) {
            BigDecimal additionalPrice = variant.getAdditionalPrice() != null
                    ? variant.getAdditionalPrice()
                    : BigDecimal.ZERO;

            unitPrice = unitPrice.add(additionalPrice);
        }

        // Create new CartItem
        CartItem cartItem = CartItem.builder()
                .product(product)
                .productVariant(variant)
                .customization(customization)
                .quantity(request.getQuantity())
                .unitPrice(unitPrice)
                .customizationPrice(customizationPrice)
                .customizationSummary(request.getCustomizationSummary())
                .build();

        //  Add item to cart & save
        cart.addItem(cartItem);
        cartRepository.save(cart);

        log.info("Added item to cart: productId={}, quantity={}", product.getId(), request.getQuantity());

        // Return updated cart
        return cartMapper.toCartResponse(cart);
    }

    private BigDecimal calculateCustomizationPrice(Customization customization) {
        // TODO: Implement actual customization pricing logic
        // This is a placeholder implementation
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

    private CartResponse updateCartItemInternal(Cart cart, UUID itemId, UpdateCartItemRequest request) {
        CartItem cartItem = cart.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cart Item not found"));

        cartItem.setQuantity(request.getQuantity());
        cartItem.calculateItemTotal();
        cart.recalculateTotals();
        cartRepository.save(cart);

        log.info("Updated cart items: itemId={}, quantity={}", itemId, request.getQuantity());

        return cartMapper.toCartResponse(cart);
    }

    private CartResponse removeCartItemInternal(Cart cart, UUID itemId) {
        CartItem cartItem = cart.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));

        cart.removeItem(cartItem);
        cartRepository.save(cart);

        log.info("Removed Item from the cart: itemId={}", itemId);
        return cartMapper.toCartResponse(cart);
    }

}
