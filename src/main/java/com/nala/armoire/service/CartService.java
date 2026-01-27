package com.nala.armoire.service;

import com.nala.armoire.exception.ResourceNotFoundException;
import com.nala.armoire.mapper.CartMapper;
import com.nala.armoire.model.dto.request.AddToCartRequest;
import com.nala.armoire.model.dto.request.SyncLocalCartRequest;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing authenticated user shopping carts.
 * 
 * <p>Architecture (v2.0):
 * <ul>
 *   <li>Guest carts are managed client-side (localStorage)</li>
 *   <li>Authenticated users get persistent server-side carts</li>
 *   <li>Local carts sync to server on login/checkout</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CartService {

    private static final int CART_EXPIRY_DAYS = 30;
    private static final BigDecimal CUSTOMIZATION_BASE_PRICE = BigDecimal.valueOf(10.00);

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CustomizationRepository customizationRepository;
    private final DesignRepository designRepository;
    private final UserRepository userRepository;
    private final CartMapper cartMapper;
    private final PricingConfigService pricingConfigService;
    
    private final ConcurrentHashMap<UUID, Object> userLocks = new ConcurrentHashMap<>();

    // ==================== PUBLIC API ====================

    public CartResponse getCart(User user) {
        log.debug("Fetching cart for user: {}", user.getId());
        Cart cart = getOrCreateCart(user);
        return cartMapper.toCartResponse(cart);
    }

    public CartResponse getCartByUserId(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        return getCart(findUserById(userId));
    }

    public CartResponse addItemToCart(User user, AddToCartRequest request) {
        log.info("Adding item to cart - userId: {}, productId: {}, quantity: {}", 
                user.getId(), request.getProductId(), request.getQuantity());
        
        Cart cart = getOrCreateCart(user);
        return addItem(cart, request);
    }

    public CartResponse addItemToCartByUserId(UUID userId, AddToCartRequest request) {
        return addItemToCart(findUserById(userId), request);
    }

    public CartResponse updateCartItem(User user, UUID itemId, UpdateCartItemRequest request) {
        Cart cart = getOrCreateCart(user);
        return updateItem(cart, itemId, request);
    }

    public CartResponse updateCartItemByUserId(UUID userId, UUID itemId, UpdateCartItemRequest request) {
        return updateCartItem(findUserById(userId), itemId, request);
    }

    public CartResponse removeCartItem(User user, UUID itemId) {
        Cart cart = getOrCreateCart(user);
        return removeItem(cart, itemId);
    }

    public CartResponse removeCartItemByUserId(UUID userId, UUID itemId) {
        return removeCartItem(findUserById(userId), itemId);
    }

    public CartResponse clearCart(User user) {
        log.info("Clearing cart for user: {}", user.getId());
        
        Cart cart = getOrCreateCart(user);
        int removedCount = cart.getItems().size();
        
        cart.clearItems();
        cartRepository.save(cart);
        
        log.info("Cart cleared - userId: {}, itemsRemoved: {}", user.getId(), removedCount);
        return cartMapper.toCartResponse(cart);
    }

    public CartResponse clearCartByUserId(UUID userId) {
        return clearCart(findUserById(userId));
    }

    public CartSummaryResponse getCartSummary(User user) {
        Cart cart = getOrCreateCart(user);
        return cartMapper.toCartSummaryResponse(cart);
    }

    public CartSummaryResponse getCartSummaryByUserId(UUID userId) {
        return getCartSummary(findUserById(userId));
    }

    /**
     * Syncs client-side cart to server on login/checkout.
     * 
     * <p>Process:
     * <ol>
     *   <li>Save any unsaved customizations</li>
     *   <li>Merge local items with existing cart</li>
     *   <li>Return consolidated cart</li>
     * </ol>
     */
    public CartResponse syncLocalCart(User user, SyncLocalCartRequest request) {
        log.info("Syncing local cart - userId: {}, itemCount: {}", user.getId(), request.getItems().size());
        
        // Use the same lock as getOrCreateCart to prevent race conditions
        Object lock = userLocks.computeIfAbsent(user.getId(), k -> new Object());
        
        synchronized (lock) {
            Cart cart = getOrCreateCart(user);
            int initialCount = cart.getItems().size();
            
            // Add all items WITHOUT saving in between
            for (SyncLocalCartRequest.LocalCartItemRequest localItem : request.getItems()) {
                AddToCartRequest addRequest = buildAddRequest(localItem, null);
                addItemWithoutSave(cart, addRequest);
            }
            
            // Save once after all items are added
            cart.recalculateTotals();
            Cart savedCart = cartRepository.save(cart);
            
            int addedCount = savedCart.getItems().size() - initialCount;
            log.info("Local cart synced - userId: {}, itemsAdded: {}, totalItems: {}", 
                    user.getId(), addedCount, savedCart.getItems().size());
            
            return cartMapper.toCartResponse(savedCart);
        }
    }

    public CartResponse syncLocalCartByUserId(UUID userId, SyncLocalCartRequest request) {
        return syncLocalCart(findUserById(userId), request);
    }

    // ==================== CART MANAGEMENT ====================

 private Cart getOrCreateCart(User user) {
        Object lock = userLocks.computeIfAbsent(user.getId(), k -> new Object());
        
        synchronized (lock) {
            Cart cart = cartRepository.findByUserWithItems(user)
                    .orElseGet(() -> createNewCart(user));
            
            // Apply pricing configuration
            cart.setGstRate(pricingConfigService.getGstRate());
            
            // Calculate subtotal first to determine shipping cost
            BigDecimal subtotal = cart.getItems().stream()
                    .map(item -> item.getItemTotal() != null ? item.getItemTotal() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Set shipping cost based on subtotal and threshold
            cart.setShippingCost(pricingConfigService.getShippingCost(subtotal));
            cart.recalculateTotals();
            
            return cart;
        }
    }

    private Cart createNewCart(User user) {
        log.info("Creating new cart for user: {}", user.getId());
        
        Cart cart = Cart.builder()
                .user(user)
                .isActive(true)
                .expiresAt(LocalDateTime.now().plusDays(CART_EXPIRY_DAYS))
                .build();
        
        Cart savedCart = cartRepository.save(cart);
        log.info("Cart created - cartId: {}, userId: {}", savedCart.getId(), user.getId());
        
        return savedCart;
    }

    // ==================== ITEM OPERATIONS ====================

    private CartResponse addItem(Cart cart, AddToCartRequest request) {
        Product product = findProductById(request.getProductId());
        ProductVariant variant = resolveVariant(request.getProductVariantId(), product);
        
        // Create customization if customization data is provided
        Customization customization = null;
        if (request.getCustomizationData() != null) {
            customization = createCustomization(cart.getUser(), request, variant);
        }
        
        Optional<CartItem> existingItem = findExistingItem(cart, product, variant, customization);
        
        if (existingItem.isPresent() && customization == null) {
            return updateExistingItem(cart, existingItem.get(), request.getQuantity());
        }
        
        return createNewItem(cart, request, product, variant, customization);
    }

    /**
     * Adds item to cart WITHOUT saving (for bulk operations like sync).
     * Caller must save the cart after all operations are complete.
     */
    private void addItemWithoutSave(Cart cart, AddToCartRequest request) {
        Product product = findProductById(request.getProductId());
        ProductVariant variant = resolveVariant(request.getProductVariantId(), product);
        
        // Create customization if customization data is provided
        Customization customization = null;
        if (request.getCustomizationData() != null) {
            customization = createCustomization(cart.getUser(), request, variant);
        }
        
        Optional<CartItem> existingItem = findExistingItem(cart, product, variant, customization);
        
        if (existingItem.isPresent() && customization == null) {
            // Update existing item quantity
            CartItem item = existingItem.get();
            item.setQuantity(item.getQuantity() + request.getQuantity());
            item.calculateItemTotal();
            log.debug("Updated existing item - itemId: {}, newQuantity: {}", item.getId(), item.getQuantity());
        } else {
            // Create new item
            BigDecimal unitPrice = calculateUnitPrice(product, variant);
            BigDecimal designPrice = customization != null ? CUSTOMIZATION_BASE_PRICE : BigDecimal.ZERO;
            
            CartItem cartItem = CartItem.builder()
                    .product(product)
                    .productVariant(variant)
                    .customization(customization)
                    .quantity(request.getQuantity())
                    .unitPrice(unitPrice)
                    .designPrice(designPrice)
                    .build();
            
            cartItem.calculateItemTotal();
            cart.addItem(cartItem);
            log.debug("Added new item - productId: {}, quantity: {}", product.getId(), request.getQuantity());
        }
    }

    private CartResponse updateExistingItem(Cart cart, CartItem item, int additionalQuantity) {
        item.setQuantity(item.getQuantity() + additionalQuantity);
        item.calculateItemTotal();
        cart.recalculateTotals();
        cartRepository.save(cart);
        
        log.info("Updated existing item - itemId: {}, newQuantity: {}", item.getId(), item.getQuantity());
        return cartMapper.toCartResponse(cart);
    }

    private CartResponse createNewItem(Cart cart, AddToCartRequest request, 
                                      Product product, ProductVariant variant, 
                                      Customization customization) {
        BigDecimal unitPrice = calculateUnitPrice(product, variant);
        BigDecimal designPrice = customization != null ? CUSTOMIZATION_BASE_PRICE : BigDecimal.ZERO;
        
        CartItem cartItem = CartItem.builder()
                .product(product)
                .productVariant(variant)
                .customization(customization)
                .quantity(request.getQuantity())
                .unitPrice(unitPrice)
                .designPrice(designPrice)
                .build();
        
        cartItem.calculateItemTotal();
        cart.addItem(cartItem);
        cartRepository.save(cart);
        
        log.info("Added new item - productId: {}, quantity: {}, total: {}", 
                product.getId(), request.getQuantity(), cartItem.getItemTotal());
        
        return cartMapper.toCartResponse(cart);
    }

    private CartResponse updateItem(Cart cart, UUID itemId, UpdateCartItemRequest request) {
        CartItem item = findCartItem(cart, itemId);
        
        if (request.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }
        
        item.setQuantity(request.getQuantity());
        item.calculateItemTotal();
        cart.recalculateTotals();
        cartRepository.save(cart);
        
        log.info("Updated item - itemId: {}, quantity: {}, total: {}", 
                itemId, request.getQuantity(), item.getItemTotal());
        
        return cartMapper.toCartResponse(cart);
    }

    private CartResponse removeItem(Cart cart, UUID itemId) {
        CartItem item = findCartItem(cart, itemId);
        
        cart.removeItem(item);
        cartRepository.save(cart);
        
        log.info("Removed item - itemId: {}", itemId);
        return cartMapper.toCartResponse(cart);
    }

    // ==================== CUSTOMIZATION HANDLING ====================

    /**
     * Creates a new customization from inline data in cart request
     */
    private Customization createCustomization(User user, AddToCartRequest request, ProductVariant variant) {
        AddToCartRequest.CustomizationData data = request.getCustomizationData();
        
        log.info("Creating customization - userId: {}, designId: {}", user.getId(), data.getDesignId());
        
        // Validate design exists
        designRepository.findById(data.getDesignId())
                .orElseThrow(() -> new ResourceNotFoundException("Design not found: " + data.getDesignId()));
        
        Customization customization = Customization.builder()
                .userId(user.getId())
                .productId(request.getProductId())
                .variantId(variant.getId())
                .designId(data.getDesignId())
                .threadColorHex(data.getThreadColorHex())
                .additionalNotes(data.getAdditionalNotes())
                .build();
        
        Customization saved = customizationRepository.save(customization);
        log.info("Customization created - customizationId: {}", saved.getId());
        
        return saved;
    }

    // ==================== HELPER METHODS ====================

    private User findUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private Product findProductById(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
    }

    private ProductVariant resolveVariant(UUID variantId, Product product) {
        if (variantId == null) {
            return null;
        }
        
        // Fetch variant with images eagerly for cart mapping
        ProductVariant variant = productVariantRepository.findByIdWithImages(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + variantId));
        
        if (!variant.getProduct().getId().equals(product.getId())) {
            throw new IllegalArgumentException("Variant does not belong to product");
        }
        
        return variant;
    }

    private Optional<CartItem> findExistingItem(Cart cart, Product product, 
                                                ProductVariant variant, Customization customization) {
        if (customization != null) {
            return Optional.empty();
        }
        
        return cartItemRepository.findByCartAndProductAndProductVariantAndCustomizationIsNull(
                cart, product, variant);
    }

    private CartItem findCartItem(Cart cart, UUID itemId) {
        return cart.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + itemId));
    }

    private BigDecimal calculateUnitPrice(Product product, ProductVariant variant) {
        BigDecimal basePrice = Optional.ofNullable(product.getBasePrice())
                .orElse(BigDecimal.ZERO);
        
        BigDecimal variantPrice = variant != null 
                ? Optional.ofNullable(variant.getAdditionalPrice()).orElse(BigDecimal.ZERO)
                : BigDecimal.ZERO;
        
        return basePrice.add(variantPrice);
    }

    private AddToCartRequest buildAddRequest(SyncLocalCartRequest.LocalCartItemRequest localItem, 
                                            UUID customizationId) {
        // Build customization data if present
        AddToCartRequest.CustomizationData customizationData = null;
        if (localItem.getCustomizationData() != null) {
            SyncLocalCartRequest.LocalCustomizationData localData = localItem.getCustomizationData();
            customizationData = AddToCartRequest.CustomizationData.builder()
                    .designId(localData.getDesignId())
                    .threadColorHex(localData.getThreadColorHex())
                    .additionalNotes(localData.getAdditionalNotes())
                    .build();
        }
        
        return AddToCartRequest.builder()
                .productId(localItem.getProductId())
                .productVariantId(localItem.getProductVariantId())
                .customizationData(customizationData)
                .quantity(localItem.getQuantity())
                .build();
    }
}