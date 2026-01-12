package com.nala.armoire.model.entity;

/**
 * Enum to define the role/purpose of a product image
 * Used to determine which images allow embroidery customization
 */
public enum ImageRole {
    /**
     * Preview/Base image - Product displayed flat/on mannequin
     * ✅ Embroidery customization ALLOWED
     */
    PREVIEW_BASE,
    
    /**
     * Showcase image - Product in display/presentation view
     * ❌ Embroidery customization NOT ALLOWED
     */
    SHOWCASE,
    
    /**
     * Detail/Zoom image - Close-up shots of product details
     * ❌ Embroidery customization NOT ALLOWED
     */
    DETAIL,
    
    /**
     * Lifestyle image - Person wearing/using the product
     * ❌ Embroidery customization NOT ALLOWED
     */
    LIFESTYLE
}
