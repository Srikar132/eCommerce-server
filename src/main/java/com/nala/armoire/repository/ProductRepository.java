package com.nala.armoire.repository;

import com.nala.armoire.model.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID>, 
                                           JpaSpecificationExecutor<Product>,
                                           ProductRepositoryCustom {

    Optional<Product> findBySlugAndIsActiveTrueAndIsDraftFalse(String slug);

    Page<Product> findByBrandIdAndIsActiveTrueAndIsDraftFalse(UUID brandId, Pageable pageable);

    boolean existsBySlug(String slug);
    
    /**
     * OPTIMIZED: Fetch products with all required relationships in a SINGLE query
     * NEW ARCHITECTURE: Product -> images -> variantImages -> variants
     * This avoids MultipleBagFetchException by fetching in proper order
     * 
     * Step 1: Fetch products with images, variants, category, brand
     * Step 2: Hibernate automatically loads variantImages when needed via helper methods
     */
    @Query("SELECT DISTINCT p FROM Product p " +
           "LEFT JOIN FETCH p.images " +
           "LEFT JOIN FETCH p.variants " +
           "LEFT JOIN FETCH p.category " +
           "LEFT JOIN FETCH p.brand " +
           "WHERE p.id IN :productIds")
    List<Product> findByIdsWithDetails(@Param("productIds") List<UUID> productIds);
}