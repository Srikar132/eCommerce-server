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
                .customizationPrice(item.getCustomizationPrice())
                .itemTotal(item.getItemTotal())
                .customizationSummary(item.getCustomizationSummary())
                .addedAt(item.getCreatedAt())
                .build();
    }

    public ProductSummary toProductSummary(Product product) {
        if(product == null) return null;

        String imageUrl = null;
        // if (product.getImages() != null && !product.getImages().isEmpty()) {
        //     imageUrl = product.getImages().get(0).getImageUrl();
        // }

        return ProductSummary.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .sku(product.getSku())
                .imageUrl(imageUrl)
                .build();
    }

    public VariantSummary toVariantSummary(ProductVariant variant) {
        if(variant == null) return null;

        return VariantSummary.builder()
                .id(variant.getId())
                .size(variant.getSize())
                .color(variant.getColor())
                .sku(variant.getColor())
                .build();
    }

    public CustomizationSummary toCustomizationSummary(Customization customization) {
        if(customization == null) return null;

        return CustomizationSummary.builder()
                .id(customization.getId())
                .customizationId(customization.getCustomizationId())
                .previewImageUrl(customization.getPreviewImageUrl())
                .thumbnailUrl(customization.getThumbnailUrl())
                .hasText(customization.getHasText())
                .hasDesign(customization.getHasDesign())
                .hasUploadedImage(customization.getHasUploadedImage())
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
                .build();
    }
}
