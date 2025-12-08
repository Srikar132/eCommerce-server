package com.nala.armoire.repository;

import com.nala.armoire.model.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> , JpaSpecificationExecutor<Product> {

    Optional<Product> findBySlugAndIsActiveTrue(String slug);
    Page<Product> findByCategoryIdAndIsActiveTrue(UUID categoryId, Pageable pageable);

    Page<Product> findByBrandIdAndIsActiveTrue(UUID brandId, Pageable pageable);

    boolean existsBySlug(String slug);
}
