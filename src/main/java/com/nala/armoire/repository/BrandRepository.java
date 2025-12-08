package com.nala.armoire.repository;

import com.nala.armoire.model.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BrandRepository extends JpaRepository<Brand, UUID> {

    Optional<Brand> findBySlugAndIsActiveTrue(String slug);

    List<Brand> findByIsActiveTrueOrderByNameAsc();

    boolean existsBySlug(String slug);
}
