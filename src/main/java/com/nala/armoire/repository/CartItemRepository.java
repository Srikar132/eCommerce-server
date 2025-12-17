package com.nala.armoire.repository;

import com.nala.armoire.model.entity.Cart;
import com.nala.armoire.model.entity.CartItem;
import com.nala.armoire.model.entity.Product;
import com.nala.armoire.model.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    Optional<CartItem> findByCartAndProductAndProductVariantAndCustomizationIsNull(Cart cart, Product product, ProductVariant productVariant);

    Optional<CartItem> findByCartAndIdAndCart_IsActiveTrue(Cart cart, UUID id);
}
