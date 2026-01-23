package com.nala.armoire.mapper;

import com.nala.armoire.model.dto.response.*;
import com.nala.armoire.model.entity.*;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class CartMapper {

    public CartResponse toCartResponse(Cart cart) {
        if(cart == null) return null;

        return CartResponse.builder()
                .id(cart.getId())
                .items(cart.getItems().stream()
                        .map(this::toCartItemResponse)
                        .collect(Collectors.toList()))
                .totalItems(cart.getTotalItemCount())
                .subtotal(cart.getSubtotal())
                .discountAmount(cart.getDiscountAmount())
                .taxAmount(cart.getTaxAmount())
                .shippingCost(cart.getShippingCost())
                .total(cart.getTotal())
                .createdAt(cart.getCreatedAt())
                .updatedAt(cart.getUpdatedAt())
                .build();
    }

    public CartItemResponse toCartItemResponse(CartItem item) {
        if(item == null) return null;

        return CartItemResponse.builder()
                .id(item.getId())
                .product(toProductSummary(item.getProduct()))
                .variant(toVariantSummary(item.getProductVariant()))
                .customization(toCustomizationSummary(item.getCustomization()))
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .itemTotal(item.getItemTotal())
                .addedAt(item.getCreatedAt())
                .build();
    }

    public ProductSummary toProductSummary(Product product) {
        if(product == null) return null;

        return ProductSummary.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .sku(product.getSku())
                .build();
    }

    public VariantSummary toVariantSummary(ProductVariant variant) {
        if(variant == null) return null;

        // Get primary image URL
        String primaryImageUrl = null;
        if (variant.getImages() != null && !variant.getImages().isEmpty()) {
            primaryImageUrl = variant.getImages().stream()
                    .filter(img -> img.getIsPrimary() != null && img.getIsPrimary())
                    .findFirst()
                    .map(img -> img.getImageUrl())
                    .orElseGet(() -> variant.getImages().get(0).getImageUrl());
        }

        return VariantSummary.builder()
                .id(variant.getId())
                .size(variant.getSize())
                .color(variant.getColor())
                .sku(variant.getSku())
                .primaryImageUrl(primaryImageUrl)
                .build();
    }

    public CustomizationSummary toCustomizationSummary(Customization customization) {
        if(customization == null) return null;

        return CustomizationSummary.builder()
                .id(customization.getId())
                .variantId(customization.getVariantId())
                .designId(customization.getDesignId())
                .threadColorHex(customization.getThreadColorHex())
                .additionalNotes(customization.getAdditionalNotes())
                .build();
    }

    public CartSummaryResponse toCartSummaryResponse(Cart cart) {
        if(cart == null) return null;

        return CartSummaryResponse.builder()
                .totalItems(cart.getTotalItemCount())
                .subtotal(cart.getSubtotal())
                .total(cart.getTotal())
                .discountAmount(cart.getDiscountAmount())
                .taxAmount(cart.getTaxAmount())
                .shippingCost(cart.getShippingCost())
                .build();
    }
}
