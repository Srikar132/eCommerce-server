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
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Base64;
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
    private static final String PREVIEW_IMAGE_FILENAME = "preview.png";

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CustomizationRepository customizationRepository;
    private final UserRepository userRepository;
    private final CartMapper cartMapper;
    private final S3ImageService s3ImageService;
    private final PricingConfigService pricingConfigService;
    
    private final ConcurrentHashMap<UUID, Object> userLocks = new ConcurrentHashMap<>();

    // ==================== PUBLIC API ====================

    public CartResponse getCart(User user) {
        log.debug("Fetching cart for user: {}", user.getId());
        Cart cart = getOrCreateCart(user);
        return cartMapper.toCartResponse(cart);
    }

    public CartResponse getCartByUserId(UUID userId) {
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
        
        // Delete all customization previews from S3 before clearing
        cart.getItems().forEach(this::deleteCustomizationPreviewIfExists);
        
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
                UUID customizationId = resolveCustomizationId(user, localItem);
                AddToCartRequest addRequest = buildAddRequest(localItem, customizationId);
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
        Customization customization = resolveCustomization(request.getCustomizationId());
        
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
        Customization customization = resolveCustomization(request.getCustomizationId());
        
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
        
        // Delete customization preview from S3 if exists
        deleteCustomizationPreviewIfExists(item);
        
        cart.removeItem(item);
        cartRepository.save(cart);
        
        log.info("Removed item - itemId: {}", itemId);
        return cartMapper.toCartResponse(cart);
    }

    // ==================== CUSTOMIZATION HANDLING ====================

    private UUID resolveCustomizationId(User user, SyncLocalCartRequest.LocalCartItemRequest localItem) {
        if (localItem.getCustomizationId() != null) {
            return localItem.getCustomizationId();
        }
        
        if (localItem.getCustomizationData() != null) {
            return saveCustomization(user, localItem);
        }
        
        return null;
    }

    private UUID saveCustomization(User user, SyncLocalCartRequest.LocalCartItemRequest localItem) {
        SyncLocalCartRequest.LocalCustomizationData data = localItem.getCustomizationData();
        
        log.info("Saving customization - userId: {}, designId: {}", user.getId(), data.getDesignId());
        
        Customization customization = Customization.builder()
                .userId(user.getId())
                .productId(localItem.getProductId())
                .variantId(localItem.getProductVariantId())
                .designId(data.getDesignId())
                .threadColorHex(data.getThreadColorHex())
                .build();
        
        Customization saved = customizationRepository.save(customization);
        uploadPreviewImage(user, saved, data.getPreviewImageBase64());
        
        log.info("Customization saved - customizationId: {}", saved.getId());
        return saved.getId();
    }

    private void uploadPreviewImage(User user, Customization customization, String base64Data) {
        if (base64Data == null || base64Data.isEmpty()) {
            return;
        }
        
        try {
            MultipartFile imageFile = convertBase64ToMultipartFile(base64Data);
            var uploadResponse = s3ImageService.uploadCustomizationPreview(
                    imageFile, user.getId(), customization.getId());
            
            customization.setPreviewImageUrl(uploadResponse.getImageUrl());
            customizationRepository.save(customization);
            
            log.info("Preview uploaded - customizationId: {}, url: {}", 
                    customization.getId(), uploadResponse.getImageUrl());
            
        } catch (Exception e) {
            log.error("Failed to upload preview - customizationId: {}", customization.getId(), e);
        }
    }

    private MultipartFile convertBase64ToMultipartFile(String base64Data) {
        String base64String = base64Data.contains(",") ? base64Data.split(",")[1] : base64Data;
        byte[] imageBytes = Base64.getDecoder().decode(base64String);
        return new Base64MultipartFile(imageBytes, PREVIEW_IMAGE_FILENAME);
    }

    /**
     * Deletes customization preview image from S3 storage when cart item is removed
     */
    private void deleteCustomizationPreviewIfExists(CartItem item) {
        if (item.getCustomization() == null) {
            return;
        }
        
        String previewImageUrl = item.getCustomization().getPreviewImageUrl();
        if (previewImageUrl == null || previewImageUrl.isEmpty()) {
            return;
        }
        
        try {
            // Extract S3 key from URL
            // URL format: https://bucket.s3.region.amazonaws.com/customizations/userId/fileName
            // or CDN format: https://cloudfront-domain/customizations/userId/fileName
            String s3Key = extractS3KeyFromUrl(previewImageUrl);
            
            if (s3Key != null && !s3Key.isEmpty()) {
                s3ImageService.deleteCustomizationPreview(s3Key);
                log.info("Deleted customization preview - customizationId: {}, s3Key: {}", 
                        item.getCustomization().getId(), s3Key);
            }
        } catch (Exception e) {
            // Log but don't fail the cart operation
            log.warn("Failed to delete customization preview - customizationId: {}, url: {}", 
                    item.getCustomization().getId(), previewImageUrl, e);
        }
    }

    /**
     * Extracts S3 key from full URL
     * Handles both direct S3 URLs and CloudFront CDN URLs
     */
    private String extractS3KeyFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        
        try {
            // Remove protocol and domain, keep only the path
            // Example: https://domain.com/customizations/userId/file.png -> customizations/userId/file.png
            int pathStartIndex = url.indexOf("customizations/");
            if (pathStartIndex != -1) {
                return url.substring(pathStartIndex);
            }
            
            // Fallback: try to extract everything after the last domain segment
            String[] parts = url.split("/", 4);
            if (parts.length >= 4) {
                return parts[3]; // Return path after domain
            }
            
            return null;
        } catch (Exception e) {
            log.warn("Failed to extract S3 key from URL: {}", url, e);
            return null;
        }
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
        
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + variantId));
        
        if (!variant.getProduct().getId().equals(product.getId())) {
            throw new IllegalArgumentException("Variant does not belong to product");
        }
        
        return variant;
    }

    private Customization resolveCustomization(UUID customizationId) {
        return customizationId != null 
                ? customizationRepository.findById(customizationId)
                        .orElseThrow(() -> new ResourceNotFoundException("Customization not found: " + customizationId))
                : null;
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
        String additionalNotes = null;
        if (localItem.getCustomizationData() != null) {
            additionalNotes = localItem.getCustomizationData().getAdditionalNotes();
        }
        
        return AddToCartRequest.builder()
                .productId(localItem.getProductId())
                .productVariantId(localItem.getProductVariantId())
                .customizationId(customizationId)
                .quantity(localItem.getQuantity())
                .additionalNotes(additionalNotes)
                .build();
    }

    // ==================== INNER CLASS ====================

    private static class Base64MultipartFile implements MultipartFile {
        private final byte[] content;
        private final String name;
        
        Base64MultipartFile(byte[] content, String name) {
            this.content = content;
            this.name = name;
        }
        
        @Override
        public String getName() {
            return name;
        }
        
        @Override
        public String getOriginalFilename() {
            return name;
        }
        
        @Override
        public String getContentType() {
            return "image/png";
        }
        
        @Override
        public boolean isEmpty() {
            return content == null || content.length == 0;
        }
        
        @Override
        public long getSize() {
            return content.length;
        }
        
        @Override
        public byte[] getBytes() {
            return content;
        }
        
        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }
        
        @Override
        public void transferTo(File dest) throws IOException {
            throw new UnsupportedOperationException("transferTo not supported");
        }
    }
}